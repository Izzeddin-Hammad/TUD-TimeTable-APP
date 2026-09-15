package com.example.timetablescraper.api

import org.json.JSONObject

/**
 * Extracted parser for Scientia API event JSON objects.
 * Regexes are pre-compiled once at class-load time.
 */
internal object TimetableParser {

    // ── Pre-compiled regexes ──────────────────────────────────────────

    private val CODE_REGEX = Regex("""(\w[\w\s]*?\d+)""")
    private val SEM_REGEX  = Regex("""(?i)Sem\s*\d""")
    private val BRACKET_REGEX = Regex("""\s*\(\d+\)$""")

    /**
     * A string field, treating a JSON `null` as absent.
     *
     * `optString` returns the **literal text `"null"`** for a value that is present but null (the
     * sentinel object is not a Java null, so it survives the `!= null` check and gets stringified).
     * Upstream genuinely sends `"Location": null` — 12 of the 186 sessions in one real course — and
     * the card rendered the room as "null".
     */
    private fun JSONObject.text(name: String): String =
        if (isNull(name)) "" else optString(name, "")

    /**
     * Parse a single event JSON object from the API response into an [ApiEvent].
     *
     * Event name pattern:
     *   "MODULE_CODE/Subject Name/Lec/Sem 1/A" or similar variations.
     *
     * ExtraProperties may contain Staff, Module, Class Group.
     */
    fun parseApiEvent(ev: JSONObject): ApiEvent {
        val start = ev.text("StartDateTime")
        val end   = ev.text("EndDateTime")
        val name  = ev.text("Name")
        val parts = name.split("/")

        var moduleCode = ""
        var title       = name
        var type        = ""
        // The cohort can come from two independent places: the event name
        // ("MODULE/Title/Lec/Sem 1/A") and the ExtraProperty "Class Group". They routinely
        // disagree, and Class Group is the authoritative one — it is the only source that
        // carries a compound cohort such as "G1 + G2". Both are kept here and resolved below.
        var nameGroup   = ""
        var classGroup  = ""

        if (parts.size >= 2) {
            val codeInFirst = CODE_REGEX.find(parts[0])
            if (codeInFirst != null) moduleCode = codeInFirst.value.trim()

            // Two name shapes are in live use, and the code is not always first:
            //   "CMPU H1012(X0025)/Infrastructure/Lab/Sem1"        code first, title second
            //   "Machine Learning /SPEC 9270(20253C) Lab support"  title first, code second
            // Assuming the first shape put the wrong text in both fields for the second: the card
            // read "SPEC 9270 — SPEC 9270(20253C) Lab support" instead of "SPEC 9270 — Machine
            // Learning".
            val codeInSecond = if (codeInFirst == null) CODE_REGEX.find(parts[1]) else null
            if (codeInSecond != null) {
                moduleCode = codeInSecond.value.trim()
                title = parts[0].trim()
            } else {
                title = parts[1].trim()
            }

            val semIndex = parts.indexOfLast { it.trim().matches(SEM_REGEX) }

            if (semIndex > 1) {
                type = parts[semIndex - 1].trim()
            }

            nameGroup = if (semIndex in 1 until parts.size - 1) {
                parts[semIndex + 1].trim()
            } else ""
        }

        var lecturer = ""
        val extraProps = ev.optJSONArray("ExtraProperties")
        if (extraProps != null) {
            for (k in 0 until extraProps.length()) {
                val prop = extraProps.getJSONObject(k)
                when (prop.text("Name")) {
                    "Staff" -> if (lecturer.isEmpty()) lecturer = prop.text("Value")
                    "Module" -> if (moduleCode.isEmpty()) {
                        val mc = CODE_REGEX.find(prop.text("Value"))
                        if (mc != null) moduleCode = mc.value.trim()
                    }
                    "Class Group" -> if (classGroup.isEmpty()) {
                        // Authoritative: recorded even when the name already yielded a
                        // subgroup, because the name carries only a coarse segment.
                        classGroup = prop.text("Value").trim()
                    }
                }
            }
        }

        var room = ev.text("Location")
            .replace(BRACKET_REGEX, "").trim()

        // Resolve the cohort twice over, deliberately. `group` is the canonical form that identity
        // and matching use, so "G2, G1" and "G1 + G2" stay the same event here, in the UI filter,
        // and in the diff engine. `groupLabel` keeps the institution's own spelling for display,
        // because a student needs to recognise the cohort their timetable printed.
        val rawGroup = classGroup.ifBlank { nameGroup }.trim()
        val group = GroupMatcher.format(rawGroup)

        return ApiEvent(
            module_code = moduleCode.trim(),
            title       = title.trim(),
            type        = type.trim(),
            lecturer    = lecturer.trim(),
            room        = room.trim(),
            start       = start.trim(),
            end         = end.trim(),
            group       = group,
            groupLabel  = rawGroup
        )
    }
}
