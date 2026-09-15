package com.example.timetablescraper.api.cache

import com.example.timetablescraper.api.GroupMatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the cohort's round trip through the cache.
 *
 * The stored column holds the institution's own spelling and the canonical form used for matching
 * and identity is derived on read. If that derivation is ever dropped, the filter silently stops
 * matching what is on screen; if the label is ever overwritten with the canonical form, the
 * student stops seeing the cohort their timetable printed. Neither shows up as a crash, so both
 * are asserted here.
 */
class CachedEventMappingTest {

    private fun entity(group: String) = CachedEventEntity(
        courseIdentity = "identity",
        weekStart = "2025-10-06",
        fetchedAt = 0L,
        moduleCode = "CMPU3021",
        title = "Software Engineering",
        type = "Lab",
        lecturer = "Dr A. Byrne",
        room = "A214",
        start = "2025-10-07T09:00:00",
        end = "2025-10-07T11:00:00",
        group = group,
        courseName = "TU859/3 Computing",
    )

    @Test
    fun `a cohort is canonicalised without being broken into its parts`() {
        val event = entity("TU859/Y3/MLAI/G2").toApiEvent()

        assertEquals("TU859/Y3/MLAI/G2", event.groupLabel)
        // The canonical form keeps the whole name; splitting it into tokens made it a different
        // string from the one the filter and the UI use.
        assertEquals("TU859/Y3/MLAI/G2", event.group)
    }

    @Test
    fun `a cohort of several groups still canonicalises to a list`() {
        // A field naming two groups is the one shape that does get split, because there really are
        // two cohorts sharing the session; spellings and order are normalised.
        val event = entity("G2,G1").toApiEvent()

        assertEquals("G1 + G2", event.group)
        assertEquals("G2,G1", event.groupLabel)
    }

    @Test
    fun `a plenary row carries no cohort at all`() {
        val event = entity("").toApiEvent()

        assertEquals("", event.groupLabel)
        assertEquals("", event.group)
        assertTrue(GroupMatcher.appliesToAll(event.group))
    }

    @Test
    fun `the label survives every other field unchanged`() {
        val event = entity("TU859/Y3/MLAI/G2").toApiEvent()

        assertEquals("CMPU3021", event.module_code)
        assertEquals("Software Engineering", event.title)
        assertEquals("A214", event.room)
        assertEquals("2025-10-07T09:00:00", event.start)
    }
}
