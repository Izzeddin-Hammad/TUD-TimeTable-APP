package com.example.timetablescraper.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Edge cases for the shared timetable utilities: the academic-week window, calendar formatting,
 * and the group options the UI offers. Kept separate from `TimetableUtilsTest` so the
 * regression tests for previously-broken behaviour are easy to find.
 */
class TimetableUtilsEdgeCaseTest {

    // ── the week picker must always contain the week the student is in ─────────

    @Test
    fun `a week in May is reachable`() {
        // Regression: the academic window was hardcoded Sep 1 -> Apr 30, so a student opening the
        // app during May resits had no way to reach the week they were actually in.
        val today = LocalDate.of(2026, 5, 11)

        val weeks = TimetableUtils.generateAcademicWeeks(today)

        assertTrue("expected the current week in $weeks", weeks.contains(LocalDate.of(2026, 5, 11)))
        assertEquals(LocalDate.of(2026, 5, 11), weeks.last())
    }

    @Test
    fun `a week in late August is reachable`() {
        val today = LocalDate.of(2026, 8, 31)

        val weeks = TimetableUtils.generateAcademicWeeks(today)

        assertTrue(weeks.contains(LocalDate.of(2026, 8, 31)))
        assertEquals(LocalDate.of(2026, 8, 31), weeks.last())
    }

    @Test
    fun `the teaching year still starts on the first Monday of September`() {
        val weeks = TimetableUtils.generateAcademicWeeks(LocalDate.of(2025, 10, 6))

        assertEquals(LocalDate.of(2025, 9, 1), weeks.first())   // 2025-09-01 is a Monday
        assertTrue(weeks.contains(LocalDate.of(2025, 10, 6)))
    }

    @Test
    fun `weeks are Mondays in ascending order with no duplicates`() {
        val weeks = TimetableUtils.generateAcademicWeeks(LocalDate.of(2026, 6, 1))

        assertEquals(weeks.sorted(), weeks)
        assertEquals(weeks.distinct(), weeks)
        assertTrue(weeks.all { it.dayOfWeek == java.time.DayOfWeek.MONDAY })
    }

    @Test
    fun `the window still ends in April for a week inside the teaching year`() {
        val weeks = TimetableUtils.generateAcademicWeeks(LocalDate.of(2025, 11, 3))

        assertTrue(weeks.last().monthValue <= 4 || weeks.last().year == 2026)
        assertEquals(LocalDate.of(2026, 4, 27), weeks.last())   // last Monday on/before Apr 30
    }

    // ── calendar formatting is locale independent ──────────────────────────────

    @Test
    fun `day labels are always English regardless of device locale`() {
        val localeBefore = java.util.Locale.getDefault()
        try {
            // A German device used to render month names in German, inconsistent with the rest
            // of the app and with the change-notification strings.
            java.util.Locale.setDefault(java.util.Locale.GERMANY)

            val monday = LocalDate.of(2025, 10, 6)

            assertEquals("Oct 6", TimetableUtils.formatDayDate(monday, 0))
            assertEquals("Oct 7", TimetableUtils.formatDayDate(monday, 1))
        } finally {
            java.util.Locale.setDefault(localeBefore)
        }
    }

    @Test
    fun `the week range label carries the year so a January week is unambiguous`() {
        val label = TimetableUtils.formatWeekRange(LocalDate.of(2025, 12, 29))

        assertEquals("Dec 29 – Jan 4, 2026", label)
    }

    @Test
    fun `the current Monday is the previous or same Monday`() {
        assertEquals(LocalDate.of(2025, 10, 6), TimetableUtils.getCurrentMonday(LocalDate.of(2025, 10, 6)))
        assertEquals(LocalDate.of(2025, 10, 6), TimetableUtils.getCurrentMonday(LocalDate.of(2025, 10, 9)))
        assertEquals(LocalDate.of(2025, 10, 6), TimetableUtils.getCurrentMonday(LocalDate.of(2025, 10, 12)))
    }

