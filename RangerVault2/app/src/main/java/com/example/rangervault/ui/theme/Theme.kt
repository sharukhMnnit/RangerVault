package com.example.rangervault.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// --- 1. DEFINE YOUR CUSTOM COLORS ---
val RangerGold = Color(0xFFFFD700)
val RangerBlack = Color(0xFF000000)
val RangerDarkGray = Color(0xFF121212)
val RangerRed = Color(0xFFD50000) // For errors/denied

// --- 2. FORCE DARK THEME COLOR SCHEME ---
private val DarkColorScheme = darkColorScheme(
    primary = RangerGold,
    onPrimary = Color.Black,
    secondary = RangerRed,
    background = RangerBlack,
    surface = RangerDarkGray,
    onSurface = Color.White
)

@Composable
fun RangerVaultTheme(
    // We remove the 'darkTheme' boolean because we ALWAYS want dark mode
    content: @Composable () -> Unit
) {
    val colorScheme = DarkColorScheme
    val view = LocalView.current

    // This makes the Status Bar (top of phone) black to match the app
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = {
            // We wrap everything in a Surface to ensure the background is always Black
            Surface(color = colorScheme.background) {
                content()
            }
        }
    )
}