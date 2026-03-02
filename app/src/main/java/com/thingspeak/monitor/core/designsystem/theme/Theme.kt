package com.thingspeak.monitor.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Design System — Motyw aplikacji ThingSpeak Monitor.
 *
 * Wykorzystuje Material 3 z oddzielonymi paletami dla Dark i Light Mode.
 * Color palette inspired by IoT / technical dashboard greens.
 */

private val DarkColorScheme = darkColorScheme(
    primary = RadiantBlue,
    secondary = NeonPurple,
    tertiary = VividMagenta,
    background = DarkBackground,
    surface = DarkSurface,
    surfaceVariant = Color(0xFF202020),
    onPrimary = Color.Black,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = DarkOnSurface,
    onSurface = DarkOnSurface,
    onSurfaceVariant = Color(0xFFB0B0B0),
    error = TsError
)

private val LightColorScheme = lightColorScheme(
    primary = DeepViolet,
    secondary = RadiantBlue,
    tertiary = VividMagenta,
    background = Color.White,
    surface = Color(0xFFF5F5F5),
    surfaceVariant = Color(0xFFEBEBEB),
    onPrimary = Color.White,
    onSecondary = Color.Black,
    onTertiary = Color.White,
    onBackground = Color.Black,
    onSurface = Color.Black,
    onSurfaceVariant = Color(0xFF404040),
    error = TsError
)

@Composable
fun ThingSpeakMonitorTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = TsTypography,
        content = content,
    )
}
