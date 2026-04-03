package com.safephone.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.safephone.ui.theme.SafePhoneTheme
import org.junit.Rule
import org.junit.Test

class SafePhoneThemePaparazziTest {

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5,
    )

    @Test
    fun home_title_snapshot() {
        paparazzi.snapshot {
            SafePhoneTheme {
                Text(
                    text = "SafePhone",
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
    }
}
