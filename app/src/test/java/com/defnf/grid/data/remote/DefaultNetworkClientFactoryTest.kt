package com.defnf.grid.data.remote

import com.defnf.grid.data.remote.smb.SmbClientProvider
import com.defnf.grid.data.remote.ssh.SshClientProvider
import com.defnf.grid.domain.model.Protocol
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertNotSame
import org.junit.Test

/**
 * The pool gives each pooled connection its own client and assumes they are independent. They are
 * not interchangeable: each holds a single session in mutable fields, so a shared instance would
 * mean connecting one connection silently overwrites another's session — and disconnecting one
 * closes it. That failure is invisible in the pool's own tests, which use a fake factory, so the
 * contract is pinned here against the real implementation.
 */
class DefaultNetworkClientFactoryTest {

    private val factory = DefaultNetworkClientFactory(
        sshClientProvider = SshClientProvider(
            context = mockk(relaxed = true),
            scope = CoroutineScope(Dispatchers.Unconfined),
            hostKeyVerifier = mockk(relaxed = true)
        ),
        smbClientProvider = SmbClientProvider(),
        appScope = CoroutineScope(Dispatchers.Unconfined)
    )

    @Test
    fun `each SFTP client is a distinct instance`() {
        assertNotSame(
            factory.createClient(Protocol.SFTP),
            factory.createClient(Protocol.SFTP)
        )
    }

    @Test
    fun `each SMB client is a distinct instance`() {
        assertNotSame(
            factory.createClient(Protocol.SMB),
            factory.createClient(Protocol.SMB)
        )
    }
}
