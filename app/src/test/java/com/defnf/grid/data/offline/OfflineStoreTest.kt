package com.defnf.grid.data.offline

import com.defnf.grid.domain.model.Connection
import com.defnf.grid.domain.model.Protocol
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
        assertEquals(File("/offline/NAS/home/me/song.mp3"), OfflineStore.fileIn(folder, "/home/me/song.mp3"))
        assertEquals(File("/offline/NAS/Music/song.mp3"), OfflineStore.fileIn(folder, "\\Music\\song.mp3"))
    }

    @Test
    fun `dot segments can't climb out of the connection folder`() {
        assertEquals(listOf("_", "etc", "_", "passwd"), OfflineStore.segmentsOf("/../etc/./passwd"))
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
    fun `stream parts for another size are stale, other part files are left alone`() {
        val dir = Files.createTempDirectory("offline").toFile()
        val target = File(dir, "song.mp3")
        val current = OfflineStore.streamPartFor(target, 100).apply { createNewFile() }
        val stale = OfflineStore.streamPartFor(target, 90).apply { createNewFile() }
        val download = OfflineStore.newPartFor(target).apply { createNewFile() }

        assertEquals(listOf(stale), OfflineStore.staleStreamParts(target, 100))
        assertTrue(OfflineStore.isPart(current))
        assertTrue(OfflineStore.isPart(download))
        assertFalse(OfflineStore.isPart(target))
    }
}
