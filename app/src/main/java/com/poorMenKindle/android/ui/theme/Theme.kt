package com.poorMenKindle.android.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val LightColorScheme = lightColorScheme(
    primary = Blue600,
    onPrimary = Color.White,
    primaryContainer = Blue100,
    onPrimaryContainer = Blue900,
    secondary = Violet600,
    onSecondary = Color.White,
    secondaryContainer = Violet100,
    onSecondaryContainer = Violet900,
    tertiary = Emerald500,
    onTertiary = Color.White,
    tertiaryContainer = Emerald100,
    onTertiaryContainer = Emerald800,
    error = Red600,
    onError = Color.White,
    background = Gray50,
    onBackground = Gray800,
    surface = Color.White,
    onSurface = Gray800,
    surfaceVariant = Gray100,
    onSurfaceVariant = Gray500,
    outline = Gray400
)

private val DarkColorScheme = darkColorScheme(
    primary = Blue400,
    onPrimary = Blue900,
    primaryContainer = Blue900,
    onPrimaryContainer = Blue100,
    secondary = Violet400,
    onSecondary = Violet900,
    secondaryContainer = Violet900,
    onSecondaryContainer = Violet100,
    tertiary = Emerald400,
    onTertiary = Emerald800,
    tertiaryContainer = Emerald800,
    onTertiaryContainer = Emerald100,
    error = Red400,
    onError = Gray900,
    background = Gray900,
    onBackground = Gray200,
    surface = Gray800,
    onSurface = Gray200,
    surfaceVariant = Gray700,
    onSurfaceVariant = Gray400,
    outline = Gray500
)

@Composable
fun PoorMensKindleTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Off by default so the brand palette applies; flip on for Material You wallpaper colors
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

/** Soft vertical gradient used behind the main app screens; adapts to dark mode. */
@Composable
fun appBackgroundBrush(): Brush = if (isSystemInDarkTheme()) {
    Brush.verticalGradient(colors = listOf(Color(0xFF0B1220), Gray900, Gray800))
} else {
    Brush.verticalGradient(colors = listOf(Blue50, Gray100, Color.White))
}

/** Warm peach-to-red gradient behind the admin dashboard; adapts to dark mode. */
@Composable
fun adminBackgroundBrush(): Brush = if (isSystemInDarkTheme()) {
    Brush.verticalGradient(colors = listOf(Color(0xFF4A2A1A), Color(0xFF7F1D1D)))
} else {
    Brush.verticalGradient(colors = listOf(Color(0xFFFCD6B5), Color(0xFFFC6969)))
}

/** Vibrant branded gradient behind the login screen; adapts to dark mode. */
@Composable
fun loginBackgroundBrush(): Brush = if (isSystemInDarkTheme()) {
    Brush.verticalGradient(
        colors = listOf(
            Color(0xFF11304A),
            Color(0xFF123B5C),
            Color(0xFF14506E),
            Color(0xFF1D4ED8)
        )
    )
} else {
    Brush.verticalGradient(
        colors = listOf(
            Color(0xFF99E2FF),
            Color(0xFF8EE1F3),
            Color(0xFF5BCCEE),
            Color(0xFF5B97F5)
        )
    )
}
