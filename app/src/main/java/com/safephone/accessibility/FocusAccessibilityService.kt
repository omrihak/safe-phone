package com.safephone.accessibility

import android.accessibilityservice.AccessibilityService
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.safephone.BuildConfig
import com.safephone.policy.PolicyEngine
import java.util.regex.Pattern

class FocusAccessibilityService : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val pkg = event.packageName?.toString() ?: return
        // Block overlay is our package; don't clear URL or we'd flicker off blocking.
        if (pkg == BuildConfig.APPLICATION_ID) return
        if (!PolicyEngine.isBrowserPackage(pkg)) {
            lastBrowserHost = null
            return
        }
        if (isBrowserNavigationPending()) return
        val root = rootInActiveWindow ?: return
        try {
            val host = extractHost(root, pkg)
            lastBrowserHost = host
            if (!host.isNullOrBlank()) {
                navigationPendingUntilElapsed = 0L
            }
        } finally {
            root.recycle()
        }
    }

    override fun onInterrupt() {}

    companion object {
        @Volatile
        var lastBrowserHost: String? = null

        @Volatile
        private var navigationPendingUntilElapsed: Long = 0L

        /** Clears the URL snapshot. When `pendingMs > 0`, host stays ignored briefly after reset. */
        fun beginBrowserNavigationReset(pendingMs: Long = 0L) {
            lastBrowserHost = null
            navigationPendingUntilElapsed = SystemClock.elapsedRealtime() + pendingMs
        }

        fun isBrowserNavigationPending(): Boolean =
            SystemClock.elapsedRealtime() < navigationPendingUntilElapsed

        private val urlPattern = Pattern.compile(
            "(https?://)([^/\\s]+)",
            Pattern.CASE_INSENSITIVE,
        )

        fun extractHost(root: AccessibilityNodeInfo, packageName: String): String? {
            val urlBar = lookupUrlBarHost(root, packageName)
            if (urlBar.host != null) return urlBar.host
            // Tab search / switcher UIs often show "Search tabs" (or similar) in the real URL bar while
            // listing other tabs' URLs in the tree. Scanning the tree would treat a background tab as active.
            if (urlBar.foundToolbarUrlField) return null
            // Do not walk page content: embedded links/iframes would masquerade as the top-level URL.
            return null
        }

        private data class UrlBarLookup(val host: String?, val foundToolbarUrlField: Boolean)

        private fun lookupUrlBarHost(root: AccessibilityNodeInfo, packageName: String): UrlBarLookup {
            val idHint = when {
                packageName.contains("chrome") -> "url_bar"
                packageName.contains("firefox") -> "mozac_browser_toolbar_url_view"
                packageName.contains("edge") -> "url_bar"
                else -> "url"
            }
            val nodes = mutableListOf<AccessibilityNodeInfo>()
            collectByViewId(root, idHint, nodes, limit = 8)
            if (nodes.isEmpty()) return UrlBarLookup(null, false)
            for (n in nodes) {
                try {
                    val t = n.text?.toString() ?: continue
                    val h = hostFromText(t)
                    if (h != null) return UrlBarLookup(h, true)
                } finally {
                    n.recycle()
                }
            }
            return UrlBarLookup(null, true)
        }

        private fun collectByViewId(node: AccessibilityNodeInfo, idSubstr: String, out: MutableList<AccessibilityNodeInfo>, limit: Int) {
            if (out.size >= limit) return
            val id = node.viewIdResourceName
            if (id != null && id.contains(idSubstr, ignoreCase = true)) {
                out.add(AccessibilityNodeInfo.obtain(node))
            }
            for (i in 0 until node.childCount) {
                val c = node.getChild(i) ?: continue
                try {
                    collectByViewId(c, idSubstr, out, limit)
                } finally {
                    c.recycle()
                }
            }
        }

        private fun hostFromText(text: String): String? {
            val m = urlPattern.matcher(text.trim())
            if (m.find()) {
                return m.group(2)?.lowercase()
            }
            val t = text.trim().lowercase()
            if (t.contains(".") && !t.contains(" ") && t.length in 4..200) {
                val stripped = t.removePrefix("http://").removePrefix("https://").split("/").firstOrNull()
                if (stripped != null && stripped.contains(".")) return stripped
            }
            return null
        }
    }
}
