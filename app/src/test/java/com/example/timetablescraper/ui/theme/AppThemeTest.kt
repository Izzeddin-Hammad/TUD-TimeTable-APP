package com.example.timetablescraper.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the theme catalog.
 *
 * The ids are persisted in preferences (see `SyncPreferences.getThemeId`), so renaming one would
 * silently reset every student's choice back to the default — these tests pin them. They also
 * check that each theme really is a distinct light/dark pair: a copy-paste slip would otherwise
 * ship two "different" themes that render identically.
 *
 * `assertTrue` is used for the colour comparisons because `Color` is an inline value class, which
 * trips JUnit's overload resolution on `assertEquals`/`assertNotEquals`.
 */
class AppThemeTest {

    @Test
    fun `an unknown or missing id falls back to the default`() {
        assertEquals(AppTheme.LATTE, AppTheme.DEFAULT)
        assertEquals(AppTheme.DEFAULT, AppTheme.fromId(null))
        assertEquals(AppTheme.DEFAULT, AppTheme.fromId(""))
        assertEquals(AppTheme.DEFAULT, AppTheme.fromId("no-such-theme"))
        // "classic" was removed in v1.29; a student who had it stored must land on the default
        // rather than on a blank palette.
        assertEquals(AppTheme.DEFAULT, AppTheme.fromId("classic"))
    }

    @Test
    fun `every theme round-trips through its persisted id`() {
        for (theme in AppTheme.entries) {
            assertEquals(theme, AppTheme.fromId(theme.id))
        }
        // Ids are persisted keys: a duplicate would make one of the two themes unreachable.
        val ids = AppTheme.entries.map { it.id }
        assertEquals(ids.size, ids.distinct().size)
    }

    @Test
    fun `every theme has a distinct light and dark palette`() {
        for (theme in AppTheme.entries) {
            val light = theme.colors(dark = false)
            val dark = theme.colors(dark = true)
            assertTrue("${theme.id}: light palette is marked dark", !light.isDark)
            assertTrue("${theme.id}: dark palette is not marked dark", dark.isDark)
            // If the two were equal the app would ignore the system light/dark setting.
            assertTrue("${theme.id}: light and dark palettes are identical", light != dark)
        }
    }

    @Test
    fun `the presets are visually distinct from one another`() {
        // Custom is derived from the sliders and shares nothing with the presets, so it is covered
        // by `the custom theme really follows its hue` instead.
        val byAccent = AppTheme.entries
            .filter { it != AppTheme.CUSTOM }
            .map { it.id to it.colors(dark = false).accent }
            .groupBy { it.second }
            .filterValues { it.size > 1 }
        assertTrue("two preset themes share an accent colour: $byAccent", byAccent.isEmpty())
    }

    @Test
    fun `a cozy theme is warm and low-contrast, not clinical black on white`() {
        // The point of the feature: every theme, including the default Custom palette.
        for (theme in AppTheme.entries) {
            val light = theme.colors(dark = false)
            val dark = theme.colors(dark = true)
            assertTrue("${theme.id}: light background is pure white", light.systemBackground != Color.White)
            assertTrue("${theme.id}: light ink is pure black", light.label != Color.Black)
            assertTrue("${theme.id}: dark background is pure black", dark.systemBackground != Color.Black)
        }
    }

    @Test
    fun `the custom theme really follows its hue`() {
        val warm = AppTheme.CUSTOM.colors(dark = false, customHue = 20f, customSaturation = 0.6f)
        val cool = AppTheme.CUSTOM.colors(dark = false, customHue = 210f, customSaturation = 0.6f)

        assertTrue("hue 20 and hue 210 produced the same accent", warm.accent != cool.accent)
        assertTrue(
            "hue 20 and hue 210 produced the same background",
            warm.systemBackground != cool.systemBackground,
        )
    }

    @Test
    fun `a fully desaturated custom palette is still legible`() {
        val neutral = AppTheme.CUSTOM.colors(dark = false, customHue = 200f, customSaturation = 0f)

        // At zero saturation everything is grey — but the ink must still contrast with the surface,
        // or the app would render white-on-white.
        assertTrue("text and background collapsed", neutral.label != neutral.systemBackground)
        assertTrue("labels collapsed", neutral.secondaryLabel != neutral.systemBackground)
    }
}
