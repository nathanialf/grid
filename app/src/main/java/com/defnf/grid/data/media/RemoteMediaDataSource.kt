package com.defnf.grid.data.media

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSourceException
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.TransferListener
import com.defnf.grid.data.offline.OfflineFiller
import com.defnf.grid.data.remote.pool.ConnectionPool
import com.defnf.grid.domain.model.Connection
import com.defnf.grid.domain.repository.ConnectionRepository
import com.defnf.grid.util.GridLog
import kotlinx.coroutines.runBlocking
import java.io.IOException
import java.io.InputStream
import java.io.InterruptedIOException
import java.io.RandomAccessFile
import javax.inject.Inject
import javax.inject.Singleton

/**
 * `grid://stream/<connectionId>/<file name>?path=<remote path>&size=<bytes>` URIs identify a file on
 * an SFTP/SMB server that the player reads directly, so playback starts without downloading the
 * whole file first. Both protocols support reading from any offset, so nothing is needed on the
 * server side; seeking simply reopens the file at the new position.
 */
object RemoteMediaUri {
    const val SCHEME = "grid"
    private const val AUTHORITY = "stream"
    private const val PARAM_PATH = "path"
    private const val PARAM_SIZE = "size"

    fun build(connectionId: String, remotePath: String, fileName: String, size: Long): Uri =
        Uri.Builder()
            .scheme(SCHEME)
            .authority(AUTHORITY)
            .appendPath(connectionId)
            // The name keeps the extension visible to Media3's content-type sniffing
            .appendPath(fileName)
            .appendQueryParameter(PARAM_PATH, remotePath)
            .appendQueryParameter(PARAM_SIZE, size.toString())
            .build()

    fun isRemote(uri: Uri): Boolean = uri.scheme == SCHEME

    internal fun connectionId(uri: Uri): String =
        uri.pathSegments.firstOrNull() ?: throw IOException("No connection in $uri")

    internal fun remotePath(uri: Uri): String =
        uri.getQueryParameter(PARAM_PATH) ?: throw IOException("No remote path in $uri")

    internal fun size(uri: Uri): Long = uri.getQueryParameter(PARAM_SIZE)?.toLongOrNull() ?: -1L
}

/**
 * Reads a `grid://` URI, saving the file offline as it goes.
 *
 * Each open starts (or joins) the file's [OfflineFiller] fill and plays from that growing local copy,
 * so the file comes down once. Only a seek well past what the fill has reached reads the server
 * directly, through the shared [ConnectionPool] session the file browser was using; so does every
 * read when the copy can't be saved (unknown size, no storage access) or the fill fails.
 */
