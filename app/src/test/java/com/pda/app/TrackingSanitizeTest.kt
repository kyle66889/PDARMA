package com.pda.app

import com.pda.app.ui.dockreceiving.sanitizeTracking
import org.junit.Assert.assertEquals
import org.junit.Test

class TrackingSanitizeTest {

    @Test
    fun `accepts real tracking numbers`() {
        assertEquals("9400136110139348703814", sanitizeTracking("9400136110139348703814")) // USPS
        assertEquals("1Z999AA10123456784", sanitizeTracking("1Z999AA10123456784"))         // UPS
        assertEquals("1Z999AA10123456784", sanitizeTracking("  1Z999AA10123456784  "))     // trims
    }

    @Test
    fun `rejects empty and garbage values`() {
        assertEquals("", sanitizeTracking(null))
        assertEquals("", sanitizeTracking(""))
        assertEquals("", sanitizeTracking("N/A"))
        assertEquals("", sanitizeTracking("未找到"))
        assertEquals("", sanitizeTracking("tracking number not found")) // prose, too few digits
        assertEquals("", sanitizeTracking("1234"))                       // too short
        assertEquals("", sanitizeTracking("ABCDEFGHIJ"))                 // no digits
    }
}
