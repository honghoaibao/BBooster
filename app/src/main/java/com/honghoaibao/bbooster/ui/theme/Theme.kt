package com.honghoaibao.bbooster.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val BBoosterDarkColorScheme = darkColorScheme(
    primary = ElectricCyan,
    secondary = Emerald,
    tertiary = Amber,
    background = Obsidian,
    surface = DarkSlate,
    onPrimary = Obsidian,
    onSecondary = Obsidian,
    onTertiary = Obsidian,
    onBackground = SoftWhite,
    onSurface = SoftWhite
)

@Composable
fun BBoosterTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = BBoosterDarkColorScheme,
        content = content
    )
}
