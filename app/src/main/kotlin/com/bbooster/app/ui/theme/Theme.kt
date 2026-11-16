package com.bbooster.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary = Color(0xFF5CA8FF),
    onPrimary = Color(0xFF002B52),
    primaryContainer = Color(0xFF1A3F73),
    onPrimaryContainer = Color(0xFFD3E6FF),
    secondary = Color(0xFFB69CFF),
    onSecondary = Color(0xFF2C1A63),
    background = Color(0xFF0B0E17),
    onBackground = Color(0xFFE6E9F2),
    surface = Color(0xFF121726),
    onSurface = Color(0xFFE6E9F2),
    surfaceVariant = Color(0xFF1B2133),
    onSurfaceVariant = Color(0xFFB4BAC9),
    error = StatusBad,
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF1C5FD0),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD9E5FF),
    onPrimaryContainer = Color(0xFF001B3F),
    secondary = Color(0xFF6A3FD6),
    onSecondary = Color.White,
    background = Color(0xFFF7F8FC),
    onBackground = Color(0xFF12151F),
    surface = Color.White,
    onSurface = Color(0xFF12151F),
    surfaceVariant = Color(0xFFE6E9F2),
    onSurfaceVariant = Color(0xFF454B5C),
    error = Color(0xFFBA1A1A),
)

/** Follows the system light/dark setting. Dynamic colour and a manual toggle come in phase 4. */
@Composable
fun BBoosterTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
