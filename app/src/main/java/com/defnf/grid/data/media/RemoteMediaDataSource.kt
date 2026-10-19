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
import com.defnf.grid.data.remote.pool.ConnectionPool
import com.defnf.grid.domain.repository.ConnectionRepository
import kotlinx.coroutines.runBlocking
import java.io.IOException
import java.io.InputStream
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
 * Reads a `grid://` URI from the remote server through the shared [ConnectionPool], reusing the
 * already-open session the file browser was using.
 */
@OptIn(UnstableApi::class)
class RemoteFileDataSource(
    private val pool: ConnectionPool,
    private val connectionRepository: ConnectionRepository
) : BaseDataSource(/* isNetwork = */ true) {

    private var uri: Uri? = null
    private var stream: InputStream? = null
    private var bytesRemaining = 0L
    private var opened = false

    override fun open(dataSpec: DataSpec): Long {
        val target = dataSpec.uri
        uri = target
        transferInitializing(dataSpec)

        val input = try {
            // DataSource calls happen on the player's loader thread, which may block.
            runBlocking {
                val connection = connectionRepository.getConnectionById(RemoteMediaUri.connectionId(target))
                pool.openStream(connection) { client ->
                    client.openInputStream(RemoteMediaUri.remotePath(target), dataSpec.position)
                }
            }
        } catch (e: IOException) {
            throw DataSourceException(e, PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED)
        } catch (e: Exception) {
            throw DataSourceException(e, PlaybackException.ERROR_CODE_IO_UNSPECIFIED)
        }
        stream = input

        val size = RemoteMediaUri.size(target)
        bytesRemaining = when {
            dataSpec.length != C.LENGTH_UNSET.toLong() -> dataSpec.length
            size > 0 -> (size - dataSpec.position).coerceAtLeast(0L)
            else -> C.LENGTH_UNSET.toLong()
        }
        opened = true
        transferStarted(dataSpec)
        return bytesRemaining
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (bytesRemaining == 0L) return C.RESULT_END_OF_INPUT
        val input = stream ?: throw DataSourceException(PlaybackException.ERROR_CODE_IO_UNSPECIFIED)

        val toRead = if (bytesRemaining == C.LENGTH_UNSET.toLong()) {
            length
        } else {
            minOf(bytesRemaining, length.toLong()).toInt()
        }
        val read = try {
            input.read(buffer, offset, toRead)
        } catch (e: IOException) {
            throw DataSourceException(e, PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED)
        }
        if (read == -1) return C.RESULT_END_OF_INPUT

        if (bytesRemaining != C.LENGTH_UNSET.toLong()) bytesRemaining -= read
        bytesTransferred(read)
        return read
    }

    override fun getUri(): Uri? = uri

    override fun close() {
        uri = null
        try {
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
}

/**
 * Routes `grid://` URIs to [RemoteFileDataSource] and everything else (local files of cached
 * downloads) to Media3's [DefaultDataSource].
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
    private val connectionRepository: ConnectionRepository
) {
    fun create(context: Context): DataSource.Factory {
        val localFactory = DefaultDataSource.Factory(context)
        return DataSource.Factory {
            RoutingDataSource(localFactory.createDataSource()) {
                RemoteFileDataSource(pool, connectionRepository)
            }
        }
    }
}
