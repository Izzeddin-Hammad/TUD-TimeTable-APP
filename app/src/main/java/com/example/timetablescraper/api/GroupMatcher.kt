package com.example.timetablescraper.api

import java.util.Locale

/**
 * Group ("subgroup") parsing and matching — the logic that decides whether a class is
 * *your* class.
 *
 * The upstream API expresses a session's cohort in a free-text `group` field. Observed
 * variants include:
 *
 * | raw value      | meaning                                            |
 * |----------------|----------------------------------------------------|
 * | `""` / `null`  | applies to the **whole cohort** (a plenary lecture) |
 * | `"G1"`         | subgroup G1 only                                    |
 * | `"G1 + G2"`    | shared by G1 and G2 (the parser's own canonical form)|
 * | `"G1,G2"`      | the same thing, comma-separated by a different build |
 * | `" g1 "`       | case/whitespace drift                               |
 *
 * The previous implementation was an inline
 * `event.group.split("+").any { it.trim() == selectedGroup }` in the Compose layer, which had
 * two student-visible failure modes:
 *
 *  1. **Plenary classes disappeared as soon as you picked a subgroup.** A blank group yielded
 *     the single token `""`, which never equals `"G1"`, so every all-cohort lecture was filtered
 *     out — i.e. the classes that apply to *everyone* were the ones that vanished.
 *  2. **Matching was case-sensitive** (`"g1"` never matched `"G1"`) and only understood `"+"`,
 *     so comma/`&`/`;`-separated groups were treated as one literal token and never matched.
 *
 * Extracting it here makes the semantics explicit, testable, and identical everywhere.
 */
object GroupMatcher {

    /** Characters that separate group tokens across the API's observed dialects. */
    private val SEPARATORS = Regex("[+,;/&|]+")

    /**
     * Split a raw group field into a normalised set of uppercase tokens.
     * Blank, null and separator-only inputs yield an empty set —
     * meaning "no specific group", i.e. the whole cohort.
     */
    fun parse(raw: String?): Set<String> {
        val value = raw?.trim().orEmpty()
        if (value.isEmpty()) return emptySet()
        return value
            .split(SEPARATORS)
            .asSequence()
            .map { it.trim().uppercase(Locale.ROOT) }
            .filter { it.isNotEmpty() }
            .toSet()
    }

    /** True when the session is not restricted to any subgroup (a plenary class). */
    fun appliesToAll(raw: String?): Boolean = parse(raw).isEmpty()

    /** Canonical display form: tokens sorted alphabetically and joined with " + ". */
    fun format(raw: String?): String = parse(raw).sorted().joinToString(" + ")

    /**
     * Distinct group tokens across a set of raw group fields, sorted for display.
     *
     * Single source of truth for the filter options the UI offers: the tokens a student can pick
     * are produced by the same parser (and compared by the same matcher) used to filter, so an
     * offered option can never fail to match.
     */
    fun availableGroups(rawGroups: List<String?>): List<String> =
        rawGroups.flatMap { parse(it) }.distinct().sorted()

    /**
     * Should a session with [eventGroup] be shown to a student filtering on [selected]?
     *
     * @param selected the student's chosen group, or `null`/blank for "show everything".
     *
     * Rules, in the order a student expects them:
     *  1. No filter selected → show everything.
     *  2. A plenary session (blank group) → always shown, whatever the filter.
     *  3. Otherwise → shown when the two token sets intersect (case/whitespace/separator
     *     insensitive).
     */
    fun matches(eventGroup: String?, selected: String?): Boolean {
        val wanted = parse(selected)
        if (wanted.isEmpty()) return true
        val actual = parse(eventGroup)
        if (actual.isEmpty()) return true
        return actual.any { it in wanted }
    }

    /**
     * Multi-select variant: a session is shown when it applies to all cohorts, or when it
     * intersects *any* of the selected groups (union, not intersection).
     */
    fun matchesAny(eventGroup: String?, selected: Set<String>): Boolean {
        val wanted = selected
            .flatMap { parse(it) }
            .map { it.uppercase(Locale.ROOT) }
            .toSet()
        if (wanted.isEmpty()) return true
        val actual = parse(eventGroup)
        if (actual.isEmpty()) return true
        return actual.any { it in wanted }
    }
}
