package com.defnf.grid.data.provider

import com.defnf.grid.domain.model.Protocol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DocumentPathsTest {

    @Test
    fun `sftp roots open at the starting directory`() {
        assertEquals("/home/me", DocumentPaths.rootOf(Protocol.SFTP, "/home/me"))
        assertEquals("/home/me", DocumentPaths.rootOf(Protocol.SFTP, "/home/me/"))
        assertEquals("/", DocumentPaths.rootOf(Protocol.SFTP, null))
        assertEquals("/", DocumentPaths.rootOf(Protocol.SFTP, "  "))
    }

    @Test
    fun `smb roots open at the share's top level`() {
        assertEquals("", DocumentPaths.rootOf(Protocol.SMB, "/ignored"))
    }

    @Test
    fun `smb ids from before 1_2_2 map onto the browser's paths`() {
        assertEquals("", DocumentPaths.normalize("/", Protocol.SMB))
        assertEquals("\\music\\a.mp3", DocumentPaths.normalize("/music/a.mp3", Protocol.SMB))
        assertEquals("\\music\\a.mp3", DocumentPaths.normalize("\\music\\a.mp3", Protocol.SMB))
        assertEquals("\\music", DocumentPaths.normalize("music\\", Protocol.SMB))
    }

    @Test
    fun `parents stop at the top of the server`() {
        assertEquals("/home", DocumentPaths.parentOf("/home/me", Protocol.SFTP))
        assertEquals("/", DocumentPaths.parentOf("/home", Protocol.SFTP))
        assertNull(DocumentPaths.parentOf("/", Protocol.SFTP))
        assertNull(DocumentPaths.parentOf("relative", Protocol.SFTP))
        assertEquals("relative", DocumentPaths.parentOf("relative/a.txt", Protocol.SFTP))

        assertEquals("\\music", DocumentPaths.parentOf("\\music\\a.mp3", Protocol.SMB))
        assertEquals("", DocumentPaths.parentOf("\\music", Protocol.SMB))
        assertNull(DocumentPaths.parentOf("", Protocol.SMB))
    }

    @Test
    fun `names are the last segment`() {
        assertEquals("a.mp3", DocumentPaths.nameOf("/music/a.mp3", Protocol.SFTP))
        assertEquals("a.mp3", DocumentPaths.nameOf("\\music\\a.mp3", Protocol.SMB))
        assertEquals("a\\b.txt", DocumentPaths.nameOf("/x/a\\b.txt", Protocol.SFTP))
    }

    @Test
    fun `within covers the folder and everything below it`() {
        assertTrue(DocumentPaths.isWithin("/home/me", "/home/me", Protocol.SFTP))
        assertTrue(DocumentPaths.isWithin("/home/me/a/b", "/home/me", Protocol.SFTP))
        assertTrue(DocumentPaths.isWithin("/anything", "/", Protocol.SFTP))
        assertFalse(DocumentPaths.isWithin("/home/meow", "/home/me", Protocol.SFTP))
        assertFalse(DocumentPaths.isWithin("/home", "/home/me", Protocol.SFTP))

        assertTrue(DocumentPaths.isWithin("\\Music\\a.mp3", "\\music", Protocol.SMB))
        assertTrue(DocumentPaths.isWithin("\\anything", "", Protocol.SMB))
        assertFalse(DocumentPaths.isWithin("\\musicals", "\\music", Protocol.SMB))
    }
}
