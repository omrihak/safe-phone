package com.safephone.ui.overlay

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.safephone.ui.theme.SafePhoneTheme

/**
 * Fallback “calm screen” when system daltonizer is unavailable: dark neutral scrim (not true pixel desaturation of underlying UI).
 */
class GrayscaleOverlayActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent.getBooleanExtra(EXTRA_DISMISS, false)) {
            finish()
            return
        }
        @Suppress("DEPRECATION")
        window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
        }
        window.attributes = window.attributes.apply {
            dimAmount = 0.35f
            flags = flags or WindowManager.LayoutParams.FLAG_DIM_BEHIND
        }
        current = this
        setContent {
            SafePhoneTheme {
                CalmOverlay()
            }
        }
    }

    override fun onDestroy() {
        if (current === this) current = null
        super.onDestroy()
    }

    companion object {
        private const val EXTRA_DISMISS = "dismiss"
        private var current: GrayscaleOverlayActivity? = null

        fun show(context: Context) {
            if (current != null) return
            val i = Intent(context, GrayscaleOverlayActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            context.startActivity(i)
        }

        fun dismiss(context: Context) {
            current?.finish()
            current = null
        }

        fun isShowing(): Boolean = current != null
    }
}

@Composable
private fun CalmOverlay() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xCC1A1A1A)),
    )
}
