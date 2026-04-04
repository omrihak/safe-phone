package com.safephone.widget

import android.app.Application
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.safephone.data.AppDatabase
import com.safephone.data.BreakPolicyEntity
import com.safephone.data.FocusPreferences
import com.safephone.service.FocusEnforcementService
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class FocusWidgetReceiverInstrumentedTest {

    @After
    fun stopService() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        FocusEnforcementService.stop(app)
    }

    @Test
    fun widgetStartBreak_setsBreakEndWhenAvailable() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val prefs = FocusPreferences(app)
        val db = AppDatabase.getInstance(app)
        db.breakPolicyDao().upsert(BreakPolicyEntity(maxBreaksPerDay = 5, breakDurationMinutes = 10, minGapBetweenBreaksMinutes = 30))
        prefs.setBreakState(
            endEpochMs = null,
            breaksUsed = 0,
            dayEpochDay = LocalDate.now().toEpochDay(),
            lastBreakEnd = 0L,
        )
        val recv = FocusWidgetReceiver()
        recv.onReceive(app, Intent(FocusWidgetReceiver.ACTION_START_BREAK))
        assertNotNull(prefs.breakEndEpochMs.first())
    }
}
