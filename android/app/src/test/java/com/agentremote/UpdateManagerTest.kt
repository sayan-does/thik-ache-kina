package com.agentremote

import com.agentremote.data.UpdateManager
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateManagerTest {

    @Test
    fun testIsNewerVersion_newerPatch() {
        assertTrue(UpdateManager.isNewerVersion("v1.0.4", "1.0.3"))
    }

    @Test
    fun testIsNewerVersion_newerMinor() {
        assertTrue(UpdateManager.isNewerVersion("v1.1.0", "1.0.9"))
    }

    @Test
    fun testIsNewerVersion_newerMajor() {
        assertTrue(UpdateManager.isNewerVersion("v2.0.0", "1.9.9"))
    }

    @Test
    fun testIsNewerVersion_sameVersion() {
        assertFalse(UpdateManager.isNewerVersion("v1.0.3", "1.0.3"))
    }

    @Test
    fun testIsNewerVersion_olderVersion() {
        assertFalse(UpdateManager.isNewerVersion("v1.0.2", "1.0.3"))
    }

    @Test
    fun testIsNewerVersion_multiDigitSegments() {
        assertTrue(UpdateManager.isNewerVersion("v1.10.0", "1.9.0"))
        assertFalse(UpdateManager.isNewerVersion("v1.9.0", "1.10.0"))
    }
}
