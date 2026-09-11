package com.example.timetablescraper.api

import java.util.Locale

/**
 * Version comparison for the in-app update check.
 *
 * Extracted from `UpdateChecker.isNewerThan`, which was `private` and therefore only testable by
 * reaching into it with Java reflection (`UpdateCheckerTest` did exactly that). Reflection-based
 * tests pin the *shape* of the code — they break on any harmless refactor and give no confidence
 * about behaviour. This is the same logic as a pure, public, directly testable function.
 *
 * Rules:
 *  - an optional leading `v` and any non-numeric suffix (`-debug`, `-beta.1`) are ignored, which
 *    matches the APK names the updater actually parses (`TimeTable-v1.22-debug.apk`);
 *  - missing components count as zero, so `1.2` == `1.2.0`;
 *  - anything unparseable compares equal to `0.0.0` rather than throwing, because a malformed
 *    release tag must never crash the update check on app start.
 */
object Semver {

    /** Numeric components of [version]; never throws. */
    fun components(version: String?): List<Int> {
        val cleaned = version?.trim().orEmpty().removePrefix("v").removePrefix("V")
        if (cleaned.isEmpty()) return emptyList()
        return cleaned
            .split('.', '-', '_', '+')
            .mapNotNull { part ->
                // Take the leading run of digits: "22b" -> 22, "beta" -> null
                val digits = part.takeWhile { it.isDigit() }
                digits.toIntOrNull()
            }
    }

    /** Negative when [a] < [b], zero when equal, positive when [a] > [b]. Never throws. */
    fun compare(a: String?, b: String?): Int {
        val left = components(a)
        val right = components(b)
        val size = maxOf(left.size, right.size)
        for (i in 0 until size) {
            val l = left.getOrElse(i) { 0 }
            val r = right.getOrElse(i) { 0 }
            if (l != r) return l.compareTo(r)
        }
        return 0
    }

    /** True when [candidate] is strictly newer than [current]. */
    fun isNewer(candidate: String?, current: String?): Boolean = compare(candidate, current) > 0

    /** Normalised display form, padded to at least three components, e.g. `1.22.0`. */
    fun normalise(version: String?): String {
        val parts = components(version)
        if (parts.isEmpty()) return "0.0.0"
        val padded = parts + List(maxOf(0, 3 - parts.size)) { 0 }
        return padded.joinToString(".")
    }
}
