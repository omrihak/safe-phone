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

    /**
     * Which neutral targets to try first when replacing the blocked browser tab.
     * [LandingFirst] keeps the hosted page for explicit “go home” flows.
     * [LocalNeutralFirst] avoids leaving Chrome on the marketing landing if focus ordering races.
     */
    enum class BlockExitUriOrder {
        LandingFirst,
        LocalNeutralFirst,
    }

    /** Query keys: `reason`, `type` (app | web | browser_lock), optional `host`, `pkg`. */
    internal fun landingUriWithQuery(landingUrl: String, query: Map<String, String>): String? {
        val trimmed = landingUrl.trim()
        if (trimmed.isEmpty()) return null
        val builder = Uri.parse(trimmed).buildUpon().clearQuery()
        query.forEach { (k, v) -> builder.appendQueryParameter(k, v) }
        return builder.build().toString()
    }

    internal fun orderedBlockExitUriSpecs(
        landingUrl: String,
        query: Map<String, String> = emptyMap(),
        order: BlockExitUriOrder = BlockExitUriOrder.LandingFirst,
    ): List<String> {
        val landing = landingUriWithQuery(landingUrl, query)
        return buildList {
            when (order) {
                BlockExitUriOrder.LandingFirst -> {
                    landing?.let { add(it) }
                    add("about:newtab")
                    add("about:blank")
                }
                BlockExitUriOrder.LocalNeutralFirst -> {
                    add("about:newtab")
                    add("about:blank")
                    landing?.let { add(it) }
                }
            }
        }
    }

    fun uriCandidates(
        @Suppress("UNUSED_PARAMETER") context: Context,
        @Suppress("UNUSED_PARAMETER") browserPackage: String,
        landingQuery: Map<String, String> = emptyMap(),
        order: BlockExitUriOrder = BlockExitUriOrder.LandingFirst,
    ): List<String> = orderedBlockExitUriSpecs(BuildConfig.BLOCK_LANDING_URL, landingQuery, order)

    /** Full HTTPS landing URI with optional block context (for in-app browser or sharing). */
    fun focusLandingUri(landingQuery: Map<String, String> = emptyMap()): Uri? {
        val spec = landingUriWithQuery(BuildConfig.BLOCK_LANDING_URL, landingQuery) ?: return null
        return Uri.parse(spec)
    }

    /**
     * Clears the accessibility URL snapshot immediately (no enforcement grace period).
     * Starts [ACTION_VIEW] in [browserPackage]; then the caller should send the user home.
     */
    fun openNeutralTabThenClearSnapshot(
        context: Context,
        browserPackage: String,
        landingQuery: Map<String, String> = emptyMap(),
        uriOrder: BlockExitUriOrder = BlockExitUriOrder.LandingFirst,
    ) {
        FocusAccessibilityService.beginBrowserNavigationReset(pendingMs = 0L)
        val pm = context.packageManager
        for (spec in uriCandidates(context, browserPackage, landingQuery, uriOrder)) {
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
