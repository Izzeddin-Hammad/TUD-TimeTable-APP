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
    /** Filled star, e.g. the pinned group. */
    val yellow: Color,
    val purple: Color,
    val teal: Color,
    val isDark: Boolean,
) {
    /** Label colour for content placed on [accent], [red] and friends. */
    val onAccent: Color get() = Color.White

    /** [accent] at the strength iOS uses behind selected or tinted content. */
    val accentWash: Color get() = accent.copy(alpha = 0.16f)

    /** [red] at the strength used behind error rows and banners. */
    val redWash: Color get() = red.copy(alpha = 0.14f)

    /**
     * [separator] for the lighter divider iOS uses inside a grouped surface. Note that this
     * *replaces* the separator's own alpha rather than multiplying it.
     */
    val separatorSoft: Color get() = separator.copy(alpha = 0.5f)
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
    yellow = Color(0xFFFFCC00),
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
    yellow = Color(0xFFFFD60A),
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

// ══════════════════════════════════════════════════════════════════════════════
// Selectable themes
// ══════════════════════════════════════════════════════════════════════════════

/**
 * Assembles a palette from its anchors, deriving the greys.
 *
 * The derived greys — secondary/tertiary labels, separators, fills — are computed from the ink
 * colour rather than hand-picked per theme, so every palette is internally consistent and none of
 * them drifts. This is the one place in the app where a raw hex value is allowed;
 * `IosDesignLayerTest` enforces that.
 */
private fun palette(
    background: Color,
    surface: Color,
    surfaceRaised: Color,
    ink: Color,
    accent: Color,
    green: Color,
    red: Color,
    orange: Color,
    yellow: Color,
    purple: Color,
    teal: Color,
    isDark: Boolean,
): IosColors = IosColors(
    systemBackground = background,
    secondarySystemBackground = surface,
    tertiarySystemBackground = surfaceRaised,
    groupedBackground = background,
    label = ink,
    secondaryLabel = ink.copy(alpha = 0.66f),
    tertiaryLabel = ink.copy(alpha = 0.38f),
    separator = ink.copy(alpha = 0.14f),
    fill = ink.copy(alpha = if (isDark) 0.16f else 0.09f),
    fillStrong = ink.copy(alpha = if (isDark) 0.24f else 0.15f),
    accent = accent,
    green = green,
    red = red,
    orange = orange,
    yellow = yellow,
    purple = purple,
    teal = teal,
    isDark = isDark,
)

/**
 * A "cozy" palette: warm and low-contrast.
 *
 * The background is an off-white (or a warm deep grey in dark) rather than pure white/black, and
 * the accents are muted rather than saturated.
 */
private fun cozy(
    background: Long,
    surface: Long,
    surfaceRaised: Long,
    ink: Long,
    accent: Long,
    green: Long,
    red: Long,
    orange: Long,
    yellow: Long,
    purple: Long,
    teal: Long,
    isDark: Boolean,
): IosColors = palette(
    background = Color(background),
    surface = Color(surface),
    surfaceRaised = Color(surfaceRaised),
    ink = Color(ink),
    accent = Color(accent),
    green = Color(green),
    red = Color(red),
    orange = Color(orange),
    yellow = Color(yellow),
    purple = Color(purple),
    teal = Color(teal),
    isDark = isDark,
)

// ── Cozy Latte — warm cream and caramel ─────────────────────────────────────
internal val LightLatteIosColors = cozy(
    background = 0xFFFBF5EC, surface = 0xFFF2E7D8, surfaceRaised = 0xFFFFFBF4, ink = 0xFF3B2F26,
    accent = 0xFFB07B4F, green = 0xFF6E8F5A, red = 0xFFC05C4C, orange = 0xFFD08A3E,
    yellow = 0xFFE0B23C, purple = 0xFF8E6B93, teal = 0xFF5F8F86, isDark = false,
)
internal val DarkLatteIosColors = cozy(
    background = 0xFF1F1813, surface = 0xFF2A211A, surfaceRaised = 0xFF35291F, ink = 0xFFF1E4D3,
    accent = 0xFFD9A97A, green = 0xFF8FAE79, red = 0xFFD97C6A, orange = 0xFFE0A257,
    yellow = 0xFFE6C462, purple = 0xFFB393BC, teal = 0xFF82B3A9, isDark = true,
)

// ── Cozy Sage — soft sage and warm white ────────────────────────────────────
internal val LightSageIosColors = cozy(
    background = 0xFFF4F6EF, surface = 0xFFE7ECDE, surfaceRaised = 0xFFFBFDF7, ink = 0xFF2F3A2E,
    accent = 0xFF6C8A5B, green = 0xFF6C8A5B, red = 0xFFC0635A, orange = 0xFFD29452,
    yellow = 0xFFDDBE62, purple = 0xFF8A7BA8, teal = 0xFF5E9A8F, isDark = false,
)
internal val DarkSageIosColors = cozy(
    background = 0xFF171C16, surface = 0xFF212821, surfaceRaised = 0xFF2C342B, ink = 0xFFE5EADF,
    accent = 0xFF9DBE88, green = 0xFF9DBE88, red = 0xFFD98A80, orange = 0xFFE0A96A,
    yellow = 0xFFE2C878, purple = 0xFFAB9CC4, teal = 0xFF7FB8AC, isDark = true,
)

// ── Cozy Dusk — muted plum and lavender ─────────────────────────────────────
internal val LightDuskIosColors = cozy(
    background = 0xFFF7F3F8, surface = 0xFFECE5EF, surfaceRaised = 0xFFFDFAFD, ink = 0xFF352E3B,
    accent = 0xFF8E6E9E, green = 0xFF6F8F72, red = 0xFFC25F6B, orange = 0xFFD08C5A,
    yellow = 0xFFDDBE62, purple = 0xFF8E6E9E, teal = 0xFF618F96, isDark = false,
)
internal val DarkDuskIosColors = cozy(
    background = 0xFF1B1620, surface = 0xFF251E2B, surfaceRaised = 0xFF302736, ink = 0xFFEDE4F0,
    accent = 0xFFBFA0D0, green = 0xFF8FAE92, red = 0xFFD9838E, orange = 0xFFE0A87A,
    yellow = 0xFFE2C878, purple = 0xFFBFA0D0, teal = 0xFF83AEB4, isDark = true,
)

// ── Cozy Peach — peach and terracotta ───────────────────────────────────────
internal val LightPeachIosColors = cozy(
    background = 0xFFFDF3EE, surface = 0xFFF8E5DB, surfaceRaised = 0xFFFFFBF8, ink = 0xFF3E2C26,
    accent = 0xFFD0764F, green = 0xFF7E9B6A, red = 0xFFC9553F, orange = 0xFFD9884A,
    yellow = 0xFFE3B45E, purple = 0xFFA8799E, teal = 0xFF5F9A94, isDark = false,
)
internal val DarkPeachIosColors = cozy(
    background = 0xFF211713, surface = 0xFF2C1F1A, surfaceRaised = 0xFF372821, ink = 0xFFF4E4DA,
    accent = 0xFFE29B76, green = 0xFF9BB884, red = 0xFFDB7A64, orange = 0xFFE3A469,
    yellow = 0xFFE6C377, purple = 0xFFC097B6, teal = 0xFF82B3AD, isDark = true,
)

// ── Cozy Mist — calm blue-grey ──────────────────────────────────────────────
internal val LightMistIosColors = cozy(
    background = 0xFFF1F5F7, surface = 0xFFE3EBEF, surfaceRaised = 0xFFFAFCFD, ink = 0xFF2C3840,
    accent = 0xFF5C86A0, green = 0xFF6E9A82, red = 0xFFC2635C, orange = 0xFFD09154,
    yellow = 0xFFDDC26A, purple = 0xFF8A7BA8, teal = 0xFF5A9AA6, isDark = false,
)
internal val DarkMistIosColors = cozy(
    background = 0xFF151B1F, surface = 0xFF1E262B, surfaceRaised = 0xFF283136, ink = 0xFFE2EBEF,
    accent = 0xFF86AFC6, green = 0xFF8FB8A0, red = 0xFFD98A82, orange = 0xFFE0A76C,
    yellow = 0xFFE2C87E, purple = 0xFFAB9CC4, teal = 0xFF7FB8C2, isDark = true,
)

/**
 * Builds a palette from a single hue — the student's "custom colouring".
 *
 * The structure is the cozy one; only the hue varies, plus how much of it the surfaces carry.
 * The category colours are offsets from the chosen hue, so tutorials, lectures and labs stay
 * distinguishable from the accent instead of colliding with it; [IosColors.red] stays a warm red
 * because it means "error" and must not be recoloured.
 */
internal fun customColors(hue: Float, saturation: Float, dark: Boolean): IosColors {
    val s = saturation.coerceIn(0f, 1f)
    val h = ((hue % 360f) + 360f) % 360f
    fun shift(degrees: Float) = (((h + degrees) % 360f) + 360f) % 360f

    // How much of the hue the surfaces carry. The floor keeps a hint of colour even at zero
    // "vividness"; the ceiling is about where an off-white stops being a page and becomes a pastel
    // fill — at lightness ~0.95 the saturation is what does the work, not the lightness, which is
    // why this is not proportional to `s` alone.
    val surfaceTint = 0.25f + s * 0.45f

    return if (!dark) {
        palette(
            background = Color.hsl(h, surfaceTint, 0.955f),
            surface = Color.hsl(h, surfaceTint * 1.05f, 0.915f),
            surfaceRaised = Color.hsl(h, surfaceTint * 0.65f, 0.985f),
            ink = Color.hsl(h, 0.18f + s * 0.22f, 0.17f),
            accent = Color.hsl(h, 0.42f + s * 0.22f, 0.44f),
            green = Color.hsl(shift(115f), 0.34f + s * 0.18f, 0.40f),
            red = Color.hsl(6f, 0.50f, 0.46f),
            orange = Color.hsl(shift(38f), 0.52f, 0.48f),
            yellow = Color.hsl(shift(55f), 0.52f, 0.50f),
            purple = Color.hsl(shift(255f), 0.34f + s * 0.16f, 0.46f),
            teal = Color.hsl(shift(180f), 0.36f, 0.42f),
            isDark = false,
        )
    } else {
        palette(
            background = Color.hsl(h, surfaceTint * 0.85f, 0.105f),
            surface = Color.hsl(h, surfaceTint, 0.145f),
            surfaceRaised = Color.hsl(h, surfaceTint * 1.05f, 0.20f),
            ink = Color.hsl(h, 0.10f + s * 0.14f, 0.93f),
            accent = Color.hsl(h, 0.38f + s * 0.24f, 0.70f),
            green = Color.hsl(shift(115f), 0.30f + s * 0.16f, 0.66f),
            red = Color.hsl(6f, 0.52f, 0.64f),
            orange = Color.hsl(shift(38f), 0.48f, 0.68f),
            yellow = Color.hsl(shift(55f), 0.48f, 0.70f),
            purple = Color.hsl(shift(255f), 0.34f + s * 0.16f, 0.72f),
            teal = Color.hsl(shift(180f), 0.32f, 0.64f),
            isDark = true,
        )
    }
}

/**
 * A colour theme the student can pick from Settings.
 *
 * Each theme is a light *and* a dark palette, so the app keeps following the system's light/dark
 * setting; only the hue is the student's choice. [id] is what is persisted, so the values must not
 * change once shipped — a stored id that no longer matches anything falls back to [DEFAULT].
 */
enum class AppTheme(
    val id: String,
    val label: String,
    val description: String,
) {
    LATTE("latte", "Cozy Latte", "Warm cream and caramel"),
    SAGE("sage", "Cozy Sage", "Soft sage and warm white"),
    DUSK("dusk", "Cozy Dusk", "Muted plum and lavender"),
    PEACH("peach", "Cozy Peach", "Peach and terracotta"),
    MIST("mist", "Cozy Mist", "Calm blue-grey"),
    CUSTOM("custom", "Custom", "Pick your own colour"),
    ;

    /**
     * The palette for this theme. [customHue]/[customSaturation] are only consulted by [CUSTOM];
     * the presets ignore them, which keeps the call sites uniform.
     */
    fun colors(
        dark: Boolean,
        customHue: Float = DEFAULT_CUSTOM_HUE,
        customSaturation: Float = DEFAULT_CUSTOM_SATURATION,
    ): IosColors = when (this) {
        LATTE -> if (dark) DarkLatteIosColors else LightLatteIosColors
        SAGE -> if (dark) DarkSageIosColors else LightSageIosColors
        DUSK -> if (dark) DarkDuskIosColors else LightDuskIosColors
        PEACH -> if (dark) DarkPeachIosColors else LightPeachIosColors
        MIST -> if (dark) DarkMistIosColors else LightMistIosColors
        CUSTOM -> customColors(customHue, customSaturation, dark)
    }

    companion object {
        /** Used when nothing is stored, or the stored id is unknown. */
        val DEFAULT = LATTE

        /** Where the custom hue slider starts before the student touches it. */
        const val DEFAULT_CUSTOM_HUE = 28f
        const val DEFAULT_CUSTOM_SATURATION = 0.55f

        fun fromId(id: String?): AppTheme = entries.firstOrNull { it.id == id } ?: DEFAULT
    }
}
