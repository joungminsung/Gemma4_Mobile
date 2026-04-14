package com.gemma4mobile.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// ChatGPT-style custom colors
data class GemmaColors(
    val userBubble: Color,
    val aiBackground: Color,
    val sidebarBackground: Color,
    val userIcon: Color,
    val aiIcon: Color,
    val inputBackground: Color,
    val inputBorder: Color,
    val placeholder: Color,
)

val LocalGemmaColors = staticCompositionLocalOf {
    GemmaColors(
        userBubble = Color.Unspecified,
        aiBackground = Color.Unspecified,
        sidebarBackground = Color.Unspecified,
        userIcon = Color.Unspecified,
        aiIcon = Color.Unspecified,
        inputBackground = Color.Unspecified,
        inputBorder = Color.Unspecified,
        placeholder = Color.Unspecified,
    )
}

private val DarkGemmaColors = GemmaColors(
    userBubble = Color(0xFF2f2f2f),
    aiBackground = Color(0xFF343541),
    sidebarBackground = Color(0xFF171717),
    userIcon = Color(0xFF5436DA),
    aiIcon = Color(0xFF19c37d),
    inputBackground = Color(0xFF2f2f2f),
    inputBorder = Color(0xFF40414f),
    placeholder = Color(0xFF8e8ea0),
)

private val LightGemmaColors = GemmaColors(
    userBubble = Color(0xFFf7f7f8),
    aiBackground = Color(0xFFf7f7f8),
    sidebarBackground = Color(0xFFf9f9f9),
    userIcon = Color(0xFF5436DA),
    aiIcon = Color(0xFF19c37d),
    inputBackground = Color(0xFFffffff),
    inputBorder = Color(0xFFd9d9e3),
    placeholder = Color(0xFF8e8ea0),
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF19c37d),
    secondary = Color(0xFF5436DA),
    background = Color(0xFF212121),
    surface = Color(0xFF2f2f2f),
    surfaceVariant = Color(0xFF343541),
    onBackground = Color(0xFFececec),
    onSurface = Color(0xFFececec),
    onSurfaceVariant = Color(0xFFd1d5db),
    outline = Color(0xFF40414f),
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF19c37d),
    secondary = Color(0xFF5436DA),
    background = Color(0xFFffffff),
    surface = Color(0xFFf7f7f8),
    surfaceVariant = Color(0xFFf7f7f8),
    onBackground = Color(0xFF202124),
    onSurface = Color(0xFF202124),
    onSurfaceVariant = Color(0xFF374151),
    outline = Color(0xFFd9d9e3),
)

@Composable
fun Gemma4MobileTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val gemmaColors = if (darkTheme) DarkGemmaColors else LightGemmaColors

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            @Suppress("DEPRECATION")
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    androidx.compose.runtime.CompositionLocalProvider(
        LocalGemmaColors provides gemmaColors,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography(),
            content = content,
        )
    }
}

// Convenience accessor
object GemmaTheme {
    val gemmaColors: GemmaColors
        @Composable
        get() = LocalGemmaColors.current
}
