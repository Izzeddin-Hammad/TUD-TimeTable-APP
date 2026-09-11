package com.example.timetablescraper.update

import com.example.timetablescraper.api.Semver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Behaviour tests for the update check.
 *
 * This replaces a version of this file that reached the private `isNewerThan` through Java
 * reflection. Reflection tests pin the *shape* of the code — they break on any harmless
 * refactor, and they test a private helper rather than the behaviour a student experiences.
 * Version comparison is now a public, directly tested function ([Semver], see `SemverTest`),
 * and what remains here is the public [`UpdateChecker.UpdateResult`] contract.
 */
class UpdateCheckerTest {

    @Test
    fun `an available update carries the version and a download url`() {
        val result = UpdateChecker.UpdateResult(
            updateAvailable = true,
            remoteVersion = "2.0",
            downloadUrl = "https://example.com/app.apk",
        )

        assertTrue(result.updateAvailable)
        assertEquals("2.0", result.remoteVersion)
        assertEquals("https://example.com/app.apk", result.downloadUrl)
        assertNull(result.errorMessage)
    }

    @Test
    fun `a check that finds nothing is not an error`() {
        val result = UpdateChecker.UpdateResult(updateAvailable = false, remoteVersion = null)

        assertFalse(result.updateAvailable)
        assertNull(result.errorMessage)
    }

    @Test
    fun `an error result carries no version or url`() {
        val result = UpdateChecker.UpdateResult(updateAvailable = false, errorMessage = "Network error")

        assertFalse(result.updateAvailable)
        assertNull(result.remoteVersion)
        assertNull(result.downloadUrl)
        assertEquals("Network error", result.errorMessage)
    }

    @Test
    fun `the version comparison the updater relies on is directly testable`() {
        // The updater compares `v$version` style strings from APK filenames; this documents that
        // contract without reflection, using the production implementation.
        assertTrue(Semver.isNewer("v1.23", "v1.22"))
        assertFalse(Semver.isNewer("v1.22", "v1.22"))
        assertFalse(Semver.isNewer("v1.21", "v1.22"))
    }

    @Test
    fun `an APK filename version compares correctly against the installed version`() {
        // Shape of the real payload: TimeTable-v1.17-debug.apk -> "1.17"
        val remote = "TimeTable-v1.17-debug.apk".substringAfter("-v").substringBefore("-")

        assertEquals("1.17", remote)
        assertTrue(Semver.isNewer(remote, "1.16"))
        assertFalse(Semver.isNewer(remote, "1.17"))
    }
}
