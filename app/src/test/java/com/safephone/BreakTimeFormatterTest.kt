package com.safephone

import org.junit.Assert.assertEquals
import org.junit.Test

class BreakTimeFormatterTest {
    @Test
    fun formatMmSs_zero() {
        assertEquals("0:00", BreakTimeFormatter.formatMmSs(0))
    }

    @Test
    fun formatMmSs_pads_seconds() {
        assertEquals("1:05", BreakTimeFormatter.formatMmSs(65_000))
    }

    @Test
    fun formatMmSs_negative_coerces_to_zero() {
        assertEquals("0:00", BreakTimeFormatter.formatMmSs(-1))
    }
}
