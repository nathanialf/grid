package com.defnf.grid.data.provider

import android.database.Cursor
import android.database.MatrixCursor
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Handler
import android.os.HandlerThread
import android.os.OperationCanceledException
import android.os.ParcelFileDescriptor
import android.os.storage.StorageManager
import android.provider.DocumentsContract
import android.provider.DocumentsProvider
import android.webkit.MimeTypeMap
import androidx.annotation.RequiresApi
import com.defnf.grid.R
import com.defnf.grid.data.offline.OfflineStore
import com.defnf.grid.data.remote.pool.ConnectionPool
import com.defnf.grid.domain.model.Connection
import com.defnf.grid.domain.model.Protocol
import com.defnf.grid.domain.model.RemoteFile
import com.defnf.grid.domain.repository.ConnectionRepository
import com.defnf.grid.domain.repository.FileRepository
import com.defnf.grid.util.GridLog
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.io.FileNotFoundException
import java.io.FilterInputStream
import java.io.InputStream
import java.io.InterruptedIOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Semaphore
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Shows each connection as a root in the system file picker, so other apps can pick (and upload)
 * files straight from a server.
 *
 * Each root opens at the connection's SFTP starting directory or SMB share. Directory listings are
 * remembered briefly, because the picker and the app it returns to ask about the same folder's files
 * several times in a row. Opening a file always asks the server for its current size and date, then
 * reads Grid's offline copy if that is up to date, and otherwise streams the file through a seekable
 * descriptor the app gets at once; only Android 7 downloads the file first.
 */
