package com.safephone.service

import android.content.Context
import android.content.pm.PackageManager
import android.Manifest
import android.provider.Settings
import com.safephone.data.DaltonizerSnapshot
import com.safephone.data.FocusPreferences
import kotlinx.coroutines.flow.first

/**
 * Toggles system accessibility color correction (display daltonizer / monochrome) via
 * [Settings.Secure]. Requires [Manifest.permission.WRITE_SECURE_SETTINGS] (typically ADB-granted).
 *
 * Mode values align with AOSP accessibility display adjustment (e.g. DisplayAdjustmentUtils).
 */
class SystemMonochromeController(
    private val context: Context,
    private val prefs: FocusPreferences,
) {
    private val cr = context.contentResolver

    fun canApply(): Boolean =
        context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) ==
            PackageManager.PERMISSION_GRANTED

    fun readCurrent(): DaltonizerSnapshot =
        DaltonizerSnapshot(
            enabled = Settings.Secure.getInt(cr, SECURE_DALTONIZER_ENABLED, 0),
            mode = Settings.Secure.getInt(cr, SECURE_DALTONIZER, DaltonizerSnapshot.MODE_DISABLED),
        )

    private fun writeSnapshot(snapshot: DaltonizerSnapshot) {
        Settings.Secure.putInt(cr, SECURE_DALTONIZER_ENABLED, snapshot.enabled)
        Settings.Secure.putInt(cr, SECURE_DALTONIZER, snapshot.mode)
    }

    private fun enableMonochrome() {
        Settings.Secure.putInt(cr, SECURE_DALTONIZER_ENABLED, 1)
        Settings.Secure.putInt(cr, SECURE_DALTONIZER, DaltonizerSnapshot.MODE_MONOCHROMACY)
    }

    /**
     * Keeps system display in sync with focus policy. Call from the enforcement loop each tick.
     */
    suspend fun sync(applyGrayscale: Boolean) {
        if (!canApply()) return
        if (!prefs.systemMonochromeAutomationEnabled.first()) {
            restoreAndClearSnapshot()
            return
        }
        try {
            if (applyGrayscale) {
                if (!prefs.hasDaltonizerSnapshot()) {
                    prefs.saveDaltonizerSnapshot(readCurrent())
                }
                enableMonochrome()
            } else {
                restoreAndClearSnapshot()
            }
        } catch (_: SecurityException) {
            prefs.clearDaltonizerSnapshot()
        }
    }

    /**
     * Restores saved daltonizer state if the app had captured a snapshot (e.g. service stopping).
     */
    suspend fun restoreIfHeld() {
        if (!canApply()) return
        try {
            restoreAndClearSnapshot()
        } catch (_: SecurityException) {
            // Keep snapshot if present so a later successful sync can restore.
        }
    }

    private suspend fun restoreAndClearSnapshot() {
        val snap = prefs.takeDaltonizerSnapshotIfPresent() ?: return
        try {
            writeSnapshot(snap)
        } catch (_: SecurityException) {
            prefs.saveDaltonizerSnapshot(snap)
        }
    }

    companion object {
        private const val SECURE_DALTONIZER_ENABLED = "accessibility_display_daltonizer_enabled"
        private const val SECURE_DALTONIZER = "accessibility_display_daltonizer"
    }
}
