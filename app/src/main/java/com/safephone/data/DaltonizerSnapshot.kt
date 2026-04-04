package com.safephone.data

/** Saved [android.provider.Settings.Secure] daltonizer state for restore after focus. */
data class DaltonizerSnapshot(
    val enabled: Int,
    val mode: Int,
) {
    companion object {
        /** AOSP disabled sentinel for [android.provider.Settings.Secure.ACCESSIBILITY_DISPLAY_DALTONIZER]. */
        const val MODE_DISABLED = -1

        /** AOSP monochromacy mode. */
        const val MODE_MONOCHROMACY = 0
    }
}
