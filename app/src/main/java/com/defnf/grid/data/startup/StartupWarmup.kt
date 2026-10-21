package com.defnf.grid.data.startup

import com.defnf.grid.data.local.EncryptionManager
import com.defnf.grid.data.local.PreferencesManager
import com.defnf.grid.data.offline.OfflineStore
import com.defnf.grid.data.remote.smb.SmbClientProvider
import com.defnf.grid.data.remote.ssh.SshClientProvider
import com.defnf.grid.data.remote.telemetry.ConnectTrace
import com.defnf.grid.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Moves one-time initialisation off the critical path to the first directory listing.
 *
 * Most of what makes a first connect slow is not the network — it is class-loading the SSH stack,
 * running its JCE capability probes, standing up the transport's thread pool, and the binder round
 * trip to AndroidKeyStore. None of that depends on which server the user picks, so it can happen
 * while they are still looking at the connection list.
 *
 * Nothing here touches the network: no speculative connects, and no credential decryption. Decrypting
 * ahead of time would defeat the biometric gate on the connection list, so only the KeyStore *handle*
 * is warmed, which is where the latency actually is.
 */
@Singleton
class StartupWarmup @Inject constructor(
    @ApplicationScope private val scope: CoroutineScope,
    private val sshClientProvider: SshClientProvider,
    private val smbClientProvider: SmbClientProvider,
    private val preferencesManager: PreferencesManager,
    private val encryptionManager: EncryptionManager,
    private val offlineStore: OfflineStore
) {

    private val started = AtomicBoolean(false)

    fun start() {
        if (!started.compareAndSet(false, true)) return

        scope.launch {
            // Let the first frames render before competing for IO. A user who taps faster than this
            // simply triggers the same lazy Deferred earlier — the work is never done twice.
            delay(STARTUP_DELAY_MS)

            val trace = ConnectTrace.forWarmup()

            // Needed to draw the connection list at all, and the first DataStore read is file IO
            // plus a JSON decode.
            runCatching { preferencesManager.getConnections().first() }
            trace.mark("datastore")

            // Binder round trip to keystore2. Often the single most expensive cold operation, and it
            // reveals nothing: no credential is decrypted here.
            runCatching { encryptionManager.isKeyAvailable() }
            trace.mark("keystore")

            // Builds and starts the shared SshClient (ServiceLoader scan, NIO2 transport, thread
            // pool) and, as part of that, class-initialises the algorithm registries and runs their
            // JCE probes. isX25519Supported() enforces that ordering itself — the registries must
            // not be touched before SshEnvironment.initOnce has disabled the BouncyCastle registrar,
            // because SecurityUtils.register() is one-shot.
            val x25519 = runCatching { sshClientProvider.isX25519Supported() }.getOrDefault(false)
            trace.mark("sshclient")

            runCatching { smbClientProvider.warmUp() }
            trace.mark("smb")

            // Offline copies moved to Downloads/Grid in 1.2.1; the old private cache is dead weight.
            runCatching { offlineStore.deleteLegacyCache() }

            trace.note("x25519", x25519.toString())
            trace.finish("ok")
        }
    }

    private companion object {
        const val STARTUP_DELAY_MS = 400L
    }
}
