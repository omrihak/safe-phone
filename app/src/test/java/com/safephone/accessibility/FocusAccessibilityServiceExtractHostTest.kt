package com.safephone.accessibility

import android.view.accessibility.AccessibilityNodeInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowAccessibilityNodeInfo

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class FocusAccessibilityServiceExtractHostTest {

    @Test
    fun urlBarPresentButUnparseable_doesNotPickHostFromTabListElsewhere() {
        val root = AccessibilityNodeInfo.obtain()
        val urlBar = AccessibilityNodeInfo.obtain()
        urlBar.viewIdResourceName = "com.android.chrome:id/url_bar"
        urlBar.text = "Search tabs"
        val tabRow = AccessibilityNodeInfo.obtain()
        tabRow.text = "https://blocked.example.com"
        Shadow.extract<ShadowAccessibilityNodeInfo>(root).addChild(urlBar)
        Shadow.extract<ShadowAccessibilityNodeInfo>(root).addChild(tabRow)
        try {
            assertNull(FocusAccessibilityService.extractHost(root, "com.android.chrome"))
        } finally {
            root.recycle()
            urlBar.recycle()
            tabRow.recycle()
        }
    }

    @Test
    fun noUrlBarField_fallsBackToTreeScan() {
        val root = AccessibilityNodeInfo.obtain()
        val body = AccessibilityNodeInfo.obtain()
        body.text = "https://page.example.org/path"
        Shadow.extract<ShadowAccessibilityNodeInfo>(root).addChild(body)
        try {
            assertEquals(
                "page.example.org",
                FocusAccessibilityService.extractHost(root, "com.android.chrome"),
            )
        } finally {
            root.recycle()
            body.recycle()
        }
    }

    @Test
    fun urlBarHasHost_prefersOmniboxOverOtherHostsInTree() {
        val root = AccessibilityNodeInfo.obtain()
        val urlBar = AccessibilityNodeInfo.obtain()
        urlBar.viewIdResourceName = "com.android.chrome:id/url_bar"
        urlBar.text = "https://good.example/"
        val noise = AccessibilityNodeInfo.obtain()
        noise.text = "https://other.example"
        Shadow.extract<ShadowAccessibilityNodeInfo>(root).addChild(urlBar)
        Shadow.extract<ShadowAccessibilityNodeInfo>(root).addChild(noise)
        try {
            assertEquals(
                "good.example",
                FocusAccessibilityService.extractHost(root, "com.android.chrome"),
            )
        } finally {
            root.recycle()
            urlBar.recycle()
            noise.recycle()
        }
    }
}
