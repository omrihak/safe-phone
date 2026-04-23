package com.safephone.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class BreakManagerTest {
    private val zone: ZoneId = ZoneId.of("UTC")
    private val dayStartEpochMs: Long = LocalDate.of(2026, 1, 1).atStartOfDay(zone).toInstant().toEpochMilli()

    @Test
    fun grantedBreaksByNow_unlocksEvenlyAcrossDay() {
        assertEquals(1, BreakManager.grantedBreaksByNow(maxBreaksPerDay = 8, nowEpochMs = dayStartEpochMs, zoneId = zone))
        assertEquals(
            2,
            BreakManager.grantedBreaksByNow(
                maxBreaksPerDay = 8,
                nowEpochMs = dayStartEpochMs + 3 * 60 * 60 * 1_000L,
                zoneId = zone,
            ),
        )
        assertEquals(
            8,
            BreakManager.grantedBreaksByNow(
                maxBreaksPerDay = 8,
                nowEpochMs = dayStartEpochMs + 23 * 60 * 60 * 1_000L + 59 * 60 * 1_000L,
                zoneId = zone,
            ),
        )
    }

    @Test
    fun grantedBreaksByNow_zeroDailyBreaks() {
        assertEquals(0, BreakManager.grantedBreaksByNow(maxBreaksPerDay = 0, nowEpochMs = dayStartEpochMs, zoneId = zone))
    }

    @Test
    fun nextGrantEpochMs_returnsNextSlotOrNull() {
        assertEquals(
            dayStartEpochMs + 3 * 60 * 60 * 1_000L,
            BreakManager.nextGrantEpochMs(maxBreaksPerDay = 8, nowEpochMs = dayStartEpochMs + 1, zoneId = zone),
        )
        assertNull(
            BreakManager.nextGrantEpochMs(
                maxBreaksPerDay = 8,
                nowEpochMs = dayStartEpochMs + 23 * 60 * 60 * 1_000L + 59 * 60 * 1_000L,
                zoneId = zone,
            ),
        )
    }
}
