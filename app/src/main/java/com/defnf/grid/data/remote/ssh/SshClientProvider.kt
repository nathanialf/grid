package com.defnf.grid.data.remote.ssh

import android.content.Context
import com.defnf.grid.di.ApplicationScope
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import org.apache.sshd.client.SshClient
import org.apache.sshd.client.keyverifier.AcceptAllServerKeyVerifier
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns the one configured, started [SshClient] the app uses for every SFTP session.
 *
 * A started `SshClient` is safe to reuse across sessions: `connect()` returns a fresh `ClientSession`
 * each time, and this app registers credentials on the *session* rather than the client, so nothing
 * leaks between connections. (Registering identities on the client would share them — don't.)
 *
 * Building one is not cheap. `start()` runs two `ServiceLoader` passes, selects the NIO2 transport,
 * and creates an `AsynchronousChannelGroup` backed by a pool of `availableProcessors() + 1` threads —
 * and behind that sits the class-initialisation of every algorithm registry, each running real JCE
 * probes. Doing that per connect, as the previous code did, put all of it on the critical path to the
 * first directory listing.
 *
 * The instance is held in a lazily-started [Deferred] so the build never runs on a caller's thread and
 * a connect racing with warm-up simply awaits the same instance instead of duplicating the work.
 */
@Singleton
class SshClientProvider @Inject constructor(
    @ApplicationContext private val context: Context,
    @ApplicationScope private val scope: CoroutineScope
) {

    private val client: Deferred<SshClient> =
        scope.async(start = CoroutineStart.LAZY) { build() }

    /** Kicks off construction without waiting. Idempotent; safe to call from warm-up. */
    fun warmUp() {
        client.start()
    }

    suspend fun get(): SshClient = client.await()

    /**
     * Whether this device can do X25519 key exchange.
     *
     * Goes through [get] first on purpose: building the client is what runs
     * [SshEnvironment.initOnce], and the algorithm registries must not be touched before that.
     */
    suspend fun isX25519Supported(): Boolean {
        get()
        return SshAlgorithms.isX25519Supported()
    }

    private fun build(): SshClient {
        // Must precede every other org.apache.sshd class load. See SshEnvironment.
        SshEnvironment.initOnce(context.filesDir)

        val sshClient = SshClient.setUpDefaultClient()
        SshAlgorithms.apply(sshClient)
        SshTuning.apply(sshClient)
        sshClient.serverKeyVerifier = AcceptAllServerKeyVerifier.INSTANCE
        sshClient.start()
        return sshClient
    }
}
