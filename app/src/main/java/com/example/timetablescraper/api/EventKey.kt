package com.example.timetablescraper.api

import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Single source of truth for how two timetable events are considered "the same session".
 *
 * Before this existed, three different components used three different, mutually
 * inconsistent keys:
 *
 *  - `TimetableUtils.deduplicateEvents` used `start|title|lecturer` (dropping group, room, type
 *    and end — so two parallel lab groups silently collapsed into one row);
 *  - `TimetableRepository.computeChanges` used the raw `Triple(module_code, start, end)`
 *    (unnormalised, so `...T09:00:00` and `...T09:00:00Z` looked like different sessions);
 *  - the UI filtered on `event.group.split("+")` with a case-sensitive comparison.
 *
 * Any divergence between these produces student-visible lies: a change that never happened, or
 * a missing class. So the key lives in exactly one place now, and is pure and unit-testable.
 *
 * **Local wall clock, not the raw digits.** A timetable session is defined in the institution's
 * local time, so the key is the session's *Dublin* wall clock. Upstream serialises in UTC, which
 * means one fixed 09:00 lecture is written `08:00Z` while Irish Summer Time is in force and `09:00Z`
 * once it is not — reading the raw digits made that same class look like a *moved* one at every DST
 * boundary. Projecting first keeps the key stable across the boundary, and two values that denote
 * the same instant now compare equal whatever offset they were written with.
 */
object EventKey {

    /**
     * Canonical wall-clock form of an ISO-8601 timestamp: fractional seconds, the `Z`
     * designator and any `±HH:MM` offset are removed, and the result is lower-cased.
     *
     * Unparseable input is returned trimmed and lower-cased rather than throwing — this runs on
     * the ingest path, where a malformed field must never take down a whole refresh.
     *
     * ```
     * "2025-10-07T09:00:00Z"        -> "2025-10-07t09:00:00"
     * "2025-10-07T09:00:00.000Z"    -> "2025-10-07t09:00:00"
     * "2025-10-07T09:00:00"         -> "2025-10-07t09:00:00"
     * "2025-10-07T09:00:00+01:00"   -> "2025-10-07t09:00:00"   (wall clock preserved)
     * "2025-10-07T10:00:00+01:00"   -> "2025-10-07t10:00:00"   (a genuinely different slot)
     * ```
     */
    /** Canonical local form: `2025-10-07T10:00:00` (lower-cased on the way out). */
    private val LOCAL_WALL_CLOCK: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss", Locale.ROOT)

    fun wallClock(raw: String?): String {
        val s = raw?.trim().orEmpty()
        if (s.isEmpty()) return ""

        // Project into the institution's zone when the value carries an offset to project from.
        //
        // Upstream sends every session in UTC, so a fixed *local* lecture is serialised an hour
        // earlier through summer time: 09:00 Dublin is 08:00Z in September and 09:00Z in December.
        // Reading the raw digits made that same lecture look like a moved class every time the
        // clocks changed, and it made the change feed report a time change that never happened.
        // Comparing the local wall clock instead is stable across the boundary — and two values
        // that denote the same instant now compare equal whatever offset they were written with.
        DublinTime.toLocal(s)?.let { return it.format(LOCAL_WALL_CLOCK).lowercase(Locale.ROOT) }

        // No offset to project from: a naive value is already local, so read it literally.
        // Drop fractional seconds first: "…:00.000Z" -> "…:00"
        var t = s.substringBefore('.')

        // Case-insensitive: wall-clock separators are seen as both 'T' and 't' upstream.
        val tPos = t.indexOfFirst { it == 'T' || it == 't' }
        if (tPos >= 0) {
            // Look for a zone designator or numeric offset only in the time portion,
            // so the '-' separators of the date itself are never touched.
            val tail = t.substring(tPos)
            val cut = tail.indexOfFirst { it == '+' || it == '-' || it == 'Z' || it == 'z' }
            if (cut > 0) t = t.substring(0, tPos + cut)
        } else {
            t = t.removeSuffix("Z").removeSuffix("z")
        }
        return t.lowercase(Locale.ROOT)
    }

    /** The time portion of a timestamp as `HH:mm`, or `??:??` when unparseable. */
    fun timeOfDay(raw: String?): String {
        val w = wallClock(raw)
        // `wallClock` lower-cases, so the separator is 't' by the time we get here.
        val tPos = w.indexOfFirst { it == 't' }
        if (tPos < 0 || w.length < tPos + 6) return "??:??"
        return w.substring(tPos + 1, tPos + 6)
    }

    /**
     * Identity of a *session slot*: module + start + end.
     *
     * This is the key used to decide "has this session changed?". It intentionally excludes
     * room/lecturer/group/type — those are the *fields* whose change we want to report as a
     * MODIFIED, not something that makes it a different session.
     */
    fun sessionKey(event: ApiEvent): String = listOf(
        event.module_code.trim().uppercase(Locale.ROOT),
        wallClock(event.start),
        wallClock(event.end),
    ).joinToString("|")

    /**
     * Identity of a session *as a thing you can point at*: module, title, wall-clock slot,
     * room, group, lecturer and type.
     *
     * Used to pair a removal with an addition so that a moved or re-keyed session is reported
     * once as "the time changed" instead of twice as "one class vanished, another appeared".
     */
    fun identity(event: ApiEvent): String = listOf(
        event.module_code.trim().uppercase(Locale.ROOT),
        event.title.trim().uppercase(Locale.ROOT),
        event.room.trim().uppercase(Locale.ROOT),
        event.group.trim().uppercase(Locale.ROOT),
        event.lecturer.trim().uppercase(Locale.ROOT),
        event.type.trim().uppercase(Locale.ROOT),
    ).joinToString("|")

    /**
     * Key identifying one *meeting*: module, title, type, wall-clock slot and lecturer.
     *
     * Deliberately excludes room and group, because the upstream frequently emits the same
     * meeting twice with one of those missing. De-duplication therefore works in two steps: rows
     * are bucketed by this key, and rows within a bucket are merged only when they do **not**
     * actively disagree (see `TimetableUtils.deduplicateEvents`). Two parallel lab groups in
     * different rooms keep both rows, while a row that merely lacks the room/group is folded into
     * the row that has it.
     */
    fun meetingKey(event: ApiEvent): String = listOf(
        wallClock(event.start),
        wallClock(event.end),
        event.module_code.trim().uppercase(Locale.ROOT),
        event.title.trim(),
        event.type.trim().lowercase(Locale.ROOT),
        event.lecturer.trim().uppercase(Locale.ROOT),
    ).joinToString("|")
}
