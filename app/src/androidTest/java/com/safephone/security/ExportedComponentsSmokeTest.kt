package com.safephone.security

import android.app.Application
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.safephone.service.BootReceiver
import com.safephone.widget.FocusWidgetReceiver
import com.safephone.widget.GrayscaleWidgetReceiver
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExportedComponentsSmokeTest {

    @Test
    fun bootReceiver_unknownAction_noCrash() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        BootReceiver().onReceive(app, Intent("com.safephone.UNKNOWN_TEST_ACTION"))
    }

    @Test
    fun focusWidgetReceiver_unknownAction_delegatesSafely() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        FocusWidgetReceiver().onReceive(app, Intent(Intent.ACTION_VIEW))
    }

    @Test
    fun grayscaleWidgetReceiver_unknownAction_delegatesSafely() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        GrayscaleWidgetReceiver().onReceive(app, Intent(Intent.ACTION_VIEW))
    }
}
