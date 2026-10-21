package com.defnf.grid.data.offline

import android.app.Application
import android.media.MediaScannerConnection
import android.os.Environment
import com.defnf.grid.domain.model.CachedFile
import com.defnf.grid.domain.model.Connection
import com.defnf.grid.domain.model.Protocol
import com.defnf.grid.domain.repository.ConnectionRepository
import com.defnf.grid.util.GridLog
import com.defnf.grid.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The one local copy of every file Grid has fetched: opened, downloaded or streamed.
 *
 * Files live in the public Downloads folder, laid out as the server has them:
 * `Downloads/Grid/<connection>/<server path>`. The layout is the index, so Offline Files just walks
 * the folder and needs no database. Each connection folder holds a hidden marker naming the
 * connection it belongs to, so a folder stays with its connection whatever happens to the names.
 * Unfinished transfers are hidden `.<name>…gridpart` files next to their target; they are never
 * listed and are swapped in by rename only once complete.
 *
 * A file that can't have a place in Downloads (its path differs only in letter case from one
 * already saved, or an earlier install of Grid owns that name) gets an unlisted copy in the app's
 * private cache instead, so it still opens.
 */
@Singleton
class OfflineStore @Inject constructor(
    private val application: Application,
    private val connectionRepository: ConnectionRepository,
    @IoDispatcher private val io: CoroutineDispatcher
) {

    val root: File
        get() = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Grid")

    suspend fun folderFor(connection: Connection): File {
        val all = connectionRepository.getAllConnections().first()
        // Scans the connection folders' markers, and may claim one: disk work.
        return withContext(io) { synchronized(this@OfflineStore) { resolveFolder(root, connection, all) } }
    }

    /**
     * Where [remotePath]'s copy goes: its place in the connection's folder, or an unlisted private
     * copy when that place is already taken by a path differing only in case. Shared storage
     * ignores case, so two such SFTP files would otherwise overwrite each other's copy.
     */
    suspend fun fileFor(connection: Connection, remotePath: String): File {
        val folder = folderFor(connection)
        val segments = segmentsOf(remotePath, connection.protocol)
        // SMB servers ignore case themselves, so differently-cased paths are the same file there.
        val collides = connection.protocol == Protocol.SFTP && withContext(io) { hasCaseCollision(folder, segments) }
        return if (collides) unlistedFileFor(connection, remotePath) else fileIn(folder, remotePath, connection.protocol)
    }

    /** The private, unlisted fallback copy of [remotePath]; the OS may purge it when space is low. */
    fun unlistedFileFor(connection: Connection, remotePath: String): File =
        fileIn(File(application.cacheDir, "unlisted/${connection.id}"), remotePath, connection.protocol)

    /** Every finished offline file in [folder], newest first. */
    fun list(folder: File, connection: Connection): List<CachedFile> =
        folder.walkTopDown()
            .filter { it.isFile && !isPart(it) && it.name != MARKER }
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

    /** Bytes held by unfinished saves in [folder]; they aren't listed, but they do take space. */
    fun unfinishedBytes(folder: File): Long =
        folder.walkTopDown().filter { it.isFile && isPart(it) }.sumOf { it.length() }

    /** Deletes an offline file along with any unfinished saves of it. */
    fun delete(file: File): Boolean {
        partsOf(file).forEach { it.delete() }
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

    /**
     * Renames a connection's folder to match its (new) name. Purely cosmetic: the marker already
     * keeps the folder with its connection. Skipped when another folder has that name.
     */
    fun moveFolder(connection: Connection, all: List<Connection>) = synchronized(this) {
        val current = markedFolder(root, connection.id) ?: return@synchronized
        val wanted = File(root, folderName(connection, all))
        if (current == wanted || wanted.exists()) return@synchronized
        if (!current.renameTo(wanted)) GridLog.w(TAG, "Couldn't move offline folder ${current.name} to ${wanted.name}")
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

        /** Hidden file in each connection folder holding the id of the connection that owns it. */
        const val MARKER = ".grid-connection"

        fun isPart(file: File): Boolean = file.name.endsWith(PART_SUFFIX)

        /** Unfinished saves of [target]: stream parts and per-attempt download parts alike. */
        fun partsOf(target: File): List<File> {
            val prefix = ".${target.name}."
            return target.parentFile?.listFiles { file -> file.name.startsWith(prefix) && isPart(file) }
                ?.toList().orEmpty()
        }

        /**
         * The folder [connection] owns under [root]: the one marked with its id, else the first of
         * its candidate names (see [folderName]) that no other connection has marked, which it then
         * claims. Ownership never moves once claimed, so deleting or renaming another connection
         * can't hand this one a folder of someone else's files.
         */
        internal fun resolveFolder(root: File, connection: Connection, all: List<Connection>): File {
            markedFolder(root, connection.id)?.let { return it }
            val folder = candidateNames(connection, all)
                .map { File(root, it) }
                .firstOrNull { ownerOf(it).let { owner -> owner == null || owner == connection.id } }
                ?: File(root, "${folderName(connection, all)} ${connection.id}")
            // Claim it now, so a later namesake can't take it before the first file lands.
            runCatching {
                folder.mkdirs()
                File(folder, MARKER).writeText(connection.id)
            }.onFailure { GridLog.w(TAG, "Couldn't mark offline folder ${folder.name}", it) }
            return folder
        }

        internal fun markedFolder(root: File, connectionId: String): File? =
            root.listFiles()?.firstOrNull { it.isDirectory && ownerOf(it) == connectionId }

        private fun ownerOf(folder: File): String? =
            runCatching { File(folder, MARKER).takeIf { it.isFile }?.readText()?.trim() }.getOrNull()

        /** A hidden, attempt-unique part file to download [target] into. */
        fun newPartFor(target: File): File =
            File(target.parentFile, ".${target.name}.${java.util.UUID.randomUUID()}$PART_SUFFIX")

        /**
         * The one resumable part file a streamed [target] fills into. Its name carries the server
         * file's size and modified time, so a part is only ever resumed against the same version.
         */
        fun streamPartFor(target: File, size: Long, modified: Long): File =
            File(target.parentFile, ".${target.name}.${versionTag(size, modified)}$PART_SUFFIX")

        /** Resumable parts of [target] left from a different version of the server's file. */
        fun staleStreamParts(target: File, size: Long, modified: Long): List<File> {
            val prefix = ".${target.name}."
            val current = versionTag(size, modified)
            return partsOf(target).filter { file ->
                val tag = file.name.removePrefix(prefix).removeSuffix(PART_SUFFIX)
                STREAM_TAG.matches(tag) && tag != current
            }
        }

        private fun versionTag(size: Long, modified: Long) = "$size-${modified.coerceAtLeast(0L)}"

        /** A stream part's tag; anything else (a download's UUID) is left alone. */
        private val STREAM_TAG = Regex("""\d+(-\d+)?""")

        /**
         * A local copy only counts when it matches the server's size; with no size to compare,
         * any non-empty copy does.
         */
        fun isValid(local: File, remoteSize: Long): Boolean =
            if (remoteSize > 0) local.length() == remoteSize else local.length() > 0

        fun fileIn(folder: File, remotePath: String, protocol: Protocol): File =
            segmentsOf(remotePath, protocol).fold(folder) { dir, segment -> File(dir, segment) }

        /**
         * Server path to local path segments, split on the protocol's own separator: `\` is legal
         * in an SFTP file name (and a local one) but separates folders on SMB. NUL, which no local
         * name may hold, and `.`/`..`, which could climb out of the connection's folder, become `_`.
         */
        fun segmentsOf(remotePath: String, protocol: Protocol): List<String> {
            val parts = when (protocol) {
                Protocol.SMB -> remotePath.split('/', '\\')
                Protocol.SFTP -> remotePath.split('/')
            }
            return parts
                .filter { it.isNotEmpty() }
                .map { segment ->
                    if (segment == "." || segment == "..") "_" else segment.replace('\u0000', '_')
                }
        }

        /**
         * True when some segment of [segments] under [folder] already exists on disk under a name
         * differing only in case, which a case-insensitive filesystem would silently merge.
         * [exists] and [list] are swappable so tests can stand in for such a filesystem.
         */
        internal fun hasCaseCollision(
            folder: File,
            segments: List<String>,
            exists: (File) -> Boolean = File::exists,
            list: (File) -> Array<String>? = File::list
        ): Boolean {
            var dir = folder
            for (segment in segments) {
                val next = File(dir, segment)
                if (!exists(next)) return false
                if (list(dir)?.contains(segment) != true) return true
                dir = next
            }
            return false
        }

        /** The inverse of [segmentsOf] (bar replaced characters), in each protocol's own path style. */
        fun remotePathOf(relativePath: String, protocol: Protocol): String {
            val segments = relativePath.split('/').filter { it.isNotEmpty() }
            return when (protocol) {
                Protocol.SMB -> segments.joinToString("\\", prefix = "\\")
                Protocol.SFTP -> segments.joinToString("/", prefix = "/")
            }
        }

        /**
         * The connection's preferred folder name: its name, or when an older connection already has
         * that name, the name plus the host (plus part of its id if the host matches too). Older
         * ones win so adding a connection never takes another's name.
         */
        fun folderName(connection: Connection, all: List<Connection>): String {
            val names = namesFor(connection)
            val older = all.filter {
                it.id != connection.id &&
                    baseFolderName(it).equals(names[0], ignoreCase = true) &&
                    (it.createdAt < connection.createdAt || (it.createdAt == connection.createdAt && it.id < connection.id))
            }
            return when {
                older.isEmpty() -> names[0]
                older.none { it.hostname.equals(connection.hostname, ignoreCase = true) } -> names[1]
                else -> names[2]
            }
        }

        /** Every name the folder may take, [folderName]'s choice first; [resolveFolder] uses the first unclaimed. */
        private fun candidateNames(connection: Connection, all: List<Connection>): List<String> {
            val preferred = folderName(connection, all)
            return listOf(preferred) + (namesFor(connection) - preferred)
        }

        private fun namesFor(connection: Connection): List<String> {
            val base = baseFolderName(connection)
            val withHost = "$base (${sanitize(connection.hostname)})"
            return listOf(base, withHost, "$withHost ${connection.id.take(8)}")
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
