package com.defnf.grid.presentation.screens.connections

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ConnectionFieldWarningsTest {

    @Test
    fun `values without outer spaces get no warning`() {
        assertNull(outerSpaceWarning(""))
        assertNull(outerSpaceWarning("/home/my files"))
    }

    @Test
    fun `leading and trailing spaces are each called out`() {
        assertEquals("Ends with a space. Double-check that's intended.", outerSpaceWarning("/data "))
        assertEquals("Starts with a space. Double-check that's intended.", outerSpaceWarning(" nas.local"))
        assertEquals("Starts and ends with a space. Double-check that's intended.", outerSpaceWarning(" me "))
    }
}
