package com.example.timetablescraper.api.cache

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.timetablescraper.api.ApiEvent
import com.example.timetablescraper.api.GroupMatcher

/**
 * Room entity that stores one cached timetable event.
 *
 * The composite lookup key is [courseIdentity] + [weekStart] (the Monday date).
 * A single fetch stores all events for that course+week together.
 */
@Entity(
    tableName = "cached_events",
    indices = [
        Index(value = ["courseIdentity", "weekStart"]),
        Index(value = ["fetchedAt"])  // speeds up pruneOlderThan() cache cleanup
    ]
)
data class CachedEventEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** The course/programme identity string from the search result */
    val courseIdentity: String,

    /** The Monday date of the week, as "yyyy-MM-dd" */
    val weekStart: String,

    /** When this record was written (epoch millis) */
    val fetchedAt: Long,

    // ── Event fields ─────────────────────────────────────────────────

    val moduleCode: String,
    val title: String,
    val type: String,
    val lecturer: String,
    val room: String,

    /** ISO-8601 start, e.g. "2025-10-07T10:00:00" */
    val start: String,

    /** ISO-8601 end, e.g. "2025-10-07T12:00:00" */
    val end: String,

    /**
     * The cohort as the institution spells it, e.g. "TU859/Y3/C/G1".
     *
     * The canonical form used for matching and identity is derived on read by [toApiEvent], so a
     * cached row can never drift from the spelling the timetable published.
     */
    val group: String = "",

    /** Human-readable course name from the search result, e.g. "TU859/Computer Science" */
    val courseName: String = ""
)

/**
 * Rebuild the API model from a cached row.
 *
 * [CachedEventEntity.group] holds the institution's own spelling; [ApiEvent.group] has to be the
 * canonical form, so it is derived here rather than stored a second time. Rows written by an
 * earlier version hold the canonical form already, and running that back through
 * [GroupMatcher.format] is a no-op — so an existing cache keeps working with no migration.
 */
internal fun CachedEventEntity.toApiEvent(): ApiEvent = ApiEvent(
    id = id,
    module_code = moduleCode,
    title = title,
    type = type,
    lecturer = lecturer,
    room = room,
    start = start,
    end = end,
    group = GroupMatcher.format(group),
    groupLabel = group
)
