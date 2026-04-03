package com.safephone.service

import android.app.Application
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.safephone.data.FocusPreferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BreakAlarmReceiverInstrumentedTest {

    @Test
    fun endBreakIntent_clearsBreakEnd() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val prefs = FocusPreferences(app)
        val end = System.currentTimeMillis() + 120_000L
        prefs.setBreakState(endEpochMs = end, breaksUsed = 1, dayEpochDay = 20_000L, lastBreakEnd = 0L)
        val recv = BreakAlarmReceiver()
        recv.onReceive(app, Intent(BreakManager.ACTION_END_BREAK))
        assertNull(prefs.breakEndEpochMs.first())
    }
}
