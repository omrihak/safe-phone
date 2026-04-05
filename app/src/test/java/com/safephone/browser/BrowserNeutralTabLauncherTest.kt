package com.safephone.browser

import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.safephone.BuildConfig
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BrowserNeutralTabLauncherTest {

    @Test
    fun ordered_specs_put_landing_first_then_about_uris() {
        val list = BrowserNeutralTabLauncher.orderedBlockExitUriSpecs("https://example.com/focus/")
        assertEquals(
            listOf("https://example.com/focus/", "about:newtab", "about:blank"),
            list,
        )
    }

    @Test
    fun ordered_specs_local_neutral_first_then_landing() {
        val list = BrowserNeutralTabLauncher.orderedBlockExitUriSpecs(
            "https://example.com/focus/",
            order = BrowserNeutralTabLauncher.BlockExitUriOrder.LocalNeutralFirst,
        )
        assertEquals(
            listOf("about:newtab", "about:blank", "https://example.com/focus/"),
            list,
        )
    }

    @Test
    fun ordered_specs_trim_landing() {
        val list = BrowserNeutralTabLauncher.orderedBlockExitUriSpecs("  https://x.test  ")
        assertEquals(
            listOf("https://x.test", "about:newtab", "about:blank"),
            list,
        )
    }

    @Test
    fun ordered_specs_blank_landing_skips_https() {
        val list = BrowserNeutralTabLauncher.orderedBlockExitUriSpecs("   ")
        assertEquals(listOf("about:newtab", "about:blank"), list)
    }

    @Test
    fun uri_candidates_matches_build_config_order() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val fromBuild = BrowserNeutralTabLauncher.orderedBlockExitUriSpecs(BuildConfig.BLOCK_LANDING_URL)
        assertEquals(fromBuild, BrowserNeutralTabLauncher.uriCandidates(ctx, "com.android.chrome"))
    }

    @Test
    fun ordered_specs_append_encoded_query_on_landing() {
        val list = BrowserNeutralTabLauncher.orderedBlockExitUriSpecs(
            "https://example.com/focus/",
            mapOf("reason" to "Blocked domain", "type" to "web", "host" to "news.example"),
        )
        val u = Uri.parse(list.first())
        assertEquals("https", u.scheme)
        assertEquals("example.com", u.host)
        assertEquals("/focus/", u.path)
        assertEquals("Blocked domain", u.getQueryParameter("reason"))
        assertEquals("web", u.getQueryParameter("type"))
        assertEquals("news.example", u.getQueryParameter("host"))
        assertEquals("about:newtab", list[1])
        assertEquals("about:blank", list[2])
    }

    @Test
    fun local_neutral_first_still_appends_query_on_trailing_landing() {
        val q = mapOf("reason" to "Blocked domain", "type" to "web")
        val list = BrowserNeutralTabLauncher.orderedBlockExitUriSpecs(
            "https://example.com/focus/",
            q,
            BrowserNeutralTabLauncher.BlockExitUriOrder.LocalNeutralFirst,
        )
        assertEquals("about:newtab", list[0])
        assertEquals("about:blank", list[1])
        val u = Uri.parse(list[2])
        assertEquals("Blocked domain", u.getQueryParameter("reason"))
        assertEquals("web", u.getQueryParameter("type"))
    }
}
