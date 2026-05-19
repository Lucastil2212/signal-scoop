package com.signalsoop.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary = ScoopBlue,
    onPrimary = ScoopBlack,
    secondary = ScoopGreen,
    onSecondary = ScoopBlack,
    background = ScoopBlack,
    onBackground = ScoopWhite,
    surface = ScoopSurface,
    onSurface = ScoopWhite,
    surfaceVariant = ScoopSurfaceHigh,
    onSurfaceVariant = ScoopMuted,
    outline = Color(0xFF2E3340),
)

@Composable
fun SignalScoopTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColors,
        typography = ScoopTypography,
        content = content,
    )
}
