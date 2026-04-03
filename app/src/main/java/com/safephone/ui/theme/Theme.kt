package com.safephone.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Forest = Color(0xFF0F4C3A)
private val ForestLight = Color(0xFF1B6B52)
private val Sand = Color(0xFFD4A574)
private val SandMuted = Color(0xFFC49A6C)
private val Mist = Color(0xFFF4F6F5)
private val Slate = Color(0xFF1C2524)
private val SlateSurface = Color(0xFF252F2D)
private val OutlineLight = Color(0xFF8FA9A2)
private val OutlineDark = Color(0xFF5C726C)

private val LightColors = lightColorScheme(
    primary = Forest,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB8E0D4),
    onPrimaryContainer = Color(0xFF002019),
    secondary = Sand,
    onSecondary = Color(0xFF2B1F0A),
    secondaryContainer = Color(0xFFFFE8CC),
    onSecondaryContainer = Color(0xFF2B1F0A),
    tertiary = ForestLight,
    onTertiary = Color.White,
    background = Mist,
    onBackground = Slate,
    surface = Color.White,
    onSurface = Slate,
    surfaceContainerLow = Color(0xFFECF1EF),
    surfaceContainerHigh = Color(0xFFE2EAE7),
    surfaceContainerHighest = Color(0xFFD8E3DF),
    onSurfaceVariant = Color(0xFF3D4F4A),
    outline = OutlineLight,
    outlineVariant = Color(0xFFBFCFC9),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF7FD4BC),
    onPrimary = Color(0xFF00382A),
    primaryContainer = Forest,
    onPrimaryContainer = Color(0xFFB8E0D4),
    secondary = SandMuted,
    onSecondary = Color(0xFF2B1F0A),
    secondaryContainer = Color(0xFF5C4030),
    onSecondaryContainer = Color(0xFFFFE8CC),
    tertiary = Color(0xFF5EB89A),
    onTertiary = Color(0xFF00382A),
    background = Slate,
    onBackground = Color(0xFFE2EAE7),
    surface = SlateSurface,
    onSurface = Color(0xFFE2EAE7),
    surfaceContainerLow = Color(0xFF1A2321),
    surfaceContainerHigh = Color(0xFF2A3533),
    surfaceContainerHighest = Color(0xFF35403E),
    onSurfaceVariant = Color(0xFFB0C4BE),
    outline = OutlineDark,
    outlineVariant = Color(0xFF3D4F4A),
)

private val AppTypography = Typography()

@Composable
fun SafePhoneTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        typography = AppTypography,
        content = content,
    )
}
