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
     * Characters that separate *several* groups named in one field (`"G1 + G2"`).
     *
     * `/` is deliberately absent: it joins the parts of a single hierarchical group name such as
     * `TU859/Y3/MLAI/G2`, so splitting on it makes two unrelated cohorts ("TU859/MLAI/G2" and
     * "TU859/CS/G2") share most of their parts and look related when they are not.
     */
    private val GROUP_LIST_SEPARATORS = Regex("[+,;&|]+")

    /**
     * The groups a raw field names, each exactly as written apart from surrounding whitespace.
     *
     * An empty list means the field names no group at all — a plenary session.
     */
    fun entries(raw: String?): List<String> =
        raw.orEmpty()
            .split(GROUP_LIST_SEPARATORS)
            .map { it.trim() }
            .filter { it.isNotEmpty() }

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

    /**
     * Canonical form of a group field: each named group uppercased and sorted, joined with " + ".
     *
     * A hierarchical name stays whole. "TU859/Y3/MLAI/G2" is one group, so it is not broken into
     * `TU859`/`Y3`/`MLAI`/`G2` — doing that made this form unrecognisable to [matches] and to the
     * UI, which shows the name as written. Only the separators that list *several* groups split.
     */
    fun format(raw: String?): String =
        entries(raw).map { it.uppercase(Locale.ROOT) }.sorted().joinToString(" + ")

    /**
     * The distinct cohorts across a set of group fields, in the institution's own spelling.
     *
     * Single source of truth for the filter options the UI offers. Three things matter:
     *
     *  - **The whole cohort is the unit.** These used to be the individual tokens, so a timetable
     *    whose cohorts were `TU859/Y3/MLAI/G2` offered "TU859", "Y3", "MLAI" and "G2" as four
     *    separate choices, and never showed the cohort the student actually recognises.
     *  - **A list of cohorts becomes one option each — never a merged "A + B" entry.** A shared
     *    session's field names several cohorts at once (`"TU859/Y1/G1 + TU859/Y1/G2"`, the shape
     *    the API actually sends). Offering that whole string as a single choice read as though
     *    `TU859/Y1/G1` had "other groups added as a +", which is not a cohort anyone is enrolled
     *    in. [entries] splits the list into its members, so the picker offers `TU859/Y1/G1` and
     *    `TU859/Y1/G2` separately; each still selects the shared session, because [matches] parses
     *    the row's field the same way. The `/`-joined parts of one hierarchical name stay whole.
     *  - **De-duplication happens on the canonical form.** Listing spellings verbatim would offer
     *    "G2" and "g2" as different options; keying on the uppercase form collapses those while the
     *    returned label keeps whichever spelling the timetable published first.
     *
     * Because [matches] parses whatever it is handed, an option produced here always matches the
     * rows it came from.
     */
    fun availableGroups(rawGroups: List<String?>): List<String> =
        rawGroups
            .flatMap { entries(it) }
            .distinctBy { it.uppercase(Locale.ROOT) }
            .sorted()

    /**
     * Should a session with [eventGroup] be shown to a student filtering on [selected]?
     *
     * @param selected the student's chosen group, or `null`/blank for "show everything".
     *
     * Rules, in the order a student expects them:
     *  1. No filter selected → show everything.
     *  2. A plenary session (blank group) → always shown, whatever the filter.
     *  3. Otherwise → shown when the session names the selected group *as written*. Coastline
     *     paths are whole names: "TU859/MLAI/G2" is one group, not the tokens TU859, MLAI and G2,
     *     so it does not match "TU859/CS/G2". A field that genuinely names several groups
     *     ("G1 + G2", the shape a shared session takes) still matches either of them.
     *
     * Comparison ignores case and surrounding whitespace, because upstream is loose about both
     * (" g1 " for "G1"), but never treats two different names as one.
     */
    fun matches(eventGroup: String?, selected: String?): Boolean {
        val wanted = entries(selected)
        if (wanted.isEmpty()) return true
        val actual = entries(eventGroup)
        if (actual.isEmpty()) return true
        return actual.any { a -> wanted.any { a.equals(it, ignoreCase = true) } }
    }

    /**
     * Multi-select variant: a session is shown when it applies to all cohorts, or when it names
     * *any* of the selected groups (union, not intersection).
     */
    fun matchesAny(eventGroup: String?, selected: Set<String>): Boolean {
        val wanted = selected.flatMap { entries(it) }
        if (wanted.isEmpty()) return true
        val actual = entries(eventGroup)
        if (actual.isEmpty()) return true
        return actual.any { a -> wanted.any { a.equals(it, ignoreCase = true) } }
    }
}
