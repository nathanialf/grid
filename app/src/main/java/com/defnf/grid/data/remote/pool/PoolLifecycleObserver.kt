package com.defnf.grid.data.remote.pool

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.defnf.grid.data.repository.CredentialRepositoryImpl
import com.defnf.grid.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Ties pooled sessions and cached credentials to whether the app is in the foreground.
 *
 * Backgrounding shortens the pool's idle timeout rather than clearing it outright — see
 * [ConnectionPool.onAppBackgrounded] for why that distinction matters for the system file picker. The
 * credential cache *is* cleared, so an app sitting in the background converges on holding no
 * decrypted secrets.
 */
@Singleton
class PoolLifecycleObserver @Inject constructor(
    private val pool: ConnectionPool,
    private val credentialRepository: CredentialRepositoryImpl,
    @ApplicationScope private val scope: CoroutineScope
) : DefaultLifecycleObserver {

    override fun onStart(owner: LifecycleOwner) {
        pool.onAppForegrounded()
    }

    override fun onStop(owner: LifecycleOwner) {
        pool.onAppBackgrounded()
        scope.launch { credentialRepository.clearCache() }
    }
}
