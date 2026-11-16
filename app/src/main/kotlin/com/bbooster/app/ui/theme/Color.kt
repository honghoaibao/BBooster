package com.bbooster.app.ui.theme

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Accent: electric blue -> violet. Contrast ratios are audited in phase 4.
val AccentBlue = Color(0xFF3D8BFF)
val AccentViolet = Color(0xFF8B5CFF)

val AccentGradient: Brush = Brush.linearGradient(
    colors = listOf(AccentBlue, AccentViolet),
    start = Offset.Zero,
    end = Offset.Infinite,
)

// Status colours are always paired with a text label (never colour alone).
val StatusOk = Color(0xFF3DDC84)
val StatusWarn = Color(0xFFFFB74D)
val StatusBad = Color(0xFFFF6B6B)
