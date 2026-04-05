package com.safephone.ui.overlay

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.safephone.browser.BrowserNeutralTabLauncher
import com.safephone.ui.theme.SafePhoneTheme

class BlockOverlayActivity : ComponentActivity() {

    private fun landingQueryParams(): Map<String, String> {
        val map = linkedMapOf<String, String>()
        intent.getStringExtra(EXTRA_REASON)?.trim()?.takeIf { it.isNotEmpty() }?.let { map["reason"] = it }
        intent.getStringExtra(EXTRA_BLOCK_TYPE)?.trim()?.takeIf { it.isNotEmpty() }?.let { map["type"] = it }
        intent.getStringExtra(EXTRA_HOST)?.trim()?.takeIf { it.isNotEmpty() }?.let { map["host"] = it }
        intent.getStringExtra(EXTRA_BLOCKED_PACKAGE)?.trim()?.takeIf { it.isNotEmpty() }?.let { map["pkg"] = it }
        return map
    }

    /** Web blocks only: load a neutral URL in the blocked browser, then return this overlay to the foreground. */
    private fun replaceBlockedBrowserTabWithLanding() {
        val browserPkg = intent.getStringExtra(EXTRA_BROWSER_PACKAGE)?.trim().orEmpty()
        if (browserPkg.isEmpty()) return
        // Prefer about:* first so if Chrome wins the focus race the user does not stay on the hosted landing page.
        BrowserNeutralTabLauncher.openNeutralTabThenClearSnapshot(
            applicationContext,
            browserPkg,
            landingQueryParams(),
            BrowserNeutralTabLauncher.BlockExitUriOrder.LocalNeutralFirst,
        )
        // ACTION_VIEW in Chrome often resumes after a single post; stagger REORDER_TO_FRONT so the app block UI wins.
        scheduleBringOverlayToFront()
    }

    private fun scheduleBringOverlayToFront() {
        val handler = Handler(Looper.getMainLooper())
        fun reopen() {
            if (isFinishing) return
            startActivity(
                Intent(this, BlockOverlayActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                    intent.extras?.let { putExtras(it) }
                },
            )
        }
        window.decorView.post { reopen() }
        handler.postDelayed({ reopen() }, 60L)
        handler.postDelayed({ reopen() }, 220L)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    private fun leaveToHome() {
        val browserPkg = intent.getStringExtra(EXTRA_BROWSER_PACKAGE)
        val query = landingQueryParams()
        if (!browserPkg.isNullOrBlank()) {
            BrowserNeutralTabLauncher.openNeutralTabThenClearSnapshot(applicationContext, browserPkg, query)
        }
        startActivity(
            Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent.getBooleanExtra(EXTRA_DISMISS, false)) {
            finish()
            return
        }
        @Suppress("DEPRECATION")
        window.addFlags(
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        current = this
        if (savedInstanceState == null) {
            replaceBlockedBrowserTabWithLanding()
        }
        val reason = intent.getStringExtra(EXTRA_REASON) ?: ""
        setContent {
            BackHandler { leaveToHome() }
            SafePhoneTheme {
                val scrim = Color(0xFF0A1210).copy(alpha = 0.97f)
                val accent = MaterialTheme.colorScheme.primary
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = scrim,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(28.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = "Focus mode",
                            style = MaterialTheme.typography.headlineMedium,
                            color = Color.White,
                        )
                        Text(
                            text = reason,
                            style = MaterialTheme.typography.titleMedium,
                            color = accent,
                            modifier = Modifier.padding(top = 14.dp),
                        )
                        Text(
                            text = "This app or site is blocked by your SafePhone rules.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color.White.copy(alpha = 0.78f),
                            modifier = Modifier.padding(top = 20.dp),
                        )
                        Button(
                            onClick = { leaveToHome() },
                            modifier = Modifier.padding(top = 36.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = accent,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                            ),
                        ) {
                            Text("Go to Home")
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        if (current === this) current = null
        super.onDestroy()
    }

    companion object {
        private const val EXTRA_REASON = "reason"
        private const val EXTRA_DISMISS = "dismiss"
        private const val EXTRA_BROWSER_PACKAGE = "browser_package"
        private const val EXTRA_BLOCK_TYPE = "block_type"
        private const val EXTRA_HOST = "blocked_host"
        private const val EXTRA_BLOCKED_PACKAGE = "blocked_package"
        private var current: BlockOverlayActivity? = null

        /**
         * @param browserPackage When set (foreground was a browser), the overlay replaces the tab with
         * about:newtab / about:blank first (then hosted landing as fallback), then brings this activity back
         * to the foreground so the user stays on the block screen. "Go to home" uses landing-first order before HOME.
         * @param blockType Landing query `type`: `app`, `web`, or `browser_lock`.
         */
        fun show(
            context: Context,
            reason: String,
            browserPackage: String? = null,
            blockType: String = "",
            blockedHost: String? = null,
            blockedPackage: String? = null,
        ) {
            if (current != null) return
            val i = Intent(context, BlockOverlayActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra(EXTRA_REASON, reason)
                if (!browserPackage.isNullOrBlank()) {
                    putExtra(EXTRA_BROWSER_PACKAGE, browserPackage)
                }
                if (blockType.isNotBlank()) putExtra(EXTRA_BLOCK_TYPE, blockType)
                if (!blockedHost.isNullOrBlank()) putExtra(EXTRA_HOST, blockedHost)
                if (!blockedPackage.isNullOrBlank()) putExtra(EXTRA_BLOCKED_PACKAGE, blockedPackage)
            }
            context.startActivity(i)
        }

        fun dismiss(context: Context) {
            current?.finish()
            current = null
        }

        /** True while this activity is alive (usage stats would report Safe Phone as foreground). */
        fun isShowing(): Boolean = current != null
    }
}
