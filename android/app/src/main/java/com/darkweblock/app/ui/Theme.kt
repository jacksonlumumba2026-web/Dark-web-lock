package com.darkweblock.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object Palette {
    val Ink = Color(0xFF090B10)
    val Surface = Color(0xFF131720)
    val SurfaceHigh = Color(0xFF1A1F2B)
    val Stroke = Color(0xFF252B37)
    val Text = Color(0xFFF4F5F8)
    val Muted = Color(0xFF9BA4B5)
    val Accent = Color(0xFF5989FF)
    val AccentDeep = Color(0xFF2E4FB8)
    val Good = Color(0xFF75E6AD)
    val Warn = Color(0xFFE8C89A)
    val Bad = Color(0xFFFF9AAF)
}

@Composable
fun DarkWebLockTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Palette.Accent,
            onPrimary = Color.White,
            background = Palette.Ink,
            onBackground = Palette.Text,
            surface = Palette.Surface,
            onSurface = Palette.Text,
        ),
        content = content,
    )
}