    @Test
    fun `week boundaries do not shift across a DST transition`() {
        // 2025-10-26 is the Irish clocks-go-back date; the Monday on either side must be exact.
        assertEquals(LocalDate.of(2025, 10, 20), TimetableUtils.getCurrentMonday(LocalDate.of(2025, 10, 25)))
        assertEquals(LocalDate.of(2025, 10, 27), TimetableUtils.getCurrentMonday(LocalDate.of(2025, 10, 28)))
    }

    // ── group options offered by the UI ────────────────────────────────────────

    @Test
    fun `available groups are distinct sorted tokens with plenary rows excluded`() {
        val raw = listOf("", "G1", "G2", "G1", null, "G1 + G2", "g2")

        val groups = GroupMatcher.availableGroups(raw)

        assertEquals(listOf("G1", "G2"), groups)
    }

    @Test
    fun `every offered group option is matchable`() {
        // If the UI offers "G1" it must be able to select the session that produced it.
        val raw = listOf("G1", "Y3/C/G2", "", "A,B")

        for (group in GroupMatcher.availableGroups(raw)) {
            assertTrue("offered '$group' but it matches nothing", GroupMatcher.matches(group, group))
        }
    }

    // ── saved-course display names (the duplicated-cohort defect) ───────────────

    @Test
    fun `a name that already carries its cohort is not given a second one`() {
        // The real value that was on screen on the emulator:
        // "TU859/3 Computing (General Entry)  (Part-Time Tallaght) (MLAI/G2) (MLAI/G2)"
        val upstream = "TU859/3 Computing (General Entry)  (Part-Time Tallaght) (MLAI/G2)"

        val name = TimetableUtils.savedCourseName(upstream, "TU859/MLAI/G2")

        assertEquals("TU859/3 Computing (General Entry) (Part-Time Tallaght) (MLAI/G2)", name)
        assertEquals(1, Regex("""\(MLAI/G2\)""").findAll(name).count())
    }

    @Test
    fun `a name without the cohort gains it exactly once`() {
        val name = TimetableUtils.savedCourseName("TU859/3 Computing", "TU859/MLAI/G2")

        assertEquals("TU859/3 Computing (MLAI/G2)", name)
    }

    @Test
    fun `no group means no suffix`() {
        assertEquals("TU859/3 Computing", TimetableUtils.savedCourseName("TU859/3 Computing", null))
        assertEquals("TU859/3 Computing", TimetableUtils.savedCourseName("TU859/3 Computing", ""))
        assertEquals("TU859/3 Computing", TimetableUtils.savedCourseName("TU859/3 Computing", "TU859"))
    }

    @Test
    fun `a stale duplicated suffix is repaired on read`() {
        // Names written by the previous implementation are persisted, so they must heal rather
        // than require the student to re-star the course.
        val stale = "TU859/3 Computing (Part-Time Tallaght) (MLAI/G2) (MLAI/G2)"

        assertEquals(
            "TU859/3 Computing (Part-Time Tallaght) (MLAI/G2)",
            TimetableUtils.savedCourseName(stale),
        )
    }

    @Test
    fun `runs of whitespace collapse`() {
        assertEquals(
            "TU859/3 Computing (General Entry) (Part-Time)",
            TimetableUtils.savedCourseName("  TU859/3   Computing  (General Entry)   (Part-Time)  "),
        )
    }

    @Test
    fun `different adjacent parenthetical parts are left alone`() {
        // The repair must target a *repeated* suffix, not any two parentheses.
        val name = TimetableUtils.savedCourseName("TU859 (Part-Time) (Tallaght)")

        assertEquals("TU859 (Part-Time) (Tallaght)", name)
    }

    @Test
    fun `classification places an event in the week containing its start date`() {
        val monday = LocalDate.of(2025, 9, 8)
        val events = listOf(
            ApiEvent("C1", "T", "Lec", "S", "R", "2025-09-08T09:00:00Z", "2025-09-08T10:00:00Z", ""),
            ApiEvent("C2", "T", "Lec", "S", "R", "2025-09-14T09:00:00Z", "2025-09-14T10:00:00Z", ""), // Sunday
        )

        val (active, empty) = TimetableUtils.classifyWeeks(events, listOf(monday))

        assertEquals(setOf("2025-09-08"), active)
        assertTrue(empty.isEmpty())
    }
}
