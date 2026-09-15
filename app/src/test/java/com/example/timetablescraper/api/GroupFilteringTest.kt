package com.example.timetablescraper.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Group behaviour, tested against **production code**.
 *
 * This file replaces a version of the same test that re-implemented the group rules inline
 * (`events.flatMap { it.group.split("+") ... }`) instead of calling the code under test. That
 * version passed no matter what production did — including while the UI was hiding every
 * all-cohort lecture as soon as a student picked a subgroup. Every assertion below now calls the
 * real implementation, so it fails when the behaviour regresses.
 */
class GroupFilteringTest {

    private fun event(
        module: String = "TU859",
        title: String = "Maths",
        type: String = "Lec",
        lecturer: String = "Staff",
        room: String = "Room",
        start: String = "2025-10-06T09:00:00Z",
        end: String = "2025-10-06T10:00:00Z",
        group: String = "",
    ) = ApiEvent(
        module_code = module, title = title, type = type, lecturer = lecturer, room = room,
        start = start, end = end, group = group,
    )

    // ── extracting the groups offered for a course ─────────────────────────────

    @Test
    fun `extracts distinct sorted groups from events`() {
        val events = listOf(
            event(title = "Maths", group = "A"),
            event(title = "Physics", type = "Lab", group = "B"),
            event(title = "CS", group = "A"),
            event(title = "DB", type = "Tut", group = "G1"),
        )

        // Production tokens, not a re-implementation of the split rules.
        val groups = events.flatMap { GroupMatcher.parse(it.group) }.distinct().sorted()

        assertEquals(listOf("A", "B", "G1"), groups)
    }

    @Test
    fun `extracts groups from compound group strings`() {
        val events = listOf(
            event(group = "A + B"),
            event(group = "G2,G1"),   // a different upstream dialect
        )

        val groups = events.flatMap { GroupMatcher.parse(it.group) }.distinct().sorted()

        assertEquals(listOf("A", "B", "G1", "G2"), groups)
    }

    @Test
    fun `plenary sessions do not contribute a group of their own`() {
        val events = listOf(event(group = ""), event(group = "A"))

        val groups = events.flatMap { GroupMatcher.parse(it.group) }.distinct().sorted()

        assertEquals(listOf("A"), groups)
        assertTrue(GroupMatcher.appliesToAll(""))
    }

    // ── filtering what the student sees ────────────────────────────────────────

    @Test
    fun `no selection shows every session`() {
        val events = listOf(event(group = "A"), event(group = "B"), event(group = ""))

        val displayed = events.filter { GroupMatcher.matches(it.group, null) }

        assertEquals(3, displayed.size)
    }

    @Test
    fun `selecting a group keeps that group and the plenary sessions`() {
        val events = listOf(event(group = "A"), event(group = "B"), event(group = ""))

        val displayed = events.filter { GroupMatcher.matches(it.group, "A") }

        // The blank-group lecture is the whole cohort's class and must remain visible.
        assertEquals(listOf("A", ""), displayed.map { it.group })
    }

    @Test
    fun `selecting a group hides other groups`() {
        val events = listOf(event(group = "A"), event(group = "B"))

        val displayed = events.filter { GroupMatcher.matches(it.group, "B") }

        assertEquals(listOf("B"), displayed.map { it.group })
    }

    @Test
    fun `a shared session appears under each of its groups`() {
        val shared = event(group = "G1 + G2")

        assertTrue(GroupMatcher.matches(shared.group, "G1"))
        assertTrue(GroupMatcher.matches(shared.group, "G2"))
    }

    @Test
    fun `selection is case and whitespace insensitive`() {
        val events = listOf(event(group = "g1"))

        val displayed = events.filter { GroupMatcher.matches(it.group, " G1 ") }

        assertEquals(1, displayed.size)
    }

    // ── de-duplication must not destroy groups ─────────────────────────────────

    @Test
    fun `parallel lab groups in different rooms are both kept`() {
        // Regression: the old dedup key (start|title|lecturer) dropped one of these, so a
        // whole subgroup silently lost its lab.
        val events = listOf(
            event(title = "Physics Lab", type = "Lab", room = "R1", group = "A", start = "2025-10-07T14:00:00Z", end = "2025-10-07T16:00:00Z"),
            event(title = "Physics Lab", type = "Lab", room = "R2", group = "B", start = "2025-10-07T14:00:00Z", end = "2025-10-07T16:00:00Z"),
        )

        val deduped = TimetableUtils.deduplicateEvents(events)

        assertEquals(2, deduped.size)
        assertEquals(setOf("A", "B"), deduped.map { it.group }.toSet())
    }

    @Test
    fun `a duplicate copy that is missing the group cannot erase the real group`() {
        // Regression: the same session can arrive twice, once carrying the cohort and once
        // without it (the parser resolves the cohort from two independent upstream fields, and
        // either can be absent). De-duplication must fold the incomplete copy into the complete
        // one — never keep the class with the group erased, which made it unreachable through the
        // group filter.
        val withGroup = event(group = "A", room = "A201")
        val withoutGroup = event(group = "", room = "A201")

        val deduped = TimetableUtils.deduplicateEvents(listOf(withoutGroup, withGroup))

        assertEquals(1, deduped.size)
        assertEquals("A", deduped.single().group)
    }

    @Test
    fun `a duplicate copy that is missing the room cannot erase the real room`() {
        val withRoom = event(group = "A", room = "A201")
        val withoutRoom = event(group = "A", room = "")

        val deduped = TimetableUtils.deduplicateEvents(listOf(withoutRoom, withRoom))

        assertEquals(1, deduped.size)
        assertEquals("A201", deduped.single().room)
    }

    @Test
    fun `two different groups in the same room are both kept`() {
        // Conflicting cohorts are genuinely different classes, so neither may be dropped.
        val groupA = event(group = "A", room = "A201")
        val groupB = event(group = "B", room = "A201")

        assertEquals(2, TimetableUtils.deduplicateEvents(listOf(groupA, groupB)).size)
    }

    @Test
    fun `exact duplicates collapse to one row`() {
        val events = listOf(event(group = "A"), event(group = "A"))

        assertEquals(1, TimetableUtils.deduplicateEvents(events).size)
    }

    @Test
    fun `every option the UI offers selects the session it came from`() {
        // The invariant that matters. Options are the groups as written, so each one has to select
        // its own sessions — and only its own.
        val events = listOf(
            event(group = "Y3/C/G1"),
            event(group = "Y3/C/G2"),
            event(group = "G1 + G2"),
            event(group = ""),
        )

        val offered = GroupMatcher.availableGroups(events.map { it.group })

        for (option in offered) {
            assertTrue(
                "offered '$option' but nothing matches it",
                events.any { GroupMatcher.matches(it.group, option) },
            )
        }
        assertTrue("a path is offered whole", offered.contains("Y3/C/G1"))
        val others = events.filter { it.group != "Y3/C/G1" && it.group.isNotBlank() }
        assertTrue(
            "one cohort's path must not select another cohort's sessions",
            others.none { GroupMatcher.matches(it.group, "Y3/C/G1") },
        )
    }
}
