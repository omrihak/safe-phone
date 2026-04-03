package com.safephone.browser

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.safephone.BuildConfig
import com.safephone.accessibility.FocusAccessibilityService

/**
 * Opens a clean tab in the blocked browser so returning later does not restore the blocked URL.
 * Tries the public focus landing URL (`BuildConfig.BLOCK_LANDING_URL`) first, then `about:newtab`, then `about:blank`.
 */
object BrowserNeutralTabLauncher {

    internal fun orderedBlockExitUriSpecs(landingUrl: String): List<String> {
        val trimmed = landingUrl.trim()
        return buildList {
            if (trimmed.isNotEmpty()) add(trimmed)
            add("about:newtab")
            add("about:blank")
        }
    }

    fun uriCandidates(context: Context, @Suppress("UNUSED_PARAMETER") browserPackage: String): List<String> =
        orderedBlockExitUriSpecs(BuildConfig.BLOCK_LANDING_URL)

    /**
     * Clears the accessibility URL snapshot immediately (no enforcement grace period).
     * Starts [ACTION_VIEW] in [browserPackage]; then the caller should send the user home.
     */
    fun openNeutralTabThenClearSnapshot(context: Context, browserPackage: String) {
        FocusAccessibilityService.beginBrowserNavigationReset(pendingMs = 0L)
        val pm = context.packageManager
        for (spec in uriCandidates(context, browserPackage)) {
            val uri = Uri.parse(spec)
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                setPackage(browserPackage)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (intent.resolveActivity(pm) == null) continue
            try {
                context.startActivity(intent)
                break
            } catch (_: Exception) {
                continue
            }
        }
    }
}