@OptIn(UnstableApi::class)
class RemoteFileDataSource(
    private val pool: ConnectionPool,
    private val connectionRepository: ConnectionRepository,
    private val filler: OfflineFiller
) : BaseDataSource(/* isNetwork = */ true) {

    private var uri: Uri? = null
    private var connection: Connection? = null
    private var remotePath: String? = null
    private var fill: OfflineFiller.Fill? = null
    private var local: RandomAccessFile? = null
    private var stream: InputStream? = null
    private var position = 0L
    private var bytesRemaining = 0L
    private var opened = false

    override fun open(dataSpec: DataSpec): Long {
        val target = dataSpec.uri
        uri = target
        transferInitializing(dataSpec)

        val size = RemoteMediaUri.size(target)
        position = dataSpec.position
        try {
            // DataSource calls happen on the player's loader thread, which may block.
            val resolved = runBlocking { connectionRepository.getConnectionById(RemoteMediaUri.connectionId(target)) }
            val path = RemoteMediaUri.remotePath(target)
            connection = resolved
            remotePath = path
            // Saving offline is a bonus; failing to start it must never stop playback.
            val joined = runCatching { runBlocking { filler.acquire(resolved, path, size) } }
                .onFailure { GridLog.w(TAG, "Couldn't save $path offline", it) }
                .getOrNull()
            fill = joined
            if (joined == null || position > joined.written + LOCAL_WINDOW_BYTES || !openLocal(joined)) {
                openRemote()
            }
        } catch (e: IOException) {
            throw DataSourceException(e, PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED)
        } catch (e: Exception) {
            throw DataSourceException(e, PlaybackException.ERROR_CODE_IO_UNSPECIFIED)
        }

        bytesRemaining = when {
            dataSpec.length != C.LENGTH_UNSET.toLong() -> dataSpec.length
            size > 0 -> (size - dataSpec.position).coerceAtLeast(0L)
            else -> C.LENGTH_UNSET.toLong()
        }
        opened = true
        transferStarted(dataSpec)
        return bytesRemaining
    }

    /** Opens the fill's local copy at [position]; false if it isn't there to open. */
    private fun openLocal(fill: OfflineFiller.Fill): Boolean {
        // The part file may be renamed into place between choosing it and opening it.
        val file = sequenceOf(fill.readable, fill.target).firstOrNull { it.exists() } ?: return false
        local = try {
            RandomAccessFile(file, "r").apply { seek(position) }
        } catch (e: IOException) {
            return false
        }
        return true
    }

    private fun openRemote() {
        val resolved = connection ?: throw IOException("No connection")
        val path = remotePath ?: throw IOException("No remote path")
        val offset = position
        stream = runBlocking {
            pool.openStream(resolved) { client -> client.openInputStream(path, offset) }
        }
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (bytesRemaining == 0L) return C.RESULT_END_OF_INPUT

        val toRead = if (bytesRemaining == C.LENGTH_UNSET.toLong()) {
            length
        } else {
            minOf(bytesRemaining, length.toLong()).toInt()
        }
        val read = try {
            readLocal(buffer, offset, toRead) ?: readRemote(buffer, offset, toRead)
        } catch (e: InterruptedException) {
            // The loader interrupts a cancelled load; stop waiting on the fill.
            Thread.currentThread().interrupt()
            throw DataSourceException(InterruptedIOException(), PlaybackException.ERROR_CODE_IO_UNSPECIFIED)
        } catch (e: IOException) {
            throw DataSourceException(e, PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED)
        }
        if (read == -1) return C.RESULT_END_OF_INPUT

        position += read
        if (bytesRemaining != C.LENGTH_UNSET.toLong()) bytesRemaining -= read
        bytesTransferred(read)
        return read
    }

    /**
     * Reads from the local copy, waiting for the fill to get there; null once reads must go to the
     * server instead (no local copy, or the fill stopped short of [position]).
     */
    private fun readLocal(buffer: ByteArray, offset: Int, length: Int): Int? {
        val file = local ?: return null
        val fill = fill ?: return null
        while (true) {
            val available = (if (fill.isComplete) fill.size else fill.written) - position
            if (available > 0) {
                val read = file.read(buffer, offset, minOf(available, length.toLong()).toInt())
                if (read != -1) return read
                // The copy is shorter than the fill reported (deleted underneath us): go remote.
            } else if (fill.isComplete) {
                return -1
            }
            if (fill.failure != null || available > 0) {
                closeLocal()
                openRemote()
                return null
            }
            fill.awaitBeyond(position, FILL_WAIT_MS)
        }
    }

    private fun readRemote(buffer: ByteArray, offset: Int, length: Int): Int {
        val input = stream ?: throw DataSourceException(PlaybackException.ERROR_CODE_IO_UNSPECIFIED)
        return input.read(buffer, offset, length)
    }

    override fun getUri(): Uri? = uri

    override fun close() {
        uri = null
        fill = null
        connection = null
        remotePath = null
        try {
            closeLocal()
            stream?.close()
        } catch (e: IOException) {
            throw DataSourceException(e, PlaybackException.ERROR_CODE_IO_UNSPECIFIED)
        } finally {
            stream = null
            if (opened) {
                opened = false
                transferEnded()
            }
        }
    }

    private fun closeLocal() {
        try {
            local?.close()
        } finally {
            local = null
        }
    }

    private companion object {
        const val TAG = "GridMedia"

        /** How far past the fill a read may start and still wait for it rather than go remote. */
        const val LOCAL_WINDOW_BYTES = 2L * 1024 * 1024

        /** Upper bound on one wait for the fill; the loop re-checks for failure in between. */
        const val FILL_WAIT_MS = 500L
    }
}

/**
 * Routes `grid://` URIs to [RemoteFileDataSource] and everything else (offline copies) to
 * Media3's [DefaultDataSource].
 */
@OptIn(UnstableApi::class)
private class RoutingDataSource(
    private val local: DataSource,
    private val createRemote: () -> DataSource
) : DataSource {

    private val listeners = mutableListOf<TransferListener>()
    private var remote: DataSource? = null
    private var active: DataSource? = null

    override fun addTransferListener(transferListener: TransferListener) {
        listeners += transferListener
        local.addTransferListener(transferListener)
        remote?.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        val source = if (RemoteMediaUri.isRemote(dataSpec.uri)) {
            remote ?: createRemote().also { created ->
                listeners.forEach(created::addTransferListener)
                remote = created
            }
        } else {
            local
        }
        active = source
        return source.open(dataSpec)
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
        active?.read(buffer, offset, length)
            ?: throw DataSourceException(PlaybackException.ERROR_CODE_IO_UNSPECIFIED)

    override fun getUri(): Uri? = active?.uri

    override fun getResponseHeaders(): Map<String, List<String>> = active?.responseHeaders ?: emptyMap()

    override fun close() {
        try {
            active?.close()
        } finally {
            active = null
        }
    }
}

/** The [DataSource.Factory] the playback services give their players. */
@Singleton
@OptIn(UnstableApi::class)
class GridMediaDataSourceFactory @Inject constructor(
    private val pool: ConnectionPool,
    private val connectionRepository: ConnectionRepository,
    private val filler: OfflineFiller
) {
    fun create(context: Context): DataSource.Factory {
        val localFactory = DefaultDataSource.Factory(context)
        return DataSource.Factory {
            RoutingDataSource(localFactory.createDataSource()) {
                RemoteFileDataSource(pool, connectionRepository, filler)
            }
        }
    }
}
