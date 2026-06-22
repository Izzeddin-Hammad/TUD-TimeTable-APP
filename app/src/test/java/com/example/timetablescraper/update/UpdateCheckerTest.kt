package com.example.timetablescraper.update

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for UpdateChecker version comparison logic.
 *
 * Tests the isNewerThan() private function via Java reflection since the
 * real BuildConfig.VERSION_NAME is only available in the instrumented environment.
 */
class UpdateCheckerTest {

    /** Access the private isNewerThan function via Java reflection. */
    private fun isNewerThan(remote: String, local: String): Boolean {
        val method = UpdateChecker::class.java.getDeclaredMethod(
            "isNewerThan", String::class.java, String::class.java
        )
        method.isAccessible = true
        return method.invoke(UpdateChecker, remote, local) as Boolean
    }

    @Test
    fun `same version is not newer`() {
        assertFalse(isNewerThan("1.4", "1.4"))
        assertFalse(isNewerThan("v1.0", "v1.0"))
        assertFalse(isNewerThan("2.3.1", "2.3.1"))
    }

    @Test
    fun `major version bump is newer`() {
        assertTrue(isNewerThan("2.0", "1.5"))
        assertTrue(isNewerThan("v3.0", "v2.9"))
    }

    @Test
    fun `minor version bump is newer`() {
        assertTrue(isNewerThan("1.5", "1.4"))
        assertTrue(isNewerThan("v2.1", "v2.0"))
    }

    @Test
    fun `patch version bump is newer`() {
        assertTrue(isNewerThan("1.2.1", "1.2.0"))
        assertTrue(isNewerThan("v1.0.1", "v1.0.0"))
    }

    @Test
    fun `remote older is not newer`() {
        assertFalse(isNewerThan("1.3", "1.5"))
        assertFalse(isNewerThan("v1.0", "v2.0"))
        assertFalse(isNewerThan("1.2.0", "1.2.1"))
    }

    @Test
    fun `leading v prefix is stripped`() {
        assertTrue(isNewerThan("v1.5", "1.4"))
        assertTrue(isNewerThan("1.6", "v1.5"))
        assertTrue(isNewerThan("V2.0", "v1.9"))
    }

    @Test
    fun `different segment counts compared correctly`() {
        // "1.4" vs "1.4.0" — shorter is padded with "0"
        assertFalse(isNewerThan("1.4", "1.4.0")) // equal
        assertTrue(isNewerThan("1.4.1", "1.4"))   // extra segment
        assertFalse(isNewerThan("1.4", "1.4.1"))  // remote shorter, local longer
    }

    @Test
    fun `numeric segments compared numerically not lexicographically`() {
        // "1.10" should be > "1.9" (numeric), not "1.10" < "1.9" (lexicographic)
        assertTrue(isNewerThan("1.10", "1.9"))
        assertTrue(isNewerThan("2.10.0", "2.9.5"))
    }

    @Test
    fun `non-numeric segments fall back to lexicographic`() {
        // For pre-release tags like "1.0-alpha" vs "1.0-beta"
        assertTrue(isNewerThan("1.0-beta", "1.0-alpha"))
    }

    @Test
    fun `detects update result has correct shape`() {
        val result = UpdateChecker.UpdateResult(
            updateAvailable = true,
            remoteVersion = "v2.0",
            downloadUrl = "https://example.com/app.apk"
        )
        assertTrue(result.updateAvailable)
        assertEquals("v2.0", result.remoteVersion)
        assertEquals("https://example.com/app.apk", result.downloadUrl)
        assertNull(result.errorMessage)
    }

    @Test
    fun `error result has no version or url`() {
        val result = UpdateChecker.UpdateResult(
            updateAvailable = false,
            errorMessage = "Network error"
        )
        assertFalse(result.updateAvailable)
        assertNull(result.remoteVersion)
        assertNull(result.downloadUrl)
        assertEquals("Network error", result.errorMessage)
    }
}
