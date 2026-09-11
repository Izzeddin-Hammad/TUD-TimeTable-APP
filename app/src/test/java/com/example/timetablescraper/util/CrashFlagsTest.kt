package com.example.timetablescraper.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the recovery-screen decision.
 *
 * The case that matters most is `marker older than the last clear`: the marker file is
 * best-effort, and when it cannot be deleted the app used to show "Something went wrong" on
 * **every** launch, with both recovery buttons appearing to do nothing.
 */
class CrashFlagsTest {

    private fun shouldShow(
        prefsFlag: Boolean = false,
        markerExists: Boolean = false,
        markerLength: Long = 0L,
        markerLastModified: Long = 0L,
        clearedAt: Long = 0L,
    ) = CrashFlags.shouldShowRecovery(prefsFlag, markerExists, markerLength, markerLastModified, clearedAt)

    @Test
    fun `no signal means no recovery screen`() {
        assertFalse(shouldShow())
    }

    @Test
    fun `the preferences flag alone is enough`() {
        assertTrue(shouldShow(prefsFlag = true))
    }

    @Test
    fun `a marker with content and no prior clear is a crash`() {
        // The normal path: persistCrash writes the marker, nothing has cleared it yet.
        assertTrue(shouldShow(markerExists = true, markerLength = 120, markerLastModified = 1_000))
    }

    @Test
    fun `an empty marker is not a crash`() {
        // Regression: clearing truncates an undeletable marker. Reading that as a crash put the
        // user straight back on the recovery screen.
        assertFalse(shouldShow(markerExists = true, markerLength = 0L, markerLastModified = 1_000))
    }

    @Test
    fun `a missing marker is not a crash`() {
        assertFalse(shouldShow(markerExists = false, markerLength = 0L, markerLastModified = 1_000))
    }

    @Test
    fun `a marker written after the last clear is a crash`() {
        assertTrue(
            shouldShow(markerExists = true, markerLength = 50, markerLastModified = 5_000, clearedAt = 1_000),
        )
    }

    @Test
    fun `a marker older than the last clear is stale and ignored`() {
        // The escape hatch: deletion failed, but the user cleared the crash, so they must get in.
        assertFalse(
            shouldShow(markerExists = true, markerLength = 50, markerLastModified = 1_000, clearedAt = 5_000),
        )
    }

    @Test
    fun `a marker written in the same millisecond as the clear is not treated as new`() {
        // Guards the boundary: only strictly newer markers count.
        assertFalse(
            shouldShow(markerExists = true, markerLength = 50, markerLastModified = 5_000, clearedAt = 5_000),
        )
    }

    @Test
    fun `the preferences flag wins over a stale marker`() {
        // A real crash recorded while a stale marker is still lying around must still be shown.
        assertTrue(
            shouldShow(prefsFlag = true, markerExists = true, markerLength = 50, markerLastModified = 1_000, clearedAt = 5_000),
        )
    }

    @Test
    fun `negative timestamps from an unreadable file do not fake a crash`() {
        assertFalse(shouldShow(markerExists = true, markerLength = 10, markerLastModified = 0L, clearedAt = 1_000))
    }
}

/**
 * Tests for the on-disk crash record. It has to be readable from the marker file because that
 * copy is written synchronously, while the preferences copy can be lost when the process dies.
 */
class CrashMarkerTest {

    @Test
    fun `a crash record round trips`() {
        val text = CrashMarker.format(
            timestamp = 1_757_923_200_000,
            message = "IllegalStateException: boom",
            stacktrace = "java.lang.IllegalStateException: boom\n\tat com.example.Foo.bar(Foo.kt:42)",
        )

        val parsed = CrashMarker.parse(text)

        assertEquals(1_757_923_200_000L, parsed?.timestamp)
        assertEquals("IllegalStateException: boom", parsed?.message)
        assertTrue(parsed!!.stacktrace!!.contains("Foo.kt:42"))
    }

    @Test
    fun `a record written by the previous implementation still parses`() {
        // The old handler wrote exactly this text; markers already on users' devices must work.
        val legacy = "Crash at: 1757923200000\n" +
            "Message: NullPointerException\n\n" +
            "java.lang.NullPointerException\n\tat com.example.Bar.baz(Bar.kt:7)"

        val parsed = CrashMarker.parse(legacy)

        assertEquals(1757923200000L, parsed?.timestamp)
        assertEquals("NullPointerException", parsed?.message)
        assertTrue(parsed!!.stacktrace!!.contains("Bar.kt:7"))
    }

    @Test
    fun `an empty or blank marker holds no crash`() {
        assertNull(CrashMarker.parse(""))
        assertNull(CrashMarker.parse("   \n  "))
        assertNull(CrashMarker.parse(null))
    }

    @Test
    fun `a truncated marker still yields whatever is readable`() {
        // A partial write (power loss) must not produce an empty detail panel if a message made
        // it to disk.
        val parsed = CrashMarker.parse("Crash at: 1757923200000\nMessage: truncated writ")

        assertEquals(1757923200000L, parsed?.timestamp)
        assertEquals("truncated writ", parsed?.message)
        assertNull(parsed?.stacktrace)
    }

    @Test
    fun `a record with a message but no stacktrace parses`() {
        val parsed = CrashMarker.parse(CrashMarker.format(5L, "OutOfMemoryError", null))

        assertEquals(5L, parsed?.timestamp)
        assertEquals("OutOfMemoryError", parsed?.message)
        assertNull(parsed?.stacktrace)
    }

    @Test
    fun `an unparseable marker is reported rather than crashing`() {
        // Timestamp lost, but the raw text is still the only evidence of what happened, so it is
        // surfaced instead of being dropped (dropping it is what produced a blank screen).
        val parsed = CrashMarker.parse("something went sideways")

        assertEquals(0L, parsed?.timestamp)
        assertEquals("Unknown error", parsed?.message)
        assertEquals("something went sideways", parsed?.stacktrace)
    }

    @Test
    fun `garbage that contains no record at all yields null`() {
        assertNull(CrashMarker.parse("\n\n\n"))
    }
}
