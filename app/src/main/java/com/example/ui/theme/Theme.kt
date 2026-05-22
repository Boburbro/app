package com.example.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val AppDarkColorScheme = darkColorScheme(
    primary = PrimaryOrange,
    secondary = SuccessGreen,
    tertiary = GoldAmber,
    background = SlateDarkBackground,
    surface = SlateDarkSurface,
    onPrimary = TextPrimary,
    onSecondary = TextPrimary,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    surfaceVariant = SlateDarkBorder,
    outline = DarkGreyDivider
)

// Standard light scheme matching the ambient tone
private val AppLightColorScheme = lightColorScheme(
    primary = PrimaryOrange,
    secondary = SuccessGreen,
    tertiary = ElectricBlue,
    background = TextPrimary,
    surface = androidx.compose.ui.graphics.Color.White,
    onBackground = SlateDarkBackground,
    onSurface = SlateDarkBackground,
    surfaceVariant = Color(0xFFE2E8F0uL),
    outline = Color(0xFFCBD5E1uL)
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true, // Force dark minimal productivity theme by default
    dynamicColor: Boolean = false, // Use our gorgeous custom colors instead of random overlays
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> AppDarkColorScheme
        else -> AppLightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            window.navigationBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

// Inline fallback import just in case
typealias Color = androidx.compose.ui.graphics.Color
