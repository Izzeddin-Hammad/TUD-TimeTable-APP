package com.example.timetablescraper.api

import java.util.Locale

/**
 * Pure change-detection engine for a course's timetable.
 *
 * Extracted from `TimetableRepository.computeChanges` (which was private, Android-coupled and
 * therefore untestable) because it is the single piece of logic that decides what students are
 * told changed. Four defects were fixed in the move:
 *
 *  1. **Many-to-one collapse.** The old code grouped both sides by
 *     `Triple(module_code, start, end)` and compared only `.first()` of each group. Two sessions
 *     sharing a slot (e.g. G1 and G2 in different rooms) meant one of them was *never inspected*
 *     (a real room change went unreported), while the ordering of the two sides differed
 *     (`oldCache` comes from `ORDER BY start ASC` with undefined tie order; `newEvents` preserves
 *     API order) and so produced phantom "Room: A → B" alerts for an unchanged timetable.
 *     Matching is now 1:1 within a slot group, over a deterministically sorted list.
 *  2. **Unnormalised keys.** `...T09:00:00` vs `...T09:00:00Z` were different sessions; that is
 *     now [EventKey.sessionKey].
 *  3. **Title changes were invisible** — `title` was never compared.
 *  4. **A moved class produced two alerts** ("removed" + "added"). A lone removal that has an
 *     identical counterpart in the added set is now reported once as a time change.
 *
 * Contract preserved from the original: an empty [previous] list means a first-ever load, and a
 * first-ever load reports no changes (nobody should be alerted about a timetable they never saw).
 */
object TimetableDiff {

    /**
     * @param previous the last known-good state (from cache); empty on a first-ever load.
     * @param incoming the freshly fetched state (already normalised by the parser).
     * @return deterministic, ordered changes: by day, then time, then module.
     */
    fun diff(previous: List<ApiEvent>, incoming: List<ApiEvent>): List<TimetableChange> {
        if (previous.isEmpty()) return emptyList()
        if (incoming.isEmpty() && previous.isEmpty()) return emptyList()

        val oldGroups = previous.sortedBy { stableOrder(it) }.groupBy { EventKey.sessionKey(it) }
        val newGroups = incoming.sortedBy { stableOrder(it) }.groupBy { EventKey.sessionKey(it) }

        val removals = mutableListOf<ApiEvent>()
        val additions = mutableListOf<ApiEvent>()
        val modifications = mutableListOf<TimetableChange>()

        // Union of keys on both sides: a key present on only one side is an add/remove,
        // a key present on both sides is compared pairwise.
        for (key in oldGroups.keys + newGroups.keys) {
            val oldList = oldGroups[key].orEmpty()
            val newList = newGroups[key].orEmpty()
            val paired = minOf(oldList.size, newList.size)

            for (i in 0 until paired) {
                val old = oldList[i]
                val new = newList[i]
                val diffs = fieldDifferences(old, new)
                if (diffs.isNotEmpty()) {
                    modifications += change(ChangeType.MODIFIED, new, diffs.joinToString("; "))
                }
            }
            // Surplus on the old side is genuinely gone; surplus on the new side is genuinely new.
            removals += oldList.drop(paired)
            additions += newList.drop(paired)
        }

        val (remainingRemovals, remainingAdditions, moves) = consolidateMoves(removals, additions)

        val changes = buildList {
            remainingRemovals.forEach { add(change(ChangeType.REMOVED, it, "Class removed")) }
            remainingAdditions.forEach { add(change(ChangeType.ADDED, it, "New class")) }
            addAll(moves)
            addAll(modifications)
        }

        return changes.sortedWith(
            compareBy<TimetableChange> { dayIndexOf(it.day) }
                .thenBy { it.timeRange }
                .thenBy { it.moduleCode }
                .thenBy { it.description }
        )
    }

    // ── internals ─────────────────────────────────────────────────────────────

    private const val REMOVED_DESCRIPTION = "Class removed"
    private const val ADDED_DESCRIPTION = "New class"

