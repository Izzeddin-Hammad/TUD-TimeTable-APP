package com.example.timetablescraper.api

import android.util.Log
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/**
 * Utilities for converting API events to UI models and date calculations.
 */
object TimetableUtils {

    private const val TAG = "TimetableUtils"

    private val DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    /**
     * Dublin timezone resolved eagerly with a safe fallback.
     * If the IANA database is somehow incomplete, the system default is used
     * and a warning is logged.  This prevents [ZoneId.of] from throwing
     * [java.time.DateTimeException] on misconfigured runtimes.
     */
    @JvmStatic
    val DUBLIN_ZONE: ZoneId = try {
        ZoneId.of("Europe/Dublin")
    } catch (e: Exception) {
        Log.w(TAG, "Europe/Dublin timezone unavailable, falling back to system default", e)
        ZoneId.systemDefault()
    }

    /**
     * Convert an API event into a UI-friendly TimetableEvent.
     * Crash-proof — returns a fallback event if parsing fails.
     */
    fun toUiEvent(event: ApiEvent, weekStart: String): TimetableEvent {
        return try {
            val startTime = if (event.start.length >= 16) event.start.substring(11, 16)
            else if (event.start.length >= 5) event.start.substring(11.coerceAtMost(event.start.length))
            else "??:??"
            val endTime = if (event.end.length >= 16) event.end.substring(11, 16)
            else if (event.end.length >= 5) event.end.substring(11.coerceAtMost(event.end.length))
            else "??:??"

            val dayName = if (event.start.length >= 10) {
                try {
                    val date = LocalDate.parse(event.start.substring(0, 10))
                    date.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.ENGLISH)
                } catch (_: Exception) { "" }
            } else ""

            val dayIndex = when (dayName) {
                "Mon" -> 0; "Tue" -> 1; "Wed" -> 2; "Thu" -> 3; "Fri" -> 4
                else -> -1
            }

            TimetableEvent(
                id = event.id,
                moduleCode = event.module_code,
                title = event.title.ifBlank { "Untitled" },
                type = event.type,
                lecturer = event.lecturer.ifBlank { "Staff" },
                room = event.room.ifBlank { "TBA" },
                start = event.start,
                end = event.end,
                day = dayName,
                dayIndex = dayIndex,
                timeRange = "$startTime - $endTime",
                weekStart = weekStart,
                group = event.group
            )
        } catch (_: Exception) {
            TimetableEvent(
                id = event.id,
                moduleCode = event.module_code.ifBlank { "?" },
                title = event.title.ifBlank { "Unknown event" },
                type = "",
                lecturer = "Staff",
                room = "TBA",
                start = "",
                end = "",
                day = "",
                dayIndex = -1,
                timeRange = "??:?? - ??:??",
                weekStart = weekStart,
                group = event.group
            )
        }
    }

    fun getCurrentMonday(today: LocalDate = LocalDate.now(DUBLIN_ZONE)): LocalDate {
        return today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    }

    fun formatDayDate(monday: LocalDate, dayOffset: Int): String {
        val date = monday.plusDays(dayOffset.toLong())
        // Locale-pinned: the old formatter used the device default, so a German device rendered
        // "Okt 6" while the rest of the app (and the tests) assumed English month names.
        return date.format(DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH))
    }

    fun formatWeekRange(monday: LocalDate): String {
        val sunday = monday.plusDays(6)
        val startStr = monday.format(DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH))
        val endStr = sunday.format(DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH))
        return "$startStr – $endStr"
    }

    /**
     * All academic weeks (Mondays) that the week picker and the full-year fetch should cover.
     *
     * The teaching year runs Sep 1 → Apr 30, but students are on campus outside that window
     * (autumn resits, May/August repeat assessments, and the first weeks of a new academic year
     * before the September boundary). The current week is therefore always included even when it
     * falls outside the nominal year — previously a student opening the app in May or late August
     * had no way to reach the week they were actually in.
     */
    fun generateAcademicWeeks(today: LocalDate = LocalDate.now(DUBLIN_ZONE)): List<LocalDate> {
        val academicYearStart = if (today.monthValue >= SEPTEMBER) {
            LocalDate.of(today.year, SEPTEMBER, 1)
        } else {
            LocalDate.of(today.year - 1, SEPTEMBER, 1)
        }
        val academicYearEnd = if (today.monthValue >= SEPTEMBER) {
            LocalDate.of(today.year + 1, APRIL, 30)
        } else {
            LocalDate.of(today.year, APRIL, 30)
        }

        val weeks = mutableListOf<LocalDate>()
        var monday = academicYearStart.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        while (!monday.isAfter(academicYearEnd)) {
            weeks.add(monday)
            monday = monday.plusWeeks(1)
        }

        // Guarantee the student can always reach the week they are in.
        val currentMonday = getCurrentMonday(today)
        if (currentMonday !in weeks) {
            weeks.add(currentMonday)
            weeks.sort()
        }
        return weeks
    }

    private const val SEPTEMBER = 9
    private const val APRIL = 4

    /**
     * Deduplicate upstream rows that describe the same meeting.
     *
     * Two-stage, because the upstream expresses the same class in several ways at once:
     *
     *  1. Rows are bucketed by [EventKey.meetingKey] (module, title, type, slot, lecturer).
     *  2. Within a bucket, rows are merged only when they do **not** actively disagree about the
     *     room or the cohort. A row that merely *lacks* the room/group is folded into the row
     *     that has it (preferring the specific value), while two rows that both state different
     *     rooms or different groups are genuinely different classes and both survive.
     *
     * That resolves the conflict in the previous single-key implementation:
     *  - keying on `start|title|lecturer` silently deleted one of two parallel lab groups, and
     *    the "richest copy" score (`group.length + lecturer.length`) could then keep the copy with
     *    no group at all, making the class unreachable through the group filter;
     *  - keying on every field (including room and group) kept a *duplicate* row whenever one copy
     *    was missing metadata, so the student saw the same class twice.
     */
    fun deduplicateEvents(events: List<ApiEvent>): List<ApiEvent> {
        if (events.size <= 1) return events

        val richestFirst = compareByDescending<ApiEvent> { if (GroupMatcher.parse(it.group).isEmpty()) 0 else 1 }
            .thenByDescending { if (it.room.isBlank()) 0 else 1 }
            .thenByDescending { if (it.lecturer.isBlank()) 0 else 1 }
            .thenBy { it.start }

        return events
            .sortedWith(richestFirst)
            .groupBy { EventKey.meetingKey(it) }
            .values
            .flatMap { rows -> mergeCompatibleCopies(rows) }
            .sortedBy { it.start }
    }

    /** A cluster of rows believed to be one meeting, tracking the most specific room/cohort seen. */
    private class MeetingCopy(seed: ApiEvent) {
        private val seed = seed
        private var room: String = seed.room
        private var group: String = seed.group

        /** Rows conflict only when both state a value and the values differ. */
        fun compatibleWith(other: ApiEvent): Boolean {
            if (room.isNotBlank() && other.room.isNotBlank() && room.trim() != other.room.trim()) return false
            val mine = GroupMatcher.parse(group)
            val theirs = GroupMatcher.parse(other.group)
            if (mine.isNotEmpty() && theirs.isNotEmpty() && mine != theirs) return false
            return true
        }

        fun absorb(other: ApiEvent) {
            if (room.isBlank()) room = other.room
            if (GroupMatcher.parse(group).isEmpty()) group = other.group
        }

        fun toEvent(): ApiEvent =
            if (room == seed.room && group == seed.group) seed
            else seed.copy(room = room, group = group)
    }

    private fun mergeCompatibleCopies(rows: List<ApiEvent>): List<ApiEvent> {
        val copies = mutableListOf<MeetingCopy>()
        for (row in rows) {                      // richest first, so the seed carries the most data
            val target = copies.firstOrNull { it.compatibleWith(row) }
            if (target != null) target.absorb(row) else copies += MeetingCopy(row)
        }
        return copies.map { it.toEvent() }
    }

    /**
     * Current date in the Dublin timezone with a safe fallback.
     * Use this instead of [LocalDate.now] everywhere in the app to
     * guarantee timezone-consistent week boundaries.
     *
     * Crash-proof: returns the system-local date if the Dublin zone
     * cannot be resolved (should never happen on standard Android runtimes).
     */
    fun currentDublinDate(): LocalDate = LocalDate.now(DUBLIN_ZONE)

    /**
     * Classify academic weeks into active (has events) and empty (no events).
     *
     * @param events   All events for the course (e.g. from a full-year API fetch).
     * @param allWeeks The full list of academic Mondays from [generateAcademicWeeks].
     * @return Pair(activeKeys, emptyKeys) where each key is "yyyy-MM-dd".
     */
    fun classifyWeeks(
        events: List<ApiEvent>,
        allWeeks: List<LocalDate> = generateAcademicWeeks()
    ): Pair<Set<String>, Set<String>> {
        val activeKeys = events.mapNotNull { event ->
            try {
                LocalDate.parse(event.start.substring(0, 10))
            } catch (_: Exception) { null }
        }.mapNotNull { date ->
            allWeeks.firstOrNull { monday ->
                !date.isBefore(monday) && !date.isAfter(monday.plusDays(6))
            }
        }.map { it.format(DATE_FORMATTER) }.toSet()

        val allKeys = allWeeks.map { it.format(DATE_FORMATTER) }.toSet()
        return Pair(activeKeys, allKeys - activeKeys)
    }

    fun safeFormat(date: LocalDate, formatter: DateTimeFormatter): String {
        return try { date.format(formatter) } catch (_: Exception) { "?" }
    }
}
