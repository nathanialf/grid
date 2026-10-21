package com.defnf.grid.data.offline

import android.app.Application
import android.media.MediaScannerConnection
import android.os.Environment
import com.defnf.grid.domain.model.CachedFile
import com.defnf.grid.domain.model.Connection
import com.defnf.grid.domain.model.Protocol
import com.defnf.grid.domain.repository.ConnectionRepository
import com.defnf.grid.util.GridLog
import kotlinx.coroutines.flow.first
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The one local copy of every file Grid has fetched: opened, downloaded or streamed.
 *
 * Files live in the public Downloads folder, laid out as the server has them:
 * `Downloads/Grid/<connection>/<server path>`. The layout is the index, so Offline Files just walks
 * the folder and needs no database. Unfinished transfers are hidden `.<name>…gridpart` files next
 * to their target; they are never listed and are swapped in by rename only once complete.
 */
@Singleton
class OfflineStore @Inject constructor(
    private val application: Application,
    private val connectionRepository: ConnectionRepository
) {

    val root: File
        get() = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Grid")

    suspend fun folderFor(connection: Connection): File =
        File(root, folderName(connection, connectionRepository.getAllConnections().first()))

    suspend fun fileFor(connection: Connection, remotePath: String): File =
        fileIn(folderFor(connection), remotePath)

    /** Every finished offline file in [folder], newest first. */
    fun list(folder: File, connection: Connection): List<CachedFile> =
        folder.walkTopDown()
            .filter { it.isFile && !isPart(it) }
            .map { file ->
                CachedFile(
                    name = file.name,
                    path = file.absolutePath,
                    remotePath = remotePathOf(file.relativeTo(folder).invariantSeparatorsPath, connection.protocol),
                    size = file.length(),
                    cachedAt = file.lastModified(),
                    connectionId = connection.id
                )
            }
            .sortedByDescending { it.cachedAt }
            .toList()

    fun delete(file: File): Boolean {
        val deleted = !file.exists() || file.delete()
        if (deleted) {
            pruneEmptyParents(file.parentFile)
            publish(file)
        }
        return deleted
    }

    /** Deletes everything under [folder], unfinished transfers included. */
    fun clear(folder: File): Boolean {
        if (!folder.exists()) return true
        val files = folder.walkTopDown().filter { it.isFile }.toList()
        val allDeleted = files.fold(true) { ok, file -> file.delete() && ok }
        folder.walkBottomUp().filter { it.isDirectory }.forEach { it.delete() }
        files.filterNot { isPart(it) }.forEach(::publish)
        return allDeleted
    }

    /**
     * Files written straight to shared storage aren't in MediaStore until scanned, so other apps
     * (file managers, galleries) wouldn't see them. Scanning a deleted path removes its entry.
     */
    fun publish(file: File) {
        MediaScannerConnection.scanFile(application, arrayOf(file.absolutePath), null, null)
    }

    /** Keeps a connection's folder following its name when the user renames it. */
    fun moveFolder(before: Connection, allBefore: List<Connection>, after: Connection, allAfter: List<Connection>) {
        val from = File(root, folderName(before, allBefore))
        val to = File(root, folderName(after, allAfter))
        if (from == to || !from.exists() || to.exists()) return
        if (!from.renameTo(to)) GridLog.w(TAG, "Couldn't move offline folder ${from.name} to ${to.name}")
    }

    /** 1.2.0 cached opened files privately under names that can't be mapped back to server paths. */
    fun deleteLegacyCache() {
        File(application.cacheDir, "opened_files").takeIf { it.exists() }?.deleteRecursively()
    }

    private fun pruneEmptyParents(start: File?) {
        var dir = start
        val stop = root.absoluteFile
        while (dir != null && dir.absoluteFile != stop && dir.list()?.isEmpty() == true) {
            dir.delete()
            dir = dir.parentFile
        }
    }

    companion object {
        private const val TAG = "GridOffline"
        const val PART_SUFFIX = ".gridpart"

        fun isPart(file: File): Boolean = file.name.endsWith(PART_SUFFIX)

        /** A hidden, attempt-unique part file to download [target] into. */
        fun newPartFor(target: File): File =
            File(target.parentFile, ".${target.name}.${java.util.UUID.randomUUID()}$PART_SUFFIX")

        /** The one resumable part file a streamed [target] of [size] bytes fills into. */
        fun streamPartFor(target: File, size: Long): File =
            File(target.parentFile, ".${target.name}.$size$PART_SUFFIX")

        /** Resumable parts of [target] left from when the server's copy had a different size. */
        fun staleStreamParts(target: File, size: Long): List<File> {
            val prefix = ".${target.name}."
            return target.parentFile?.listFiles { file ->
                val middle = file.name.removePrefix(prefix).removeSuffix(PART_SUFFIX)
                file.name.startsWith(prefix) && isPart(file) &&
                    middle.isNotEmpty() && middle.all(Char::isDigit) && middle != size.toString()
            }?.toList().orEmpty()
        }

        /**
         * A local copy only counts when it matches the server's size; with no size to compare,
         * any non-empty copy does.
         */
        fun isValid(local: File, remoteSize: Long): Boolean =
            if (remoteSize > 0) local.length() == remoteSize else local.length() > 0

        fun fileIn(folder: File, remotePath: String): File =
            segmentsOf(remotePath).fold(folder) { dir, segment -> File(dir, segment) }

        /**
         * Server path to local path segments. Both separators split, so SFTP (`/a/b`) and SMB
         * (`\a\b`) map alike, and `.`/`..` can never climb out of the connection's folder.
         */
        fun segmentsOf(remotePath: String): List<String> =
            remotePath.split('/', '\\')
                .filter { it.isNotEmpty() }
                .map { segment ->
                    if (segment == "." || segment == "..") "_" else segment.replace('\u0000', '_')
                }

        /** The inverse of [segmentsOf], in each protocol's own path style. */
        fun remotePathOf(relativePath: String, protocol: Protocol): String {
            val segments = relativePath.split('/').filter { it.isNotEmpty() }
            return when (protocol) {
                Protocol.SMB -> segments.joinToString("\\", prefix = "\\")
                Protocol.SFTP -> segments.joinToString("/", prefix = "/")
            }
        }

        /**
         * The connection's folder: its name, or when an older connection already has that name,
         * the name plus the host. Older ones win so adding a connection never moves another's files.
         */
        fun folderName(connection: Connection, all: List<Connection>): String {
            val base = baseFolderName(connection)
            val older = all.filter {
                it.id != connection.id &&
                    baseFolderName(it).equals(base, ignoreCase = true) &&
                    (it.createdAt < connection.createdAt || (it.createdAt == connection.createdAt && it.id < connection.id))
            }
            if (older.isEmpty()) return base
            val withHost = "$base (${sanitize(connection.hostname)})"
            return if (older.none { it.hostname.equals(connection.hostname, ignoreCase = true) }) {
                withHost
            } else {
                "$withHost ${connection.id.take(8)}"
            }
        }

        private fun baseFolderName(connection: Connection): String =
            sanitize(connection.name).ifEmpty { sanitize(connection.hostname) }.ifEmpty { connection.id }

        /** Connection names are free text; keep them to characters every filesystem accepts. */
        private fun sanitize(name: String): String =
            name.trim()
                .map { c -> if (c < ' ' || c in "/\\:*?\"<>|") '_' else c }
                .joinToString("")
                .trim('.', ' ')
    }
}
