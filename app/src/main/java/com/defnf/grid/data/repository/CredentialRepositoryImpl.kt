package com.defnf.grid.data.repository

import com.defnf.grid.data.local.EncryptionManager
import com.defnf.grid.data.local.PreferencesManager
import com.defnf.grid.data.remote.pool.ConnectionPool
import com.defnf.grid.di.ApplicationScope
import com.defnf.grid.domain.model.Credential
import com.defnf.grid.domain.repository.CredentialRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

/**
 * Credential storage, with an in-memory cache in front of it.
 *
 * Reading a credential is on the critical path of every remote operation: the connection pool needs
 * it to build its key before it can decide whether an existing session can be reused. Uncached, that
 * would mean a full DataStore read plus an AES pass over every stored credential per operation.
 *
 * The cache holds [Deferred] rather than plain values so that concurrent callers for the same id
 * collapse onto a single load instead of stampeding the store. Failed loads are evicted so a
 * transient Keystore error cannot poison the entry for the life of the process.
 *
 * This class is the only reader *and* the only writer of credentials, so invalidation is complete
 * and entirely local to it.
 *
 * Note that this keeps decrypted secrets in memory for as long as they are cached. A pooled session
 * already holds the same material for its own lifetime, so the exposure is not new — but
 * [clearCache] is called when the app is backgrounded so an idle app converges on holding nothing.
 */
@Singleton
class CredentialRepositoryImpl @Inject constructor(
    private val preferencesManager: PreferencesManager,
    private val encryptionManager: EncryptionManager,
    // A Provider, not a direct injection: ConnectionPool depends on CredentialRepository, so taking
    // the pool by value here would be a dependency cycle.
    private val connectionPool: Provider<ConnectionPool>,
    @ApplicationScope private val scope: CoroutineScope
) : CredentialRepository {

    private val json = Json { ignoreUnknownKeys = true }

    private val cacheMutex = Mutex()
    private val cache = mutableMapOf<String, Deferred<Credential>>()

    override suspend fun insertCredential(credential: Credential) {
        val encryptedCredential = credential.copy(
            password = encryptionManager.encrypt(credential.password),
            privateKey = credential.privateKey?.let { encryptionManager.encrypt(it) }
        )

        val credentialJson = json.encodeToString(encryptedCredential)
        preferencesManager.saveCredential(credential.id, credentialJson)
        invalidate(credential.id)
        // Drop any session still authenticated with the previous secret rather than waiting for it
        // to idle out.
        connectionPool.get().evictByCredential(credential.id)
    }

    override suspend fun updateCredential(credential: Credential) {
        insertCredential(credential) // Same implementation for update
    }

    override suspend fun deleteCredential(credentialId: String) {
        preferencesManager.deleteCredential(credentialId)
        invalidate(credentialId)
        connectionPool.get().evictByCredential(credentialId)
    }

    override suspend fun getCredentialById(credentialId: String): Credential {
        val deferred = cacheMutex.withLock {
            cache.getOrPut(credentialId) {
                scope.async(start = CoroutineStart.LAZY) { loadCredential(credentialId) }
            }
        }

        return try {
            deferred.await()
        } catch (e: Throwable) {
            // Don't let a transient failure stick around as a cached result.
            cacheMutex.withLock {
                if (cache[credentialId] === deferred) cache.remove(credentialId)
            }
            throw e
        }
    }

    override suspend fun getAllCredentials(): Flow<List<Credential>> = flow {
        val credentials = preferencesManager.getAllCredentials().map { (_, credentialJson) ->
            val encryptedCredential = json.decodeFromString<Credential>(credentialJson)
            encryptedCredential.copy(
                password = encryptionManager.decrypt(encryptedCredential.password),
                privateKey = encryptedCredential.privateKey?.let { encryptionManager.decrypt(it) }
            )
        }
        emit(credentials)
    }

    /** Drops every cached credential. Called when the app is backgrounded. */
    suspend fun clearCache() = invalidate(null)

    private suspend fun invalidate(credentialId: String?) = cacheMutex.withLock {
        if (credentialId == null) cache.clear() else cache.remove(credentialId)
        Unit
    }

    private suspend fun loadCredential(credentialId: String): Credential {
        val credentialJson = preferencesManager.getCredential(credentialId)
            ?: throw IllegalArgumentException("Credential not found: $credentialId")

        val encryptedCredential = json.decodeFromString<Credential>(credentialJson)

        return encryptedCredential.copy(
            password = encryptionManager.decrypt(encryptedCredential.password),
            privateKey = encryptedCredential.privateKey?.let { encryptionManager.decrypt(it) }
        )
    }
}
