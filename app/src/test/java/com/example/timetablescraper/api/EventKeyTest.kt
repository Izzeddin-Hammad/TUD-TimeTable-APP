package com.example.timetablescraper.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the single source of truth for session identity ([EventKey]).
 *
 * These exist because the app previously used three different keys in three places
 * (dedupe, diff, UI filtering), and any disagreement between them shows a student a
 * timetable that is wrong rather than merely stale.
 */
class EventKeyTest {

    private fun event(
        module: String = "CMPU3021",
        title: String = "Software Engineering",
        type: String = "Lecture",
        lecturer: String = "Dr. A. Byrne",
        room: String = "A214",
        start: String = "2025-10-07T09:00:00Z",
        end: String = "2025-10-07T11:00:00Z",
        group: String = "",
    ) = ApiEvent(
        module_code = module, title = title, type = type, lecturer = lecturer, room = room,
        start = start, end = end, group = group,
    )

    // ── wallClock normalisation ────────────────────────────────────────────────

    @Test
    fun `wall clock projects an offset value into the institution's zone`() {
        // 09:00Z is 10:00 in Irish Summer Time (2025-10-07 is before the clocks go back).
        assertEquals("2025-10-07t10:00:00", EventKey.wallClock("2025-10-07T09:00:00Z"))
        assertEquals("2025-10-07t10:00:00", EventKey.wallClock("2025-10-07T09:00:00z"))
    }

    @Test
    fun `wall clock strips fractional seconds`() {
        assertEquals("2025-10-07t10:00:00", EventKey.wallClock("2025-10-07T09:00:00.000Z"))
        assertEquals("2025-10-07t10:00:00", EventKey.wallClock("2025-10-07T09:00:00.123456Z"))
    }

    @Test
    fun `wall clock projects by instant, not by the digits written`() {
        // 09:00+01:00 already *is* 09:00 in Dublin; 09:00-05:00 is 15:00 there. Reading the digits
        // and ignoring the offset — what this used to do — both mis-times the session and made two
        // different instants compare equal.
        assertEquals("2025-10-07t09:00:00", EventKey.wallClock("2025-10-07T09:00:00+01:00"))
        assertEquals("2025-10-07t15:00:00", EventKey.wallClock("2025-10-07T09:00:00-05:00"))
    }

    @Test
    fun `the key is the local clock the season's UTC digits project to`() {
        // One 09:00 Dublin lecture, serialised in UTC through the year: 08:00Z in August (IST) and
        // 09:00Z in December (GMT). The *key* is the local time in both cases; the UTC digits are
        // an artefact of when the fetch happened, and must not be what identifies the class.
        assertEquals("2026-08-12t09:00:00", EventKey.wallClock("2026-08-12T08:00:00Z"))
        assertEquals("2026-12-09t09:00:00", EventKey.wallClock("2026-12-09T09:00:00Z"))
    }

    @Test
    fun `wall clock reads a naive value literally as already local`() {
        assertEquals("2025-10-07t09:00:00", EventKey.wallClock("2025-10-07T09:00:00"))
        assertTrue(EventKey.wallClock("2025-10-07T09:00:00Z").startsWith("2025-10-07"))
    }

    @Test
    fun `wall clock tolerates blank and unparseable input`() {
        assertEquals("", EventKey.wallClock(null))
        assertEquals("", EventKey.wallClock(""))
        assertEquals("", EventKey.wallClock("   "))
        assertEquals("not a date", EventKey.wallClock("  NOT A DATE  "))
    }

    @Test
    fun `timeOfDay extracts HH mm`() {
        assertEquals("10:00", EventKey.timeOfDay("2025-10-07T09:00:00Z"))
        assertEquals("23:45", EventKey.timeOfDay("2025-10-07T23:45:00+01:00"))
        assertEquals("??:??", EventKey.timeOfDay(""))
        assertEquals("??:??", EventKey.timeOfDay("2025-10-07"))
    }

    // ── sessionKey ─────────────────────────────────────────────────────────────

    @Test
    fun `the same instant written with different zone noise shares one key`() {
        // Regression: '...T09:00:00' vs '...T09:00:00Z' used to look like two different classes,
        // which produced a phantom "class removed / new class" alert pair on every refresh.
        val zulu = event(start = "2025-10-07T09:00:00Z", end = "2025-10-07T11:00:00Z")
        val withMillis = event(start = "2025-10-07T09:00:00.000Z", end = "2025-10-07T11:00:00.000Z")
        // …and the same instant written with a different offset is the same session too.
        val withOffset = event(start = "2025-10-07T10:00:00+01:00", end = "2025-10-07T12:00:00+01:00")

        assertEquals(EventKey.sessionKey(zulu), EventKey.sessionKey(withMillis))
        assertEquals(EventKey.sessionKey(zulu), EventKey.sessionKey(withOffset))
    }

