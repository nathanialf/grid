package com.defnf.grid.data.remote.ssh

import org.apache.sshd.common.util.io.PathUtils
import org.apache.sshd.common.util.security.SecurityUtils
import java.io.File
import java.nio.file.Paths

/**
 * Process-global Apache SSHD setup.
 *
 * This MUST run before any other `org.apache.sshd` class is loaded. `SecurityUtils.register()` is
 * guarded by a one-shot flag, so disabling a provider after something has already touched
 * [SecurityUtils] is silently ignored.
 *
 * ### Why BouncyCastle is disabled
 *
 * `bcprov-jdk18on` is on the runtime classpath transitively via smbj, so SSHD's BouncyCastle
 * registrar reports itself supported. Left alone, `getOrCreateProvider("BC")` calls
 * `Security.getProvider("BC")` first and finds *Android's own deprecated platform provider* — the
 * names collide — and `SecurityUtils.register()` then promotes it to SSHD's global default provider
 * choice for every cipher, MAC, digest and key operation. Android stripped most implementations out
 * of that provider at API 28, so the result is either per-algorithm fallback probing or being pinned
 * to slow pure-Java implementations instead of Conscrypt/BoringSSL. Disabling it keeps SSHD on the
 * platform providers, which is both faster and better maintained.
 */
internal object SshEnvironment {

    @Volatile
    private var initialized = false

    fun initOnce(filesDir: File) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return

            // Stops register() adopting Android's platform "BC" as SSHD's default provider choice.
            // (There is also an org.apache.sshd.registerBouncyCastle system property, but it is
            // deprecated in 2.14 — this is the supported route and is sufficient on its own.)
            SecurityUtils.setAPrioriDisabledProvider(SecurityUtils.BOUNCY_CASTLE, true)

            // Android has no user home directory; without this, SSHD's "~" expansion throws.
            PathUtils.setUserHomeFolderResolver { Paths.get(filesDir.absolutePath) }

            initialized = true
        }
    }
}
