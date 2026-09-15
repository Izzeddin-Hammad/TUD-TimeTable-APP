package com.example.timetablescraper.ui.screens

import androidx.compose.ui.graphics.Color
import com.example.timetablescraper.ui.theme.DarkIosColors
import com.example.timetablescraper.ui.theme.LightIosColors
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the session-type colour mapping.
 *
 * This table used to be Material's own hues written inline in the timetable screen — blue 500 for
 * labs, green 500 for tutorials, deep purple for lectures, and a blue-grey fallback. It now
 * resolves against the iOS palette, so these tests check two things: which token each type maps
 * to, and that the answer really is a palette colour. A regression back to a literal would still
 * pass "it is a blue", which is why the exact tokens are asserted.
 */
class SessionTypeColorTest {

    private val light = LightIosColors
    private val dark = DarkIosColors

    // Color is an inline value class over ULong; assertTrue keeps JUnit's overload resolution out
    // of the way and prints both colours on failure.
    private fun assertColor(expected: Color, actual: Color, what: String) {
        assertTrue("$what: expected $expected but was $actual", expected == actual)
    }

    @Test
    fun `lectures use the palette purple`() {
        assertColor(light.purple, colorForType("Lecture", light), "Lecture")
        assertColor(light.purple, colorForType("Lec", light), "Lec")
    }

    @Test
    fun `tutorials use the palette green`() {
        assertColor(light.green, colorForType("Tutorial", light), "Tutorial")
        assertColor(light.green, colorForType("Tut", light), "Tut")
    }

    @Test
    fun `labs and practicals use the accent`() {
        assertColor(light.accent, colorForType("Lab", light), "Lab")
        assertColor(light.accent, colorForType("Practical", light), "Practical")
    }

    @Test
    fun `matching is case-insensitive and works on the long upstream names`() {
        assertColor(light.purple, colorForType("lecture", light), "lowercase")
        assertColor(light.purple, colorForType("LECTURE", light), "uppercase")
        assertColor(light.accent, colorForType("Laboratory", light), "Laboratory")
        assertColor(light.green, colorForType("Tutorial 1", light), "Tutorial 1")
    }

    @Test
    fun `an unrecognised type falls back to the neutral secondary label`() {
        assertColor(light.secondaryLabel, colorForType("Seminar", light), "Seminar")
        assertColor(light.secondaryLabel, colorForType("", light), "empty type")
    }

    @Test
    fun `the colour comes from the active scheme`() {
        // The old table could not do this: one hex literal served both themes.
        assertColor(dark.purple, colorForType("Lecture", dark), "Lecture in dark")
        assertTrue(
            "light and dark should not resolve to the same value",
            colorForType("Lecture", light) != colorForType("Lecture", dark)
        )
    }

    @Test
    fun `no material hue survives in the mapping`() {
        val materialHues = listOf(
            Color(0xFF2196F3), // blue 500, used for labs
            Color(0xFF4CAF50), // green 500, used for tutorials
            Color(0xFF673AB7), // deep purple 500, used for lectures
            Color(0xFF607D8B), // blue grey 500, the old fallback
        )
        for (type in listOf("Lab", "Practical", "Tutorial", "Tut", "Lecture", "Lec", "Seminar")) {
            val resolved = colorForType(type, light)
            assertTrue("$type still resolves to a Material hue", resolved !in materialHues)
        }
    }
}
