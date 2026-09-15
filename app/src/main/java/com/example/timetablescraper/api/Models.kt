package com.example.timetablescraper.api

import androidx.compose.runtime.Immutable

/**
 * Request body for POST /search/data
 */
data class SearchRequest(val query: String)

/**
 * A single search result from the server.
 */
@Immutable
data class SearchResult(
    val name: String,
    val programme_code: String,
    val identity: String,
    val type: String,
    val selection_id: String,
    val timetable_type_id: String
)

/**
 * Response wrapper for /search/data
 */
data class SearchResponse(
    val results: List<SearchResult>,
    val count: Int
)

/**
 * Request body for POST /timetable
 */
data class TimetableRequest(val url: String)

/**
 * A raw event as returned by the API.
 */
@Immutable
data class ApiEvent(
    val module_code: String,
    val title: String,
    val type: String,
    val lecturer: String,
    val room: String,
    val start: String,   // ISO-8601: "2025-10-07T10:00:00"
    val end: String,     // ISO-8601: "2025-10-07T12:00:00"
    /** Canonical cohort, e.g. "G1 + G2". Used for matching and for event identity. */
    val group: String = "",
    val id: Long = 0,    // Room primary key (0 for network-fresh events)
    /**
     * The cohort exactly as the institution spells it — "TU859/Y3/C/G1", "G2, G1".
     *
     * This is what the UI shows. It exists alongside [group] rather than replacing it because the
     * two jobs pull in opposite directions: identity and de-duplication must treat "G2, G1" and
     * "G1 + G2" as the *same* cohort, while a student has to see the cohort their timetable
     * actually printed. Collapsing them either way loses something a person can see.
     */
    val groupLabel: String = ""
)

/**
 * Response wrapper for /timetable
 */
data class TimetableResponse(
    val events: List<ApiEvent>,
    val source: String,
    val count: Int
)

/**
 * UI-ready event model with computed day and time range.
 */
@Immutable
data class TimetableEvent(
    val moduleCode: String,
    val title: String,
    val type: String,
    val lecturer: String,
    val room: String,
    val start: String,       // original ISO
    val end: String,         // original ISO
    val day: String,         // "Mon", "Tue", ...
    val dayIndex: Int,       // 0=Mon … 4=Fri
    val timeRange: String,   // "10:00 - 12:00"
    val weekStart: String,   // the Monday date this event belongs to
    /** Canonical cohort, e.g. "G1 + G2" — the form the filter compares against. */
    val group: String = "",
    val id: Long = 0,        // Room primary key — stable identity for LazyColumn
    /** The cohort as the institution spells it, e.g. "TU859/Y3/C/G1" — what the student sees. */
    val groupLabel: String = ""
)

/**
 * One line of a [TimetableChange]'s expanded detail.
 *
 * [from]/[to] are the values a *changed* field moved between. For an added or cancelled class only
 * one side is set — [to] for something that appeared, [from] for something that disappeared — so
 * the row reads as a single fact rather than an "A → B" that never happened.
 */
@Immutable
data class TimetableChangeDetail(
    val label: String,
    val from: String? = null,
    val to: String? = null,
)

/**
 * Describes a single change detected in the timetable after a network refresh.
 *
 * @param type         What kind of change: ADDED, REMOVED, or MODIFIED.
 * @param day          Day abbreviation ("Mon", "Tue", …).
 * @param timeRange    Human-readable time range ("10:00 - 12:00").
 * @param moduleCode   Module/course code.
 * @param title        Event title.
 * @param description  One-line description of the change (e.g. "Room: A→B"); kept as the stable,
 *                     testable summary that both the notification text and the UI can rely on.
 * @param details      The same change broken into its individual fields, so the UI can show a
 *                     compact one-line summary and reveal the specifics only when a row is
 *                     expanded instead of dumping a semicolon-joined sentence on the student.
 */
@Immutable
data class TimetableChange(
    val type: ChangeType,
    val day: String,
    val timeRange: String,
    val moduleCode: String,
    val title: String,
    val description: String,
    val details: List<TimetableChangeDetail> = emptyList()
)

enum class ChangeType { ADDED, REMOVED, MODIFIED }