    @Test
    fun `session key ignores fields that describe a change rather than an identity`() {
        val original = event(room = "A214", lecturer = "Dr. A. Byrne", group = "G1", type = "Lecture")
        val roomMoved = event(room = "B102", lecturer = "Dr. C. Nolan", group = "G2", type = "Tutorial")
        assertEquals(EventKey.sessionKey(original), EventKey.sessionKey(roomMoved))
    }

    @Test
    fun `session key distinguishes a different hour and a different module`() {
        val atNine = event(start = "2025-10-07T09:00:00Z", end = "2025-10-07T11:00:00Z")
        val atEleven = event(start = "2025-10-07T11:00:00Z", end = "2025-10-07T13:00:00Z")
        assertFalse(EventKey.sessionKey(atNine) == EventKey.sessionKey(atEleven))
        assertFalse(EventKey.sessionKey(atNine) == EventKey.sessionKey(event(module = "CMPU2004")))
    }

    @Test
    fun `session key is case and whitespace tolerant for the module code`() {
        assertEquals(EventKey.sessionKey(event(module = "cmpu3021")), EventKey.sessionKey(event(module = " CMPU3021 ")))
    }

    // ── meetingKey ──────────────────────────────────────────────────────────────

    @Test
    fun `meeting key treats timestamps that differ only by zone noise as one meeting`() {
        assertEquals(
            EventKey.meetingKey(event(start = "2025-10-07T09:00:00Z", end = "2025-10-07T11:00:00Z")),
            EventKey.meetingKey(event(start = "2025-10-07T09:00:00.000Z", end = "2025-10-07T11:00:00.000Z")),
        )
    }

    @Test
    fun `meeting key ignores room and group so an incomplete copy can be folded in`() {
        val withMetadata = event(room = "A201", group = "A")
        val withoutMetadata = event(room = "", group = "")

        assertEquals(EventKey.meetingKey(withMetadata), EventKey.meetingKey(withoutMetadata))
    }

    @Test
    fun `meeting key distinguishes a lecture from a tutorial in the same slot`() {
        assertFalse(EventKey.meetingKey(event(type = "Lecture")) == EventKey.meetingKey(event(type = "Tutorial")))
    }

    @Test
    fun `meeting key distinguishes a different hour module or lecturer`() {
        assertFalse(
            EventKey.meetingKey(event(start = "2025-10-07T09:00:00Z", end = "2025-10-07T11:00:00Z")) ==
                EventKey.meetingKey(event(start = "2025-10-07T11:00:00Z", end = "2025-10-07T13:00:00Z")),
        )
        assertFalse(EventKey.meetingKey(event(module = "CMPU3021")) == EventKey.meetingKey(event(module = "CMPU2004")))
        assertFalse(EventKey.meetingKey(event(lecturer = "Dr. A")) == EventKey.meetingKey(event(lecturer = "Dr. B")))
    }

    @Test
    fun `two parallel lab groups share a meeting key but survive de-duplication`() {
        // The key deliberately cannot separate them (they are the same meeting on paper); the
        // merge step in `deduplicateEvents` uses the *conflicting* room/group to keep both rows.
        val groupA = event(type = "Lab", room = "R1", group = "A", start = "2025-10-07T14:00:00Z", end = "2025-10-07T16:00:00Z")
        val groupB = event(type = "Lab", room = "R2", group = "B", start = "2025-10-07T14:00:00Z", end = "2025-10-07T16:00:00Z")

        assertEquals(EventKey.meetingKey(groupA), EventKey.meetingKey(groupB))
        assertEquals(2, TimetableUtils.deduplicateEvents(listOf(groupA, groupB)).size)
    }

    // ── identity ───────────────────────────────────────────────────────────────

    @Test
    fun `identity ignores the time so a moved session can be paired`() {
        val before = event(start = "2025-10-07T10:00:00Z", end = "2025-10-07T11:00:00Z")
        val after = event(start = "2025-10-07T12:00:00Z", end = "2025-10-07T13:00:00Z")
        assertEquals(EventKey.identity(before), EventKey.identity(after))
    }

    @Test
    fun `identity distinguishes different rooms so a room swap is not a time move`() {
        val inA = event(room = "A214")
        val inB = event(room = "B102")
        assertFalse(EventKey.identity(inA) == EventKey.identity(inB))
    }
}
