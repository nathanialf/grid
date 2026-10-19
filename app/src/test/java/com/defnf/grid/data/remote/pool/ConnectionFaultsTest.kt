package com.defnf.grid.data.remote.pool

import com.hierynomus.mserref.NtStatus
import com.hierynomus.mssmb2.SMB2MessageCommandCode
import com.hierynomus.mssmb2.SMBApiException
import org.apache.sshd.common.SshException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.SocketException

class ConnectionFaultsTest {

    private fun smbError(status: NtStatus) =
        SMBApiException(status.value, SMB2MessageCommandCode.SMB2_CREATE, null)

    @Test
    fun `SMB server answers are not connection faults`() {
        assertFalse(ConnectionFaults.isConnectionFault(smbError(NtStatus.STATUS_OBJECT_NAME_NOT_FOUND)))
        assertFalse(ConnectionFaults.isConnectionFault(smbError(NtStatus.STATUS_ACCESS_DENIED)))
    }

    @Test
    fun `SMB session teardown statuses are connection faults`() {
        assertTrue(ConnectionFaults.isConnectionFault(smbError(NtStatus.STATUS_NETWORK_SESSION_EXPIRED)))
        assertTrue(ConnectionFaults.isConnectionFault(smbError(NtStatus.STATUS_USER_SESSION_DELETED)))
    }

    @Test
    fun `dead transports are connection faults, including when wrapped`() {
        assertTrue(ConnectionFaults.isConnectionFault(SocketException("reset")))
        assertTrue(ConnectionFaults.isConnectionFault(SshException("closed")))
        assertTrue(ConnectionFaults.isConnectionFault(IOException("wrapped", SocketException("reset"))))
        assertTrue(ConnectionFaults.isConnectionFault(IOException("Channel is closed")))
    }

    @Test
    fun `ordinary IO errors are not connection faults`() {
        assertFalse(ConnectionFaults.isConnectionFault(IOException("No such file")))
    }
}
