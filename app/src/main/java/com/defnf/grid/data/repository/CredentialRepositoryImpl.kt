package com.defnf.grid.data.repository

import com.defnf.grid.data.local.EncryptionManager
import com.defnf.grid.data.local.PreferencesManager
import com.defnf.grid.domain.model.Credential
import com.defnf.grid.domain.repository.CredentialRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CredentialRepositoryImpl @Inject constructor(
    private val preferencesManager: PreferencesManager,
    private val encryptionManager: EncryptionManager
) : CredentialRepository {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun insertCredential(credential: Credential) {
        val encryptedCredential = credential.copy(
            password = encryptionManager.encrypt(credential.password),
            privateKey = credential.privateKey?.let { encryptionManager.encrypt(it) }
        )
        
        val credentialJson = json.encodeToString(encryptedCredential)
        preferencesManager.saveCredential(credential.id, credentialJson)
    }

    override suspend fun updateCredential(credential: Credential) {
        insertCredential(credential) // Same implementation for update
    }

    override suspend fun deleteCredential(credentialId: String) {
        preferencesManager.deleteCredential(credentialId)
    }

    override suspend fun getCredentialById(credentialId: String): Credential {
        val credentialJson = preferencesManager.getCredential(credentialId)
            ?: throw IllegalArgumentException("Credential not found: $credentialId")
        
        val encryptedCredential = json.decodeFromString<Credential>(credentialJson)
        
        return encryptedCredential.copy(
            password = encryptionManager.decrypt(encryptedCredential.password),
            privateKey = encryptedCredential.privateKey?.let { encryptionManager.decrypt(it) }
        )
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
}