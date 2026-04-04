package com.safephone.ui.overlay

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.safephone.BuildConfig
import com.safephone.R
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
        val reason = intent.getStringExtra(EXTRA_REASON) ?: ""
        val landingUrl = BuildConfig.BLOCK_LANDING_URL.trim()
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
                        if (landingUrl.isNotEmpty()) {
                            OutlinedButton(
                                onClick = {
                                    val uri = BrowserNeutralTabLauncher.focusLandingUri(landingQueryParams())
                                        ?: Uri.parse(landingUrl)
                                    startActivity(
                                        Intent(Intent.ACTION_VIEW, uri)
                                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                    )
                                },
                                modifier = Modifier.padding(top = 12.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = Color.White.copy(alpha = 0.92f),
                                ),
                            ) {
                                Text(stringResource(R.string.block_overlay_open_focus_page))
                            }
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
         * @param browserPackage When set (foreground was a browser), "Go to home" opens the focus landing
         * URL (then about:newtab / about:blank) in that browser so reopening does not land on the blocked page.
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
