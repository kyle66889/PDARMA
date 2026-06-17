package com.pda.app

import com.pda.app.ui.dockreceiving.CARRIERS
import com.pda.app.ui.dockreceiving.CONDITIONS
import com.pda.app.ui.dockreceiving.normalizeCarrier
import org.junit.Assert.assertEquals
import org.junit.Test

class DockReceivingConstantsTest {

    @Test
    fun `carriers and conditions match web constants`() {
        assertEquals(listOf("UPS", "FedEx", "USPS", "DHL", "Amazon", "OnTrac", "Other"), CARRIERS)
        assertEquals(listOf("Good", "Fair", "Damaged", "Unknown"), CONDITIONS)
    }

    @Test
    fun `normalizeCarrier maps case-insensitively to canonical spelling`() {
        assertEquals("FedEx", normalizeCarrier("fedex"))
        assertEquals("FedEx", normalizeCarrier("FEDEX"))
        assertEquals("UPS", normalizeCarrier("ups"))
    }

    @Test
    fun `normalizeCarrier returns raw value when no match`() {
        assertEquals("LaserShip", normalizeCarrier("LaserShip"))
    }

    @Test
    fun `normalizeCarrier returns empty string for null or blank`() {
        assertEquals("", normalizeCarrier(null))
        assertEquals("", normalizeCarrier("  "))
    }
}
