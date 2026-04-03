package com.safephone.service

import android.content.ContentResolver
import android.provider.Settings
import android.util.Log

class GrayscaleController(private val contentResolver: ContentResolver) {

    fun applySystemGrayscale(enabled: Boolean): Boolean {
        return try {
            // String keys work across API levels; Settings.Secure constants are API 33+ only.
            val enabledKey = "accessibility_display_daltonizer_enabled"
            val daltonizerKey = "accessibility_display_daltonizer"
            if (enabled) {
                Settings.Secure.putInt(contentResolver, enabledKey, 1)
                Settings.Secure.putInt(contentResolver, daltonizerKey, 0)
            } else {
                Settings.Secure.putInt(contentResolver, enabledKey, 0)
            }
            true
        } catch (e: SecurityException) {
            Log.w(TAG, "WRITE_SECURE_SETTINGS not granted; use adb pm grant", e)
            false
        }
    }

    companion object {
        private const val TAG = "GrayscaleController"
    }
}
