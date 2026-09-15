package com.example.timetablescraper.api

import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * Maps an upstream timestamp to the institution's local wall clock.
 *
 * The Scientia API serialises session times in **UTC** — a 09:00 Irish lecture arrives as
 * `2026-09-14T08:00:00+00:00` — so anything a student reads must be projected into the
 * institution's zone first. Two surfaces render those times (the timetable grid via
 * [TimetableUtils.toUiEvent], the change feed via [TimetableDiff]) and they have to agree.
 *
 * This object is deliberately free of Android dependencies so the change feed can keep being
 * unit-tested on a plain JVM (see `tools/jvm-test-harness.sh`); [TimetableUtils] builds on it too,
 * so there is exactly one definition of "what time is it in Dublin".
 */
internal object DublinTime {

    /** The institution's zone, with a safe fallback if the IANA database is incomplete. */
    val ZONE: ZoneId = try {
        ZoneId.of("Europe/Dublin")
    } catch (_: Exception) {
        ZoneId.systemDefault()
    }

    private val CLOCK = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH)

    /**
     * The Dublin-local date-time an ISO-8601 timestamp denotes, or `null` when it carries no offset
     * to project from — a naive value, a bare date or garbage. Callers treat `null` as
     * "already local", which is the only safe reading and keeps malformed rows crash-proof.
     */
    fun toLocal(raw: String?): LocalDateTime? {
        val value = raw?.trim().orEmpty()
        if (value.isEmpty()) return null
        return try {
            OffsetDateTime.parse(value).atZoneSameInstant(ZONE).toLocalDateTime()
        } catch (_: Exception) {
            null
        }
    }

    /** `HH:mm` in the institution's zone, or `null` when [raw] carries no offset. */
    fun timeOfDay(raw: String?): String? = toLocal(raw)?.toLocalTime()?.format(CLOCK)

    /** `Mon`/`Tue`/… in the institution's zone, or `null` when [raw] carries no offset. */
    fun dayOfWeek(raw: String?): String? =
        toLocal(raw)?.dayOfWeek?.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)
}
