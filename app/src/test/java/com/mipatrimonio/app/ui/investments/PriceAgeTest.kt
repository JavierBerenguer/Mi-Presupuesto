package com.mipatrimonio.app.ui.investments

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PriceAgeTest {
    @Test fun `formatea horas y no marca antiguo a los tres dias exactos`() {
        val now = 10L * 24 * 60 * 60 * 1_000
        assertEquals(PriceAgeUnit.HOURS, priceAge(now - 3_600_000, now).unit)
        assertFalse(priceAge(now - 3L * 24 * 60 * 60 * 1_000, now).old)
    }

    @Test fun `marca antiguo despues de tres dias`() {
        val day = 24L * 60 * 60 * 1_000
        assertTrue(priceAge(0, 3 * day + 1).old)
        assertEquals(3, priceAge(0, 3 * day + 1).amount)
    }
}