class GridDocumentsProvider : DocumentsProvider() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface GridDocumentsProviderEntryPoint {
        fun connectionRepository(): ConnectionRepository
        fun fileRepository(): FileRepository
        fun connectionPool(): ConnectionPool
        fun offlineStore(): OfflineStore
    }

    private val entryPoint by lazy {
        EntryPointAccessors.fromApplication(
            context!!.applicationContext,
            GridDocumentsProviderEntryPoint::class.java
        )
    }

    private val connectionRepository by lazy { entryPoint.connectionRepository() }
    private val fileRepository by lazy { entryPoint.fileRepository() }
    private val pool by lazy { entryPoint.connectionPool() }
    private val offlineStore by lazy { entryPoint.offlineStore() }

    private class Listing(val files: List<RemoteFile>, val loadedAtMs: Long)

    /** Recent folder listings by [listingKey], least recently used first. */
    private val listings = object : LinkedHashMap<String, Listing>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Listing>?): Boolean =
            size > MAX_LISTINGS
    }

    /** Per connection, how many more server streams other apps' open files may hold right now. */
    private val streamSlots = ConcurrentHashMap<String, Semaphore>()

    companion object {
        private const val TAG = "GridDocuments"
        private const val AUTHORITY = "com.defnf.grid.documents"

        /** How long a listing answers questions about the files in it without asking the server. */
        private const val LISTING_FRESH_MS = 30_000L

        /** The oldest listing still shown, with an error, when the server can't be reached. */
        private const val LISTING_FALLBACK_MS = 5 * 60_000L
        private const val MAX_LISTINGS = 64

        /**
         * Server streams other apps' open files may hold per connection. Each holds one of its pooled
         * session's [com.defnf.grid.data.remote.pool.PoolConfig.maxConcurrencyPerSession] slots for
         * as long as it is read, so this leaves the rest for browsing, in the picker and in Grid.
         * Further files wait their turn on their own descriptor threads.
         */
        private const val STREAM_SLOTS = 2

        private const val TEMP_DIR = "grid_documents"
        private const val STALE_TEMP_MS = 10 * 60_000L

        fun notifyRootsChanged(context: android.content.Context) {
            try {
                val uri = DocumentsContract.buildRootsUri(AUTHORITY)
                context.contentResolver.notifyChange(uri, null)
                GridLog.d(TAG) { "Notified system of roots change" }
            } catch (e: Exception) {
                GridLog.e(TAG, "Failed to notify roots change", e)
            }
        }

        private val DEFAULT_ROOT_PROJECTION = arrayOf(
            DocumentsContract.Root.COLUMN_ROOT_ID,
            DocumentsContract.Root.COLUMN_MIME_TYPES,
            DocumentsContract.Root.COLUMN_FLAGS,
            DocumentsContract.Root.COLUMN_ICON,
            DocumentsContract.Root.COLUMN_TITLE,
            DocumentsContract.Root.COLUMN_SUMMARY,
            DocumentsContract.Root.COLUMN_DOCUMENT_ID,
            DocumentsContract.Root.COLUMN_AVAILABLE_BYTES
        )

        private val DEFAULT_DOCUMENT_PROJECTION = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
            DocumentsContract.Document.COLUMN_FLAGS,
            DocumentsContract.Document.COLUMN_SIZE
        )
    }

    override fun onCreate(): Boolean {
        // Before 1.2.2 every opened document was downloaded here and never deleted.
        Thread({ deleteStaleTempFiles() }, "GridDocumentsCleanup").start()
        return true
    }

    override fun queryRoots(projection: Array<String>?): Cursor {
        val result = MatrixCursor(projection ?: DEFAULT_ROOT_PROJECTION)
        try {
            val connections = runBlocking {
                // Never hang the system picker on a slow preferences read.
                withTimeoutOrNull(5000) { connectionRepository.getAllConnections().first() }
            } ?: emptyList<Connection>().also { GridLog.w(TAG, "Timed out loading connections") }

            for (connection in connections) {
                val root = rootOf(connection)
                val summary = buildString {
                    append("${connection.protocol.name} - ${connection.hostname}")
                    if (connection.protocol == Protocol.SFTP && root != "/") append(" $root")
                }
                val row = result.newRow()
                row.add(DocumentsContract.Root.COLUMN_ROOT_ID, connection.id)
                row.add(DocumentsContract.Root.COLUMN_MIME_TYPES, "*/*")
                row.add(
                    DocumentsContract.Root.COLUMN_FLAGS,
                    DocumentsContract.Root.FLAG_SUPPORTS_IS_CHILD or DocumentsContract.Root.FLAG_LOCAL_ONLY
                )
                row.add(DocumentsContract.Root.COLUMN_ICON, R.mipmap.ic_launcher)
                row.add(DocumentsContract.Root.COLUMN_TITLE, "Grid: ${connection.name}")
                row.add(DocumentsContract.Root.COLUMN_SUMMARY, summary)
                row.add(DocumentsContract.Root.COLUMN_DOCUMENT_ID, documentIdOf(connection, root))
                row.add(DocumentsContract.Root.COLUMN_AVAILABLE_BYTES, -1)
            }
        } catch (e: Exception) {
            // Never crash the system picker.
            GridLog.e(TAG, "Error in queryRoots", e)
        }
        return result
    }

    override fun queryDocument(documentId: String, projection: Array<String>?): Cursor {
        val result = MatrixCursor(projection ?: DEFAULT_DOCUMENT_PROJECTION)
        try {
            runBlocking {
                val (connection, path) = resolve(documentId)
                val root = rootOf(connection)
                if (path == root || DocumentPaths.parentOf(path, connection.protocol) == null) {
                    addFolderRow(result, documentId, if (path == root) connection.name else path)
                } else {
                    findFile(connection, path)?.let { addFileRow(result, documentId, it) }
                }
            }
        } catch (e: Exception) {
            GridLog.e(TAG, "Error in queryDocument", e)
        }
        return result
    }

    override fun queryChildDocuments(
        parentDocumentId: String,
        projection: Array<String>?,
        sortOrder: String?
    ): Cursor {
        val result = MatrixCursor(projection ?: DEFAULT_DOCUMENT_PROJECTION)
        try {
            runBlocking {
                val (connection, path) = resolve(parentDocumentId)
                val files = try {
                    listChildren(connection, path)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // A blip shouldn't empty a folder the user was just looking at, but they should
                    // know it may be out of date.
                    GridLog.w(TAG, "Listing $path failed", e)
                    val fallback = cachedListing(connection, path, LISTING_FALLBACK_MS)
                    val reason = e.message ?: "connection failed"
                    result.extras = Bundle().apply {
                        putString(
                            DocumentsContract.EXTRA_ERROR,
                            if (fallback != null) {
                                "Couldn't reach ${connection.name} ($reason). Showing the last listing."
                            } else {
                                "Couldn't reach ${connection.name}: $reason"
                            }
                        )
                    }
                    fallback.orEmpty()
                }
                for (file in files) {
                    addFileRow(result, documentIdOf(connection, file.path), file)
                }
            }
        } catch (e: Exception) {
            GridLog.e(TAG, "Error in queryChildDocuments", e)
        }
        return result
    }

    override fun isChildDocument(parentDocumentId: String, documentId: String): Boolean = try {
        parentDocumentId.substringBefore(':') == documentId.substringBefore(':') && runBlocking {
            val (connection, parentPath) = resolve(parentDocumentId)
            DocumentPaths.isWithin(pathOf(documentId, connection), parentPath, connection.protocol)
        }
    } catch (e: Exception) {
        GridLog.e(TAG, "Error in isChildDocument", e)
        false
    }

    override fun openDocument(
        documentId: String,
        mode: String,
        signal: CancellationSignal?
    ): ParcelFileDescriptor {
        try {
            val (connection, file) = runCancellable(signal) {
                val (connection, path) = resolve(documentId)
                // Asked fresh, not from a listing: a streamed file ends where this size says, so a
                // size from before the file last changed would cut the app's copy short.
                val file = fileRepository.getFile(connection, path)
                if (file.isDirectory) throw FileNotFoundException("$path is a folder")
                connection to file
            }

            openOfflineCopy(connection, file)?.let { return it }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                openStreaming(connection, file)?.let { return it }
            }
            return openDownloaded(connection, file, signal)
        } catch (e: OperationCanceledException) {
            throw e
        } catch (e: FileNotFoundException) {
            GridLog.w(TAG, "Couldn't open $documentId", e)
            throw e
        } catch (e: Exception) {
            GridLog.e(TAG, "Error in openDocument", e)
            throw FileNotFoundException("Cannot open document: ${e.message}")
        }
    }

    /**
     * Grid's own offline copy of [file], when it has a complete one saved since the file last changed
     * on the server: no network at all. A copy only matching in size may be an older version.
     */
    private fun openOfflineCopy(connection: Connection, file: RemoteFile): ParcelFileDescriptor? = try {
        offlineStore.existingCopyOf(connection, file.path)
            ?.takeIf { OfflineStore.isValid(it, file.size) && it.lastModified() >= file.lastModified }
            ?.let { ParcelFileDescriptor.open(it, ParcelFileDescriptor.MODE_READ_ONLY) }
    } catch (e: Exception) {
        // No storage access, or the copy went away: read the server instead.
        GridLog.w(TAG, "Couldn't open the offline copy of ${file.path}", e)
        null
    }

    /**
     * A descriptor reading [file] from the server as the app asks for bytes, so the app can start
     * (and finish) its upload without first waiting for Grid to download the whole file. Nothing is
     * opened on the server until the first read. Null when the system can't make the descriptor.
     */
    @RequiresApi(Build.VERSION_CODES.O)
    private fun openStreaming(connection: Connection, file: RemoteFile): ParcelFileDescriptor? {
        val reader = RemoteRangeReader(file.size) { offset -> openRemote(connection, file.path, offset) }
        val thread = HandlerThread("GridDocument").apply { start() }
        return try {
            val handler = Handler(thread.looper)
            val storage = context!!.getSystemService(StorageManager::class.java)
            storage.openProxyFileDescriptor(
                ParcelFileDescriptor.MODE_READ_ONLY,
                RemoteFileProxyCallback(reader, thread, handler, file.name),
                handler
            )
        } catch (e: Exception) {
            GridLog.w(TAG, "Couldn't stream ${file.path}; downloading it instead", e)
            thread.quitSafely()
            null
        }
    }

    /**
     * Opens [path] at [offset] once one of the connection's [STREAM_SLOTS] is free; closing the
     * stream frees it. Called on a descriptor's own thread, never a binder thread, so waiting is fine.
     */
    private fun openRemote(connection: Connection, path: String, offset: Long): InputStream {
        val slots = streamSlots.getOrPut(connection.id) { Semaphore(STREAM_SLOTS) }
        try {
            slots.acquire()
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            throw InterruptedIOException()
        }
        val stream = try {
            runBlocking { pool.openFile(connection, path, offset) }
        } catch (e: Throwable) {
            slots.release()
            if (e is InterruptedException) {
                Thread.currentThread().interrupt()
                throw InterruptedIOException()
            }
            throw e
        }
        return object : FilterInputStream(stream) {
            private val released = AtomicBoolean(false)

            override fun close() {
                try {
                    super.close()
                } finally {
                    if (released.compareAndSet(false, true)) slots.release()
                }
            }
        }
    }

    /** Downloads [file] and hands over the copy. The descriptor keeps it readable once deleted. */
    private fun openDownloaded(
        connection: Connection,
        file: RemoteFile,
        signal: CancellationSignal?
    ): ParcelFileDescriptor {
        val temp = runCancellable(signal) { fileRepository.downloadFileToTemp(connection, file) }
        return try {
            ParcelFileDescriptor.open(temp, ParcelFileDescriptor.MODE_READ_ONLY)
        } finally {
            temp.delete()
        }
    }

    /**
     * [runBlocking] that [signal] can stop: once the caller cancels, the work is cancelled and this
     * throws [OperationCanceledException], as the framework expects of a cancelled call.
     */
    private fun <T> runCancellable(signal: CancellationSignal?, block: suspend CoroutineScope.() -> T): T =
        runBlocking {
            val work = async(block = block)
            signal?.setOnCancelListener { work.cancel() }
            try {
                work.await()
            } catch (e: CancellationException) {
                if (signal?.isCanceled == true) throw OperationCanceledException()
                throw e
            } finally {
                signal?.setOnCancelListener(null)
            }
        }

    private suspend fun resolve(documentId: String): Pair<Connection, String> {
        val connection = connectionRepository.getConnectionById(documentId.substringBefore(':'))
        return connection to pathOf(documentId, connection)
    }

    /** The server path [documentId] names on [connection]; an ID with no path names its root. */
    private fun pathOf(documentId: String, connection: Connection): String =
        if (':' in documentId) {
            DocumentPaths.normalize(documentId.substringAfter(':'), connection.protocol)
        } else {
            rootOf(connection)
        }

    private fun rootOf(connection: Connection): String =
        DocumentPaths.rootOf(connection.protocol, connection.startingDirectory)

    private fun documentIdOf(connection: Connection, path: String): String =
        "${connection.id}:${DocumentPaths.normalize(path, connection.protocol)}"

    /** [path]'s entry in its folder's listing, reusing a recent listing when it has the file. */
    private suspend fun findFile(connection: Connection, path: String): RemoteFile? {
        val folder = DocumentPaths.parentOf(path, connection.protocol) ?: return null
        val name = DocumentPaths.nameOf(path, connection.protocol)
        cachedListing(connection, folder, LISTING_FRESH_MS)
            ?.firstOrNull { it.name == name }
            ?.let { return it }
        return listChildren(connection, folder).firstOrNull { it.name == name }
    }

    private suspend fun listChildren(connection: Connection, folder: String): List<RemoteFile> {
        val files = fileRepository.listFiles(connection, folder)
        synchronized(listings) {
            listings[listingKey(connection, folder)] = Listing(files, System.currentTimeMillis())
        }
        return files
    }

    private fun cachedListing(connection: Connection, folder: String, maxAgeMs: Long): List<RemoteFile>? =
        synchronized(listings) { listings[listingKey(connection, folder)] }
            ?.takeIf { System.currentTimeMillis() - it.loadedAtMs <= maxAgeMs }
            ?.files

    /**
     * Keyed on where the connection points as well as its id, so a listing from before the user
     * changed its server or share is never shown for the new one.
     */
    private fun listingKey(connection: Connection, folder: String): String = listOf(
        connection.id,
        connection.protocol,
        connection.hostname,
        connection.effectivePort,
        connection.shareName,
        DocumentPaths.normalize(folder, connection.protocol)
    ).joinToString("\u0000")

    private fun addFolderRow(result: MatrixCursor, documentId: String, displayName: String) {
        val row = result.newRow()
        row.add(DocumentsContract.Document.COLUMN_DOCUMENT_ID, documentId)
        row.add(DocumentsContract.Document.COLUMN_MIME_TYPE, DocumentsContract.Document.MIME_TYPE_DIR)
        row.add(DocumentsContract.Document.COLUMN_DISPLAY_NAME, displayName)
        row.add(DocumentsContract.Document.COLUMN_LAST_MODIFIED, null)
        row.add(DocumentsContract.Document.COLUMN_FLAGS, 0)
        row.add(DocumentsContract.Document.COLUMN_SIZE, null)
    }

    private fun addFileRow(result: MatrixCursor, documentId: String, file: RemoteFile) {
        val mimeType = if (file.isDirectory) {
            DocumentsContract.Document.MIME_TYPE_DIR
        } else {
            getMimeType(file.name)
        }
        val row = result.newRow()
        row.add(DocumentsContract.Document.COLUMN_DOCUMENT_ID, documentId)
        row.add(DocumentsContract.Document.COLUMN_MIME_TYPE, mimeType)
        row.add(DocumentsContract.Document.COLUMN_DISPLAY_NAME, file.name)
        row.add(DocumentsContract.Document.COLUMN_LAST_MODIFIED, file.lastModified.takeIf { it > 0 })
        row.add(DocumentsContract.Document.COLUMN_FLAGS, 0)
        row.add(DocumentsContract.Document.COLUMN_SIZE, if (file.isDirectory) null else file.size)
    }

    private fun getMimeType(fileName: String): String {
        val extension = fileName.substringAfterLast('.', "")
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.lowercase()) ?: "application/octet-stream"
    }

    /**
     * Removes leftovers older than [STALE_TEMP_MS]. Folders are aged too: one just made by an
     * Android 7 download in progress must not be removed before its file lands in it.
     */
    private fun deleteStaleTempFiles() {
        val dir = File(System.getProperty("java.io.tmpdir"), TEMP_DIR)
        val cutoff = System.currentTimeMillis() - STALE_TEMP_MS
        // Ages are all read before anything is deleted, since deleting a file freshens its folder.
        dir.walkBottomUp()
            .filter { it != dir && it.lastModified() < cutoff }
            .toList()
            // A folder only goes once it is empty.
            .forEach { it.delete() }
    }
}
