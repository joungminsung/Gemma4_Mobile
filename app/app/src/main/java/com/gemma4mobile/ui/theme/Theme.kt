package com.gemma4mobile.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF8AB4F8),
    secondary = Color(0xFF81C995),
    tertiary = Color(0xFFF28B82),
    background = Color(0xFF1A1A2E),
    surface = Color(0xFF16213E),
    onBackground = Color(0xFFE8EAED),
    onSurface = Color(0xFFE8EAED),
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF1A73E8),
    secondary = Color(0xFF188038),
    tertiary = Color(0xFFC5221F),
    background = Color(0xFFF8F9FA),
    surface = Color(0xFFFFFFFF),
    onBackground = Color(0xFF202124),
    onSurface = Color(0xFF202124),
)

@Composable
fun Gemma4MobileTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(),
        content = content,
    )
}