    private fun fieldDifferences(old: ApiEvent, new: ApiEvent): List<String> = buildList {
        if (old.room != new.room) add("Room: ${display(old.room)} → ${display(new.room)}")
        if (old.lecturer != new.lecturer) add("Lecturer: ${display(old.lecturer)} → ${display(new.lecturer)}")
        if (old.group != new.group) add("Group: ${display(GroupMatcher.format(old.group))} → ${display(GroupMatcher.format(new.group))}")
        if (old.type != new.type) add("Type: ${display(old.type)} → ${display(new.type)}")
        if (old.title != new.title) add("Title: ${display(old.title)} → ${display(new.title)}")
    }

    private fun display(value: String?): String = value?.takeIf { it.isNotBlank() } ?: "—"

    private fun change(type: ChangeType, event: ApiEvent, description: String) = TimetableChange(
        type = type,
        day = dayOf(event.start),
        timeRange = "${EventKey.timeOfDay(event.start)} - ${EventKey.timeOfDay(event.end)}",
        moduleCode = event.module_code.ifBlank { "?" },
        title = event.title.ifBlank { "Untitled" },
        description = description,
    )

    /**
     * Pair a lone removal with a lone addition of the same session identity so that a moved
     * class is one alert ("Time: 10:00 - 11:00 → 11:00 - 12:00"), not two.
     * Anything that cannot be paired 1:1 is left as a genuine add/remove.
     */
    private fun consolidateMoves(
        removals: List<ApiEvent>,
        additions: List<ApiEvent>,
    ): Triple<List<ApiEvent>, List<ApiEvent>, List<TimetableChange>> {
        if (removals.isEmpty() || additions.isEmpty()) {
            return Triple(removals, additions, emptyList())
        }

        val removalsByIdentity = removals.groupBy { EventKey.identity(it) }
        val additionsByIdentity = additions.groupBy { EventKey.identity(it) }
        val consumedRemovals = mutableSetOf<ApiEvent>()
        val consumedAdditions = mutableSetOf<ApiEvent>()
        val moves = mutableListOf<TimetableChange>()

        for ((identity, oldList) in removalsByIdentity) {
            val newList = additionsByIdentity[identity] ?: continue
            val pairs = minOf(oldList.size, newList.size)
            for (i in 0 until pairs) {
                val old = oldList[i]
                val new = newList[i]
                consumedRemovals += old
                consumedAdditions += new
                moves += change(
                    ChangeType.MODIFIED,
                    new,
                    "Time: ${EventKey.timeOfDay(old.start)} - ${EventKey.timeOfDay(old.end)}" +
                        " → ${EventKey.timeOfDay(new.start)} - ${EventKey.timeOfDay(new.end)}",
                )
            }
        }

        return Triple(
            removals.filterNot { it in consumedRemovals },
            additions.filterNot { it in consumedAdditions },
            moves,
        )
    }

    /** Deterministic ordering so that equal inputs always produce equal outputs. */
    private fun stableOrder(event: ApiEvent): String = listOf(
        EventKey.wallClock(event.start),
        event.room.trim().uppercase(Locale.ROOT),
        GroupMatcher.parse(event.group).sorted().joinToString("+"),
        event.lecturer.trim().uppercase(Locale.ROOT),
        event.type.trim().uppercase(Locale.ROOT),
        event.module_code.trim().uppercase(Locale.ROOT),
    ).joinToString("|")

    private fun dayOf(start: String): String = try {
        val date = java.time.LocalDate.parse(EventKey.wallClock(start).substring(0, 10))
        date.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, Locale.ENGLISH)
    } catch (_: Exception) {
        "?"
    }

    private fun dayIndexOf(day: String): Int = when (day) {
        "Mon" -> 0; "Tue" -> 1; "Wed" -> 2; "Thu" -> 3; "Fri" -> 4; "Sat" -> 5; "Sun" -> 6
        else -> 7
    }
}
