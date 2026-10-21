package com.defnf.grid.presentation.screens.filebrowser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowsePathsTest {

    @Test
    fun `sftp goes up to the starting directory and no further`() {
        assertEquals("/home/me", BrowsePaths.parentWithinRoot("/home/me/music", "/home/me", isSmb = false))
        assertNull(BrowsePaths.parentWithinRoot("/home/me", "/home/me", isSmb = false))
    }

    @Test
    fun `a root typed with a trailing slash is the same folder`() {
        assertNull(BrowsePaths.parentWithinRoot("/home/me", "/home/me/", isSmb = false))
        assertEquals("/home/me", BrowsePaths.parentWithinRoot("/home/me/music", "/home/me/", isSmb = false))
    }

    @Test
    fun `a root ending in a space is honoured exactly`() {
        assertEquals("/data ", BrowsePaths.parentWithinRoot("/data /x", "/data ", isSmb = false))
        assertNull(BrowsePaths.parentWithinRoot("/data ", "/data ", isSmb = false))
    }

    @Test
    fun `a sibling that merely shares the root's prefix is outside it`() {
        assertNull(BrowsePaths.parentWithinRoot("/home/meow", "/home/me", isSmb = false))
    }

    @Test
    fun `without a starting directory sftp goes up to slash`() {
        assertEquals("/", BrowsePaths.parentWithinRoot("/home", "/", isSmb = false))
        assertNull(BrowsePaths.parentWithinRoot("/", "/", isSmb = false))
    }

    @Test
    fun `smb stops at the share's top level`() {
        assertEquals("\\Music", BrowsePaths.parentWithinRoot("\\Music\\Albums", "", isSmb = true))
        assertEquals("", BrowsePaths.parentWithinRoot("\\Music", "", isSmb = true))
        assertNull(BrowsePaths.parentWithinRoot("", "", isSmb = true))
    }

    @Test
    fun `the parent row shows only below the root`() {
        assertFalse(FileBrowserUiState(currentPath = "/home/me", rootPath = "/home/me", protocol = "SFTP").canNavigateUp)
        assertTrue(FileBrowserUiState(currentPath = "/home/me/a", rootPath = "/home/me", protocol = "SFTP").canNavigateUp)
    }
}
