package com.example.timetablescraper.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * Maps the fixed iOS palette onto Material 3's slots.
 *
 * The app still uses plenty of Material components, and rewriting all of them would be a much
 * larger change than this one. Feeding them an iOS-shaped colour scheme means the components
 * nobody restyled — menus, dialogs, dropdowns, text fields — still land on the right greys
 * instead of Material's purple, so the app reads as one design rather than two.
 */
private fun iosColorScheme(c: IosColors): ColorScheme {
    val base = if (c.isDark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = c.accent,
        onPrimary = c.onAccent,
        primaryContainer = c.accent.copy(alpha = 0.16f),
        onPrimaryContainer = c.accent,
        secondary = c.accent,
        onSecondary = c.onAccent,
        tertiary = c.purple,
        onTertiary = c.onAccent,
        background = c.systemBackground,
        onBackground = c.label,
        surface = c.systemBackground,
        onSurface = c.label,
        surfaceVariant = c.secondarySystemBackground,
        onSurfaceVariant = c.secondaryLabel,
        surfaceContainerLowest = c.systemBackground,
        surfaceContainerLow = c.secondarySystemBackground,
        surfaceContainer = c.secondarySystemBackground,
        surfaceContainerHigh = c.tertiarySystemBackground,
        surfaceContainerHighest = c.tertiarySystemBackground,
        surfaceTint = Color.Transparent,          // iOS has no elevation tinting
        inverseSurface = c.label,
        inverseOnSurface = c.systemBackground,
        error = c.red,
        onError = c.onAccent,
        errorContainer = c.red.copy(alpha = 0.14f),
        onErrorContainer = c.red,
        outline = c.separator,
        outlineVariant = c.separator.copy(alpha = 0.5f),
        scrim = Color.Black,
    )
}

/**
 * The app theme.
 *
 * [dynamicColor] defaults to **false**, which is a deliberate change: it used to be on, so on
 * Android 12+ every screen was tinted from the user's wallpaper. That is an Android idea, and it
 * is precisely what stops the app from looking like an iOS app. Set it to true only if you want
 * the old behaviour back.
 */
@Composable
fun TimetableScraperTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val iosColors = if (darkTheme) DarkIosColors else LightIosColors
    val colorScheme = if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        iosColorScheme(iosColors)
    }

    // Status/navigation bar icons must invert with the theme, or a dark background gets dark
    // icons and looks broken. The bars themselves are transparent: `enableEdgeToEdge()` in
    // MainActivity already lets content run underneath them.
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            runCatching {
                val window = (view.context as Activity).window
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = !darkTheme
                    isAppearanceLightNavigationBars = !darkTheme
                }
            }
        }
    }

    CompositionLocalProvider(LocalIosColors provides iosColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            shapes = IosShapes,
            content = content
        )
    }
}
