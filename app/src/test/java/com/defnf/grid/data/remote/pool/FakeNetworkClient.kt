package com.defnf.grid.data.remote.pool

import com.defnf.grid.data.remote.NetworkClient
import com.defnf.grid.domain.model.Connection
import com.defnf.grid.domain.model.Credential
import com.defnf.grid.domain.model.RemoteFile
import com.defnf.grid.domain.model.Result
import com.defnf.grid.domain.model.TransferProgress
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.apache.sshd.common.SshException
import java.io.InputStream
import java.io.OutputStream
import java.net.SocketException
import java.util.concurrent.atomic.AtomicInteger

/**
 * A hand-written fake rather than a MockK mock: these tests need exact connect/disconnect counts, a
 * concurrency high-water mark, and suspension points a test can hold open and release on demand.
 */
@Suppress("UNUSED_PARAMETER")
class FakeNetworkClient : NetworkClient {

    val connectCount = AtomicInteger(0)
    val disconnectCount = AtomicInteger(0)

    private val concurrentOps = AtomicInteger(0)

    @Volatile
    var maxObservedConcurrency = 0
        private set

    @Volatile
    var connected = false
        private set

    /** Number of upcoming connect() calls that should fail. */
    @Volatile
    var connectFailuresRemaining = 0

    /** Number of upcoming listFiles() calls that should throw a connection-fault exception. */
    @Volatile
    var listFailuresRemaining = 0

    /** Number of upcoming listFiles() calls that should throw a NON-connection-fault exception. */
    @Volatile
    var listBusinessFailuresRemaining = 0

    /** When set, connect() suspends until it completes. */
    @Volatile
    var connectGate: CompletableDeferred<Unit>? = null

    /** When set, listFiles() suspends until it completes. */
    @Volatile
    var listGate: CompletableDeferred<Unit>? = null

    override suspend fun connect(connection: Connection, credential: Credential): Result<Unit> {
        connectGate?.await()
        connectCount.incrementAndGet()
        if (connectFailuresRemaining > 0) {
            connectFailuresRemaining--
            return Result.Error(SocketException("connection refused"))
        }
        connected = true
        return Result.Success(Unit)
    }

    override suspend fun disconnect(): Result<Unit> {
        disconnectCount.incrementAndGet()
        connected = false
        return Result.Success(Unit)
    }

    override suspend fun listFiles(path: String): Result<List<RemoteFile>> = track {
        listGate?.await()
        if (listFailuresRemaining > 0) {
            listFailuresRemaining--
            throw SshException("Channel is closed")
        }
        if (listBusinessFailuresRemaining > 0) {
            listBusinessFailuresRemaining--
            throw NoSuchFileOnServerException()
        }
        Result.Success(emptyList())
    }

    override suspend fun createDirectory(path: String): Result<Unit> = Result.Success(Unit)

    override suspend fun deleteFile(path: String): Result<Unit> = Result.Success(Unit)

    override suspend fun renameFile(oldPath: String, newPath: String): Result<Unit> =
        Result.Success(Unit)

    override suspend fun downloadFile(
        remotePath: String,
        outputStream: OutputStream
    ): Flow<TransferProgress> = emptyFlow()

    override suspend fun uploadFile(
        localInputStream: InputStream,
        remotePath: String,
        fileSize: Long
    ): Flow<TransferProgress> = emptyFlow()

    override fun isConnected(): Boolean = connected

    private suspend fun <T> track(body: suspend () -> T): T {
        val inFlight = concurrentOps.incrementAndGet()
        if (inFlight > maxObservedConcurrency) maxObservedConcurrency = inFlight
        try {
            return body()
        } finally {
            concurrentOps.decrementAndGet()
        }
    }
}

/** Stands in for an SFTP status failure: a healthy session answering "no". */
class NoSuchFileOnServerException : Exception("No such file")
