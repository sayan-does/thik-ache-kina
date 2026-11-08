package com.agentremote.ui.theme

import android.app.Activity
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// True Obsidian & Tactical Engineering Palette (Hard-coded to override system light mode)
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
    outline = BorderDark,
    surfaceTint = Color.Transparent
)

@Composable
fun AgentRemoteTheme(content: @Composable () -> Unit) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                // Hard-override system status bar and navigation bar to true obsidian
                window.statusBarColor = CanvasDark.toArgb()
                window.navigationBarColor = CanvasDark.toArgb()
                val insetsController = WindowCompat.getInsetsController(window, view)
                insetsController.isAppearanceLightStatusBars = false
                insetsController.isAppearanceLightNavigationBars = false
            }
        }
    }

    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography
    ) {
        CompositionLocalProvider(
            LocalTextStyle provides Typography.bodyMedium,
            LocalContentColor provides TextPrimary,
            content = content
        )
    }
}
