package com.example.timetablescraper.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * An iOS-flavoured palette.
 *
 * iOS does not derive its colours from the wallpaper: it uses a fixed set of *semantic* colours
 * (systemBackground, label, separator, …) that resolve differently in light and dark. Android's
 * dynamic colour does the opposite — it tints the whole app from the user's wallpaper — which is
 * why it is switched off in [TimetableScraperTheme].
 *
 * The values below follow Apple's published system colours, including their use of alpha for
 * text: iOS secondary labels are the label colour at 60% opacity rather than a fixed grey, so
 * they sit correctly on any surface.
 */
@Immutable
data class IosColors(
    /** Behind the main content. */
    val systemBackground: Color,
    /** Cards, grouped list rows, secondary surfaces. */
    val secondarySystemBackground: Color,
    /** Nested surfaces: switches off-track, tertiary fills. */
    val tertiarySystemBackground: Color,
    /** Behind inset grouped lists (Settings, results). */
    val groupedBackground: Color,
    /** Primary text. */
    val label: Color,
    /** Supporting text, subtitles, section footers. */
    val secondaryLabel: Color,
    /** Placeholders and disabled text. */
    val tertiaryLabel: Color,
    /** Hairline dividers between list rows. */
    val separator: Color,
    /** Fills: search field background, pressed states, segmented control track. */
    val fill: Color,
    /** A slightly stronger fill, for the selected side of a segmented control. */
    val fillStrong: Color,
    /** The single tint colour, as iOS uses for interactive elements. */
    val accent: Color,
    val green: Color,
    val red: Color,
    val orange: Color,
    val purple: Color,
    val teal: Color,
    val isDark: Boolean,
) {
    /** Label colour for content placed on [accent], [red] and friends. */
    val onAccent: Color get() = Color.White
}

private fun labelAlpha(base: Color, alpha: Float) = base.copy(alpha = alpha)

internal val LightIosColors = IosColors(
    systemBackground = Color(0xFFFFFFFF),
    secondarySystemBackground = Color(0xFFF2F2F7),
    tertiarySystemBackground = Color(0xFFFFFFFF),
    groupedBackground = Color(0xFFF2F2F7),
    label = Color(0xFF000000),
    secondaryLabel = labelAlpha(Color(0xFF3C3C43), 0.60f),
    tertiaryLabel = labelAlpha(Color(0xFF3C3C43), 0.30f),
    separator = labelAlpha(Color(0xFF3C3C43), 0.29f),
    fill = labelAlpha(Color(0xFF787880), 0.12f),
    fillStrong = labelAlpha(Color(0xFF787880), 0.20f),
    accent = Color(0xFF007AFF),
    green = Color(0xFF34C759),
    red = Color(0xFFFF3B30),
    orange = Color(0xFFFF9500),
    purple = Color(0xFFAF52DE),
    teal = Color(0xFF30B0C7),
    isDark = false,
)

internal val DarkIosColors = IosColors(
    systemBackground = Color(0xFF000000),
    secondarySystemBackground = Color(0xFF1C1C1E),
    tertiarySystemBackground = Color(0xFF2C2C2E),
    groupedBackground = Color(0xFF000000),
    label = Color(0xFFFFFFFF),
    secondaryLabel = labelAlpha(Color(0xFFEBEBF5), 0.60f),
    tertiaryLabel = labelAlpha(Color(0xFFEBEBF5), 0.30f),
    separator = labelAlpha(Color(0xFF545458), 0.65f),
    fill = labelAlpha(Color(0xFF767680), 0.24f),
    fillStrong = labelAlpha(Color(0xFF767680), 0.36f),
    accent = Color(0xFF0A84FF),
    green = Color(0xFF30D158),
    red = Color(0xFFFF453A),
    orange = Color(0xFFFF9F0A),
    purple = Color(0xFFBF5AF2),
    teal = Color(0xFF40C8E0),
    isDark = true,
)

/** Makes the palette available to components: `IosTheme.colors.systemBackground`. */
val LocalIosColors = staticCompositionLocalOf { LightIosColors }

object IosTheme {
    val colors: IosColors
        @androidx.compose.runtime.Composable
        @androidx.compose.runtime.ReadOnlyComposable
        get() = LocalIosColors.current
}
