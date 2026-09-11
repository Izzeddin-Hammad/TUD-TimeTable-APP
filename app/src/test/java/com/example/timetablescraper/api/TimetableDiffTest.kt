package com.example.timetablescraper.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Behavioural tests for the extracted, pure diff engine — the logic that decides what students
 * are told changed. Each test here corresponds to a defect found in the original private
 * `TimetableRepository.computeChanges`.
 */
class TimetableDiffTest {

    private fun ev(
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

    private fun descriptions(changes: List<TimetableChange>) = changes.map { it.description }

    // ── no-op paths ────────────────────────────────────────────────────────────

    @Test
    fun `an unchanged timetable reports nothing`() {
        val previous = listOf(ev(), ev(module = "CMPU2004", start = "2025-10-08T14:00:00Z", end = "2025-10-08T16:00:00Z"))
        val incoming = listOf(ev(), ev(module = "CMPU2004", start = "2025-10-08T14:00:00Z", end = "2025-10-08T16:00:00Z"))

        assertTrue(TimetableDiff.diff(previous, incoming).isEmpty())
    }

    @Test
    fun `zone noise alone is not a change`() {
        // Regression: '...T09:00:00' vs '...T09:00:00Z' used to produce a REMOVED + ADDED pair.
        val previous = listOf(ev(start = "2025-10-07T09:00:00", end = "2025-10-07T11:00:00"))
        val incoming = listOf(ev(start = "2025-10-07T09:00:00.000Z", end = "2025-10-07T11:00:00.000Z"))

        assertTrue(TimetableDiff.diff(previous, incoming).isEmpty())
    }

    @Test
    fun `a first ever load reports no changes`() {
        // Nobody should be alerted about a timetable they have never seen.
        assertTrue(TimetableDiff.diff(previous = emptyList(), incoming = listOf(ev(), ev(module = "X"))).isEmpty())
    }

    @Test
    fun `two empty states are not a change`() {
        assertTrue(TimetableDiff.diff(previous = emptyList(), incoming = emptyList()).isEmpty())
    }

    // ── the many-to-one collapse regression (the phantom alert) ────────────────

    @Test
    fun `two sessions sharing a slot with different rooms report no change when nothing changed`() {
        // Regression (high): both sides were grouped by (module, start, end) and only `.first()`
        // was compared. With two members per group, the cache order (SQL `ORDER BY start ASC`,
        // ties undefined) could differ from the API order, producing a phantom
        // "Room: A214 → B102" alert for an unchanged timetable.
        val previous = listOf(
            ev(room = "A214", group = "G1"),
            ev(room = "B102", group = "G2"),
        )
        val incomingOrderSwapped = listOf(
            ev(room = "B102", group = "G2"),
            ev(room = "A214", group = "G1"),
        )

        assertTrue(TimetableDiff.diff(previous, incomingOrderSwapped).isEmpty())
    }

    @Test
    fun `a room change on the second session of a shared slot is detected`() {
        // Regression (high): the second member of each slot group was never inspected, so this
        // real change was silently swallowed.
        val previous = listOf(ev(room = "A214", group = "G1"), ev(room = "B102", group = "G2"))
        val incoming = listOf(ev(room = "A214", group = "G1"), ev(room = "C303", group = "G2"))

        val changes = TimetableDiff.diff(previous, incoming)

        assertEquals(1, changes.size)
        assertEquals(ChangeType.MODIFIED, changes.single().type)
        assertEquals("Room: B102 → C303", changes.single().description)
    }

    // ── field-level changes ────────────────────────────────────────────────────

    @Test
    fun `a room swap is one MODIFIED with a field level diff`() {
        val changes = TimetableDiff.diff(listOf(ev(room = "A214")), listOf(ev(room = "B102")))

        assertEquals(1, changes.size)
        assertEquals(ChangeType.MODIFIED, changes.single().type)
        assertEquals("Room: A214 → B102", changes.single().description)
        assertEquals("CMPU3021", changes.single().moduleCode)
        assertEquals("Tue", changes.single().day)
        assertEquals("09:00 - 11:00", changes.single().timeRange)
    }

    @Test
    fun `a lecturer change is reported without churning the room`() {
        val changes = TimetableDiff.diff(
            listOf(ev(lecturer = "Dr. A. Byrne")),
            listOf(ev(lecturer = "Dr. C. Nolan")),
        )

        assertEquals(listOf("Lecturer: Dr. A. Byrne → Dr. C. Nolan"), descriptions(changes))
    }

    @Test
    fun `a title change is reported`() {
        // Regression: `title` was never compared, so a renamed module was invisible.
        val changes = TimetableDiff.diff(
            listOf(ev(title = "Algorithms")),
            listOf(ev(title = "Algorithms & Data Structures")),
        )

        assertEquals(ChangeType.MODIFIED, changes.single().type)
        assertTrue(changes.single().description.contains("Title: Algorithms → Algorithms & Data Structures"))
    }

    @Test
    fun `multiple field changes are combined into one revision`() {
        val changes = TimetableDiff.diff(
            listOf(ev(room = "A214", lecturer = "Dr. A. Byrne")),
            listOf(ev(room = "B102", lecturer = "Dr. C. Nolan")),
        )

        assertEquals(1, changes.size)
        val description = changes.single().description
        assertTrue(description.contains("Room: A214 → B102"))
        assertTrue(description.contains("Lecturer: Dr. A. Byrne → Dr. C. Nolan"))
    }

    @Test
    fun `a group change is reported in canonical form and both groups are named`() {
        val changes = TimetableDiff.diff(listOf(ev(group = "G1")), listOf(ev(group = "G2")))

        assertEquals(ChangeType.MODIFIED, changes.single().type)
        assertEquals("Group: G1 → G2", changes.single().description)
    }

    @Test
    fun `a group becoming blank is reported`() {
        val changes = TimetableDiff.diff(listOf(ev(group = "G1")), listOf(ev(group = "")))

        assertEquals("Group: G1 → —", changes.single().description)
    }

    @Test
    fun `a blank group becoming a real one is a MODIFIED not an ADDED`() {
        val changes = TimetableDiff.diff(listOf(ev(group = "")), listOf(ev(group = "G2")))

        assertEquals(1, changes.size)
        assertEquals(ChangeType.MODIFIED, changes.single().type)
    }

    // ── additions, removals and cancellations ──────────────────────────────────

    @Test
    fun `a session that disappears is one REMOVED`() {
        val changes = TimetableDiff.diff(
            previous = listOf(ev(), ev(module = "CMPU2004", start = "2025-10-08T14:00:00Z", end = "2025-10-08T16:00:00Z")),
            incoming = listOf(ev()),
        )

        assertEquals(1, changes.size)
        assertEquals(ChangeType.REMOVED, changes.single().type)
        assertEquals("CMPU2004", changes.single().moduleCode)
        assertEquals("Class removed", changes.single().description)
    }

    @Test
    fun `a new session is one ADDED`() {
        val changes = TimetableDiff.diff(
            previous = listOf(ev()),
            incoming = listOf(ev(), ev(module = "CMPU2004", start = "2025-10-08T14:00:00Z", end = "2025-10-08T16:00:00Z")),
        )

        assertEquals(1, changes.size)
        assertEquals(ChangeType.ADDED, changes.single().type)
        assertEquals("New class", changes.single().description)
    }

    @Test
    fun `a fully cancelled week reports every session as removed`() {
        // This is what lets the UI say "your week was cancelled" instead of silently showing
        // nothing (or, worse, resurrecting the old classes from cache).
        val previous = listOf(ev(), ev(module = "CMPU2004", start = "2025-10-08T14:00:00Z", end = "2025-10-08T16:00:00Z"))

        val changes = TimetableDiff.diff(previous, incoming = emptyList())

        assertEquals(2, changes.size)
        assertTrue(changes.all { it.type == ChangeType.REMOVED })
    }

    // ── moves ──────────────────────────────────────────────────────────────────

    @Test
    fun `a session moved to a new time is ONE time change not a remove plus an add`() {
        // Regression: a time shift changes the (module, start, end) key, so the old code emitted
        // a removal and an addition — two notifications, and a student-visible "2 changes" for
        // one real event.
        val changes = TimetableDiff.diff(
            previous = listOf(ev(start = "2025-10-07T10:00:00Z", end = "2025-10-07T11:00:00Z")),
            incoming = listOf(ev(start = "2025-10-07T12:00:00Z", end = "2025-10-07T13:00:00Z")),
        )

        assertEquals(1, changes.size)
        assertEquals(ChangeType.MODIFIED, changes.single().type)
        assertEquals("Time: 10:00 - 11:00 → 12:00 - 13:00", changes.single().description)
        assertEquals("12:00 - 13:00", changes.single().timeRange)
    }

    @Test
    fun `a move to a different room is a room change not a move`() {
        // Same slot, different room => MODIFIED with the room diff (no time churn to report).
        val changes = TimetableDiff.diff(listOf(ev(room = "A214")), listOf(ev(room = "B102")))

        assertEquals(1, changes.size)
        assertTrue(changes.single().description.startsWith("Room:"))
        assertFalse(changes.single().description.contains("Time:"))
    }

    @Test
    fun `two simultaneous removals and additions are not silently merged`() {
        // A swap of two modules must remain visible as real churn, not be paired away.
        val previous = listOf(ev(module = "A", room = "R1"), ev(module = "B", room = "R2"))
        val incoming = listOf(ev(module = "C", room = "R3"), ev(module = "D", room = "R4"))

        val changes = TimetableDiff.diff(previous, incoming)

        assertEquals(4, changes.size)
        assertEquals(2, changes.count { it.type == ChangeType.REMOVED })
        assertEquals(2, changes.count { it.type == ChangeType.ADDED })
    }

    // ── determinism ────────────────────────────────────────────────────────────

    @Test
    fun `the same input always produces the same ordered output`() {
        val previous = listOf(
            ev(module = "C1", start = "2025-10-07T09:00:00Z", end = "2025-10-07T10:00:00Z"),
            ev(module = "C2", start = "2025-10-08T09:00:00Z", end = "2025-10-08T10:00:00Z"),
            ev(module = "C3", start = "2025-10-09T09:00:00Z", end = "2025-10-09T10:00:00Z"),
        )
        val incoming = emptyList<ApiEvent>()

        val first = TimetableDiff.diff(previous, incoming)
        val second = TimetableDiff.diff(previous.shuffled(), incoming)

        assertEquals(first.map { it.moduleCode }, second.map { it.moduleCode })
        assertEquals(listOf("C1", "C2", "C3"), first.map { it.moduleCode })
    }

    @Test
    fun `unparseable timestamps degrade to unknown rather than throwing`() {
        val previous = listOf(ev(start = "garbage", end = "garbage"))
        val incoming = listOf(ev(start = "garbage", end = "garbage", room = "B102"))

        val changes = TimetableDiff.diff(previous, incoming)

        assertEquals(1, changes.size)
        assertEquals("?", changes.single().day)
        assertEquals("??:?? - ??:??", changes.single().timeRange)
    }
}
