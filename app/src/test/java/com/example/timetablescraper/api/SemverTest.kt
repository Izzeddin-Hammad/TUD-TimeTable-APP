package com.example.timetablescraper.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Version-comparison tests. Replaces the previous `UpdateCheckerTest`, which reached the
 * `private fun isNewerThan` through Java reflection — a test that pins the code's *shape*
 * (and breaks on any harmless refactor) rather than its behaviour.
 */
class SemverTest {

    @Test
    fun `a higher patch minor or major is newer`() {
        assertTrue(Semver.isNewer("1.22.1", "1.22.0"))
        assertTrue(Semver.isNewer("1.23.0", "1.22.9"))
        assertTrue(Semver.isNewer("2.0.0", "1.99.99"))
    }

    @Test
    fun `an equal or lower version is not newer`() {
        assertFalse(Semver.isNewer("1.22.0", "1.22.0"))
        assertFalse(Semver.isNewer("1.21.0", "1.22.0"))
        assertFalse(Semver.isNewer("0.9.0", "1.0.0"))
    }

    @Test
    fun `missing components count as zero`() {
        assertFalse(Semver.isNewer("1.2", "1.2.0"))
        assertFalse(Semver.isNewer("1.2.0", "1.2"))
        assertTrue(Semver.isNewer("1.2.1", "1.2"))
    }

    @Test
    fun `a leading v is tolerated`() {
        assertTrue(Semver.isNewer("v1.23", "1.22"))
        assertTrue(Semver.isNewer("V2.0.0", "v1.9.9"))
    }

    @Test
    fun `suffixes from real APK names are ignored`() {
        // The updater parses names like TimeTable-v1.22-debug.apk
        assertTrue(Semver.isNewer("1.23-debug", "1.22"))
        assertFalse(Semver.isNewer("1.22-debug", "1.22"))
        assertTrue(Semver.isNewer("1.23.0-beta.1", "1.22.0"))
    }

    @Test
    fun `garbage never throws and compares as zero`() {
        assertFalse(Semver.isNewer("banana", "1.22.0"))
        assertFalse(Semver.isNewer(null, "1.22.0"))
        assertFalse(Semver.isNewer("", "1.22.0"))
        assertEquals(0, Semver.compare("banana", "0.0.0"))
    }

    @Test
    fun `both sides garbage compare equal`() {
        assertEquals(0, Semver.compare("??", null))
        assertFalse(Semver.isNewer("??", "also garbage"))
    }

    @Test
    fun `multi digit components are compared numerically not lexically`() {
        // Lexical comparison would call "1.9" newer than "1.10".
        assertTrue(Semver.isNewer("1.10.0", "1.9.0"))
        assertFalse(Semver.isNewer("1.9.0", "1.10.0"))
    }

    @Test
    fun `compare is antisymmetric`() {
        assertEquals(-1, Semver.compare("1.21.0", "1.22.0"))
        assertEquals(1, Semver.compare("1.22.0", "1.21.0"))
        assertEquals(0, Semver.compare("1.22.0", "1.22.0"))
    }

    @Test
    fun `normalise produces a comparable display form`() {
        assertEquals("1.22.0", Semver.normalise("v1.22"))
        assertEquals("0.0.0", Semver.normalise(null))
        assertEquals("2.1.3", Semver.normalise("2.1.3-debug"))
    }
}
