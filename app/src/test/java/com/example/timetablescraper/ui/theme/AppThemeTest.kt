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
        assertEquals(AppTheme.CLASSIC, AppTheme.DEFAULT)
        assertEquals(AppTheme.CLASSIC, AppTheme.fromId(null))
        assertEquals(AppTheme.CLASSIC, AppTheme.fromId(""))
        assertEquals(AppTheme.CLASSIC, AppTheme.fromId("no-such-theme"))
        assertEquals(AppTheme.CLASSIC, AppTheme.fromId(AppTheme.DEFAULT.id))
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
    fun `the themes are visually distinct from one another`() {
        val byAccent = AppTheme.entries
            .associate { it.id to it.colors(dark = false).accent }
            .toList()
            .groupBy { it.second }
            .filterValues { it.size > 1 }
        assertTrue("two themes share an accent colour: $byAccent", byAccent.isEmpty())
    }

    @Test
    fun `a cozy theme is warm and low-contrast, not clinical black on white`() {
        // The point of the feature. Classic keeps Apple's exact palette, so it is exempt.
        for (theme in AppTheme.entries.filter { it != AppTheme.CLASSIC }) {
            val c = theme.colors(dark = false)
            assertTrue("${theme.id}: background is pure white", c.systemBackground != Color.White)
            assertTrue("${theme.id}: ink is pure black", c.label != Color.Black)
        }
    }
}
