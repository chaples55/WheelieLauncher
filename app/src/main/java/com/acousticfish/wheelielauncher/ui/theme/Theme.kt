package com.acousticfish.wheelielauncher.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val WheelieColors = darkColorScheme(
    primary = Color(0xFFE8E6E3),
    onPrimary = Color(0xFF121212),
    secondary = Color(0xFFB0AEA8),
    background = Color(0xFF0D0D0F),
    surface = Color(0xFF16161A),
    onBackground = Color(0xFFE8E6E3),
    onSurface = Color(0xFFE8E6E3),
)

@Composable
fun WheelieTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = WheelieColors,
        content = content,
    )
}
