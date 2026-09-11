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
     * Parse a single event JSON object from the API response into an [ApiEvent].
     *
     * Event name pattern:
     *   "MODULE_CODE/Subject Name/Lec/Sem 1/A" or similar variations.
     *
     * ExtraProperties may contain Staff, Module, Class Group.
     */
    fun parseApiEvent(ev: JSONObject): ApiEvent {
        val start = ev.optString("StartDateTime", "")
        val end   = ev.optString("EndDateTime", "")
        val name  = ev.optString("Name", "")
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
            val codeMatch = CODE_REGEX.find(parts[0])
            if (codeMatch != null) moduleCode = codeMatch.value.trim()
            title = parts[1].trim()

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
                when (prop.optString("Name", "")) {
                    "Staff" -> if (lecturer.isEmpty()) lecturer = prop.optString("Value", "")
                    "Module" -> if (moduleCode.isEmpty()) {
                        val mc = CODE_REGEX.find(prop.optString("Value", ""))
                        if (mc != null) moduleCode = mc.value.trim()
                    }
                    "Class Group" -> if (classGroup.isEmpty()) {
                        // Authoritative: recorded even when the name already yielded a
                        // subgroup, because the name carries only a coarse segment.
                        classGroup = prop.optString("Value", "").trim()
                    }
                }
            }
        }

        var room = ev.optString("Location", "")
            .replace(BRACKET_REGEX, "").trim()

        // Resolve the cohort: Class Group wins, the name-derived segment is the fallback, and
        // both are normalised through GroupMatcher so compound cohorts ("G1+G2", "G2, G1")
        // parse identically here, in the UI filter, and in the diff engine.
        val group = GroupMatcher.format(classGroup.ifBlank { nameGroup })

        return ApiEvent(
            module_code = moduleCode.trim(),
            title       = title.trim(),
            type        = type.trim(),
            lecturer    = lecturer.trim(),
            room        = room.trim(),
            start       = start.trim(),
            end         = end.trim(),
            group       = group.trim()
        )
    }
}
