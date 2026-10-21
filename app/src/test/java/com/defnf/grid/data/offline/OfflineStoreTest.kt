package com.defnf.grid.data.offline

import com.defnf.grid.domain.model.Connection
import com.defnf.grid.domain.model.Protocol
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineStoreTest {

    private fun connection(id: String, name: String, host: String = "nas.local", createdAt: Long = 0L) =
        Connection(
            id = id,
            name = name,
            hostname = host,
            protocol = Protocol.SFTP,
            credentialId = "cred",
            createdAt = createdAt
        )

    @Test
    fun `server paths mirror into the connection folder`() {
        val folder = File("/offline/NAS")
        assertEquals(File("/offline/NAS/home/me/song.mp3"), OfflineStore.fileIn(folder, "/home/me/song.mp3", Protocol.SFTP))
        assertEquals(File("/offline/NAS/Music/song.mp3"), OfflineStore.fileIn(folder, "\\Music\\song.mp3", Protocol.SMB))
    }

    @Test
    fun `a backslash is part of an sftp file name, not a folder break`() {
        assertEquals(listOf("a\\b.txt"), OfflineStore.segmentsOf("/a\\b.txt", Protocol.SFTP))
        assertEquals("/a\\b.txt", OfflineStore.remotePathOf("a\\b.txt", Protocol.SFTP))
        assertEquals(listOf("a", "b.txt"), OfflineStore.segmentsOf("\\a\\b.txt", Protocol.SMB))
    }

    @Test
    fun `dot segments can't climb out of the connection folder`() {
        assertEquals(listOf("_", "etc", "_", "passwd"), OfflineStore.segmentsOf("/../etc/./passwd", Protocol.SFTP))
    }

    @Test
    fun `a path matching a saved one only by case is a collision`() {
        // Stand in for case-insensitive shared storage holding /Docs/Notes.txt.
        val folder = File("/offline/NAS")
        val onDisk = mapOf(folder to arrayOf("Docs"), File(folder, "Docs") to arrayOf("Notes.txt"))
        val exists = { file: File ->
            val parent = file.parentFile
            onDisk.entries.any { (dir, names) ->
                dir.path.equals(parent?.path, ignoreCase = true) && names.any { it.equals(file.name, ignoreCase = true) }
            }
        }
        val list = { dir: File -> onDisk.entries.firstOrNull { it.key.path.equals(dir.path, ignoreCase = true) }?.value }

        assertFalse(OfflineStore.hasCaseCollision(folder, listOf("Docs", "Notes.txt"), exists, list))
        assertTrue(OfflineStore.hasCaseCollision(folder, listOf("docs", "notes.txt"), exists, list))
        assertTrue(OfflineStore.hasCaseCollision(folder, listOf("Docs", "notes.txt"), exists, list))
        assertFalse(OfflineStore.hasCaseCollision(folder, listOf("Docs", "Other.txt"), exists, list))
    }

    @Test
    fun `a claimed folder stays with its connection when namesakes come and go`() {
        val root = Files.createTempDirectory("grid").toFile()
        val first = connection("a", "NAS", host = "10.0.0.1", createdAt = 1)
        val second = connection("b", "NAS", host = "10.0.0.2", createdAt = 2)

        val firstFolder = OfflineStore.resolveFolder(root, first, listOf(first, second))
        val secondFolder = OfflineStore.resolveFolder(root, second, listOf(first, second))
        assertEquals("NAS", firstFolder.name)
        assertEquals("NAS (10.0.0.2)", secondFolder.name)

        // Deleting the older one must not hand its folder (and files) to the newer one.
        assertEquals(secondFolder, OfflineStore.resolveFolder(root, second, listOf(second)))
        // Nor may renaming it.
        val renamed = first.copy(name = "Home")
        assertEquals(secondFolder, OfflineStore.resolveFolder(root, second, listOf(renamed, second)))
        assertEquals(firstFolder, OfflineStore.resolveFolder(root, renamed, listOf(renamed, second)))
    }

    @Test
    fun `a folder that is already claimed is skipped for the next free name`() {
        val root = Files.createTempDirectory("grid").toFile()
        val old = connection("a", "NAS", createdAt = 1)
        OfflineStore.resolveFolder(root, old, listOf(old))

        // A new connection that would prefer "NAS" now that the old one is gone can't have it.
        val new = connection("b", "NAS", createdAt = 2)
        assertEquals("NAS (nas.local)", OfflineStore.resolveFolder(root, new, listOf(new)).name)
    }

    @Test
    fun `local paths map back to each protocol's own style`() {
        assertEquals("/home/me/a b.txt", OfflineStore.remotePathOf("home/me/a b.txt", Protocol.SFTP))
        assertEquals("\\Music\\song.mp3", OfflineStore.remotePathOf("Music/song.mp3", Protocol.SMB))
    }

    @Test
    fun `folder is the connection name, with the host added only for a newer namesake`() {
        val first = connection("a", "NAS", host = "10.0.0.1", createdAt = 1)
        val second = connection("b", "nas", host = "10.0.0.2", createdAt = 2)
        val all = listOf(first, second)
        assertEquals("NAS", OfflineStore.folderName(first, all))
        assertEquals("nas (10.0.0.2)", OfflineStore.folderName(second, all))
    }

    @Test
    fun `same name and host still get distinct folders`() {
        val first = connection("aaaaaaaa-1", "NAS", createdAt = 1)
        val second = connection("bbbbbbbb-2", "NAS", createdAt = 2)
        assertEquals("NAS (nas.local) bbbbbbbb", OfflineStore.folderName(second, listOf(first, second)))
    }

    @Test
    fun `connection names are made safe for the filesystem`() {
        assertEquals("Home_Work", OfflineStore.folderName(connection("a", " Home/Work. "), emptyList()))
        assertEquals("nas.local", OfflineStore.folderName(connection("a", "  "), emptyList()))
    }

    @Test
    fun `a copy only counts when it matches the server size`() {
        val file = Files.createTempFile("offline", ".bin").toFile().apply { writeBytes(ByteArray(10)) }
        assertTrue(OfflineStore.isValid(file, 10))
        assertFalse(OfflineStore.isValid(file, 11))
        assertTrue(OfflineStore.isValid(file, 0))
    }

    @Test
    fun `stream parts of another version are stale, other part files are left alone`() {
        val dir = Files.createTempDirectory("offline").toFile()
        val target = File(dir, "song.mp3")
        val current = OfflineStore.streamPartFor(target, 100, 5_000).apply { createNewFile() }
        val otherSize = OfflineStore.streamPartFor(target, 90, 5_000).apply { createNewFile() }
        val sameSizeEdited = OfflineStore.streamPartFor(target, 100, 6_000).apply { createNewFile() }
        val download = OfflineStore.newPartFor(target).apply { createNewFile() }

        assertEquals(
            setOf(otherSize, sameSizeEdited),
            OfflineStore.staleStreamParts(target, 100, 5_000).toSet()
        )
        assertTrue(OfflineStore.isPart(current))
        assertTrue(OfflineStore.isPart(download))
        assertFalse(OfflineStore.isPart(target))
    }

    @Test
    fun `unfinished saves are counted and go with their file`() {
        val folder = Files.createTempDirectory("offline").toFile()
        val store = OfflineStore(mockk(relaxed = true), mockk(), Dispatchers.Unconfined)
        val target = File(folder, "music/song.mp3").apply { parentFile!!.mkdirs(); writeBytes(ByteArray(10)) }
        OfflineStore.streamPartFor(target, 100, 0).writeBytes(ByteArray(30))
        OfflineStore.newPartFor(target).writeBytes(ByteArray(5))
        File(folder, OfflineStore.MARKER).writeText("c1")

        assertEquals(35L, store.unfinishedBytes(folder))
        assertEquals(listOf("song.mp3"), store.list(folder, connection("c1", "NAS")).map { it.name })

        assertTrue(store.delete(target))
        assertEquals(0L, store.unfinishedBytes(folder))
    }
}
