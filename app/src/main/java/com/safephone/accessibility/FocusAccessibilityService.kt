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
        // Block/grayscale overlays are our package; don't clear URL or we'd flicker off blocking.
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
            val fromId = findUrlBarHost(root, packageName)
            if (fromId != null) return fromId
            return scanTextForHost(root, maxNodes = 400)
        }

        private var nodeScanCount = 0

        private fun findUrlBarHost(root: AccessibilityNodeInfo, packageName: String): String? {
            val idHint = when {
                packageName.contains("chrome") -> "url_bar"
                packageName.contains("firefox") -> "mozac_browser_toolbar_url_view"
                packageName.contains("edge") -> "url_bar"
                else -> "url"
            }
            val nodes = mutableListOf<AccessibilityNodeInfo>()
            collectByViewId(root, idHint, nodes, limit = 8)
            for (n in nodes) {
                try {
                    val t = n.text?.toString() ?: continue
                    val h = hostFromText(t)
                    if (h != null) return h
                } finally {
                    n.recycle()
                }
            }
            return null
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

        private fun scanTextForHost(root: AccessibilityNodeInfo, maxNodes: Int): String? {
            nodeScanCount = 0
            return walk(root, maxNodes)
        }

        private fun walk(node: AccessibilityNodeInfo, maxNodes: Int): String? {
            if (nodeScanCount++ > maxNodes) return null
            val t = node.text?.toString()
            if (!t.isNullOrBlank()) {
                val h = hostFromText(t)
                if (h != null) return h
            }
            val d = node.contentDescription?.toString()
            if (!d.isNullOrBlank()) {
                val h = hostFromText(d)
                if (h != null) return h
            }
            for (i in 0 until node.childCount) {
                val c = node.getChild(i) ?: continue
                try {
                    val h = walk(c, maxNodes)
                    if (h != null) return h
                } finally {
                    c.recycle()
                }
            }
            return null
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
