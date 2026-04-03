package com.safephone.browser

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
}
