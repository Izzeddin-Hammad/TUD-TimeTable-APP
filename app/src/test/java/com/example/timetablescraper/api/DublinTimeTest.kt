package com.example.timetablescraper.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The single place that maps an upstream timestamp to the institution's local wall clock.
 *
 * `DublinTime` sits under both the timetable grid and the change feed, and the whole point of the
 * feature is that those two agree and that the answer is right on both sides of the DST boundary —
 * so the boundary cases are pinned here rather than left to whichever surface happens to be tested.
 */
class DublinTimeTest {

    @Test
    fun `a UTC timestamp is projected into Irish local time in summer`() {
        // Irish Summer Time is UTC+1: the API sends 08:00+00:00 for a 09:00 lecture.
        assertEquals("09:00", DublinTime.timeOfDay("2026-09-14T08:00:00+00:00"))
    }

    @Test
    fun `the Z designator is understood`() {
        assertEquals("09:00", DublinTime.timeOfDay("2026-09-14T08:00:00Z"))
    }

    @Test
    fun `a non-UTC offset is projected by instant, not read literally`() {
        // 08:00 at +02:00 is 06:00 UTC, i.e. 07:00 in Irish summer time.
        assertEquals("07:00", DublinTime.timeOfDay("2026-09-14T08:00:00+02:00"))
    }

    @Test
    fun `winter needs no shift`() {
        // From late October Dublin is on GMT, so the projection is a no-op.
        assertEquals("08:00", DublinTime.timeOfDay("2026-12-07T08:00:00+00:00"))
    }

    @Test
    fun `the weekday comes from the local date, not the UTC one`() {
        // Monday 00:30 local is serialised as Sunday 23:30 UTC; reading the UTC date put the class
        // — and the week it was classified into — a day early.
        assertEquals("Mon", DublinTime.dayOfWeek("2026-09-13T23:30:00+00:00"))
        assertEquals("Mon", DublinTime.dayOfWeek("2026-09-14T00:30:00+01:00"))
    }

    @Test
    fun `a value with no offset has no local projection`() {
        // Callers treat `null` as "already local", which is the only safe reading for a naive
        // timestamp — and it keeps malformed rows on the ingest path crash-proof.
        assertNull(DublinTime.toLocal("2026-09-14T08:00:00"))
        assertNull(DublinTime.timeOfDay("2026-09-14"))
        assertNull(DublinTime.dayOfWeek("2026-09-14"))
        assertNull(DublinTime.toLocal(""))
        assertNull(DublinTime.toLocal("   "))
        assertNull(DublinTime.toLocal(null))
        assertNull(DublinTime.toLocal("not-a-date"))
    }
}
