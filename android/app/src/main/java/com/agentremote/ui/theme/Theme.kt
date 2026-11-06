package com.agentremote.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = AccentEmerald,
    secondary = AccentCyan,
    tertiary = AccentAmber,
    background = CanvasDark,
    surface = SurfaceDark,
    surfaceVariant = SurfaceElevated,
    onPrimary = CanvasDark,
    onSecondary = CanvasDark,
    onTertiary = CanvasDark,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    onSurfaceVariant = TextSecondary,
    outline = BorderDark
)

@Composable
fun AgentRemoteTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}
