package com.safephone.widget

import android.app.Application
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.safephone.data.FocusPreferences
import com.safephone.service.FocusEnforcementService
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FocusWidgetReceiverInstrumentedTest {

    @After
    fun stopService() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        FocusEnforcementService.stop(app)
    }

    @Test
    fun widgetToggle_flipsEnforcement() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val prefs = FocusPreferences(app)
        prefs.setEnforcementEnabled(false)
        val recv = FocusWidgetReceiver()
        recv.onReceive(app, Intent(FocusWidgetReceiver.ACTION_TOGGLE))
        assertEquals(true, prefs.enforcementEnabled.first())
        recv.onReceive(app, Intent(FocusWidgetReceiver.ACTION_TOGGLE))
        assertEquals(false, prefs.enforcementEnabled.first())
    }
}
