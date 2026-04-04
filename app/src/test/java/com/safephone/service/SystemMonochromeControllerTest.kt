package com.safephone.service

import android.app.Application
import android.Manifest
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import com.safephone.data.DaltonizerSnapshot
import com.safephone.data.FocusPreferences
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class SystemMonochromeControllerTest {

    @Test
    fun sync_with_permission_enables_monochrome_then_restores_prior_state() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        Shadows.shadowOf(app).grantPermissions(Manifest.permission.WRITE_SECURE_SETTINGS)
        val prefs = FocusPreferences(app)
        prefs.clearDaltonizerSnapshot()

        val cr = app.contentResolver
        Settings.Secure.putInt(cr, SECURE_DALTONIZER_ENABLED, 0)
        Settings.Secure.putInt(cr, SECURE_DALTONIZER, 12)

        val controller = SystemMonochromeController(app, prefs)
        controller.sync(true)
        assertEquals(1, Settings.Secure.getInt(cr, SECURE_DALTONIZER_ENABLED, -99))
        assertEquals(DaltonizerSnapshot.MODE_MONOCHROMACY, Settings.Secure.getInt(cr, SECURE_DALTONIZER, -99))
        assertTrue(prefs.hasDaltonizerSnapshot())

        controller.sync(false)
        assertEquals(0, Settings.Secure.getInt(cr, SECURE_DALTONIZER_ENABLED, -99))
        assertEquals(12, Settings.Secure.getInt(cr, SECURE_DALTONIZER, -99))
        assertFalse(prefs.hasDaltonizerSnapshot())
    }

    @Test
    fun sync_without_permission_does_not_change_settings() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val prefs = FocusPreferences(app)
        prefs.clearDaltonizerSnapshot()

        val cr = app.contentResolver
        Settings.Secure.putInt(cr, SECURE_DALTONIZER_ENABLED, 0)
        Settings.Secure.putInt(cr, SECURE_DALTONIZER, DaltonizerSnapshot.MODE_DISABLED)

        val controller = SystemMonochromeController(app, prefs)
        controller.sync(true)
        assertEquals(0, Settings.Secure.getInt(cr, SECURE_DALTONIZER_ENABLED, -99))
        assertEquals(DaltonizerSnapshot.MODE_DISABLED, Settings.Secure.getInt(cr, SECURE_DALTONIZER, -99))
    }

    private companion object {
        private const val SECURE_DALTONIZER_ENABLED = "accessibility_display_daltonizer_enabled"
        private const val SECURE_DALTONIZER = "accessibility_display_daltonizer"
    }
}
