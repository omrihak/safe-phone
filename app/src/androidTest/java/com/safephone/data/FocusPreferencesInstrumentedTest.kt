package com.safephone.data

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FocusPreferencesInstrumentedTest {

    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test
    fun onboarding_enforcement_roundTrip() = runBlocking {
        val prefs = FocusPreferences(context)
        prefs.setOnboardingCompleted(true)
        prefs.setEnforcementEnabled(true)
        assertTrue(prefs.onboardingCompleted.first())
        assertTrue(prefs.enforcementEnabled.first())
        prefs.setOnboardingCompleted(false)
        prefs.setEnforcementEnabled(false)
        assertFalse(prefs.onboardingCompleted.first())
        assertFalse(prefs.enforcementEnabled.first())
    }

    @Test
    fun activeProfileId_and_forcedEnforce() = runBlocking {
        val prefs = FocusPreferences(context)
        prefs.setActiveProfileId(42L)
        assertEquals(42L, prefs.activeProfileId.first())
        prefs.setActiveProfileId(null)
        assertNull(prefs.activeProfileId.first())
        val until = System.currentTimeMillis() + 10_000L
        prefs.setForcedEnforceUntil(until)
        assertEquals(until, prefs.forcedEnforceUntilMs.first())
        prefs.setForcedEnforceUntil(null)
        assertNull(prefs.forcedEnforceUntilMs.first())
    }

    @Test
    fun breakState_roundTrip() = runBlocking {
        val prefs = FocusPreferences(context)
        val end = System.currentTimeMillis() + 60_000L
        prefs.setBreakState(endEpochMs = end, breaksUsed = 2, dayEpochDay = 19_000L, lastBreakEnd = 1L)
        assertEquals(end, prefs.breakEndEpochMs.first())
        assertEquals(2, prefs.breaksUsedToday.first())
        prefs.clearBreakTimer()
        assertNull(prefs.breakEndEpochMs.first())
    }
}
