package com.example.timetablescraper.util

/**
 * Pure decision logic for the crash-recovery screen, and the on-disk format of the crash marker.
 *
 * Both pieces used to live inline in `CrashHandler`, entangled with `Context` and therefore
 * untestable — which is how the app ended up in a state where the recovery screen could be shown
 * with nothing to show and no way out.
 */
object CrashFlags {

    /**
     * Should the recovery screen be shown at launch?
     *
     * @param prefsFlag        `crash_occurred` from `crash_prefs`.
     * @param markerExists     whether the marker file exists.
     * @param markerLength     marker file size in bytes.
     * @param markerLastModified marker modification time in epoch millis.
     * @param clearedAt        when the crash flags were last cleared (0 if never).
     *
     * Why the [clearedAt] comparison matters: the marker file is best-effort, and deletion can
     * fail (read-only storage, an odd permission, or the file being recreated by a racing writer).
     * Without it, an undeletable marker means the recovery screen reappears on **every** launch
     * even after the user has cleared it — the app becomes unusable, and both recovery buttons
     * appear to "do nothing". A marker older than the last clear is stale by definition.
     */
    fun shouldShowRecovery(
        prefsFlag: Boolean,
        markerExists: Boolean,
        markerLength: Long,
        markerLastModified: Long,
        clearedAt: Long,
    ): Boolean {
        if (prefsFlag) return true
        // An empty marker means "cleared, but could not be removed" — never a crash.
        if (!markerExists || markerLength <= 0L) return false
        return markerLastModified > clearedAt
    }
}

/** Parsed contents of the crash marker file. */
data class ParsedCrash(
    val timestamp: Long,
    val message: String,
    val stacktrace: String?,
)

/**
 * The crash marker on disk: a plain-text record that survives SharedPreferences corruption.
 *
 * It is written **synchronously** during a crash, unlike the preferences copy (which used
 * `apply()`, is asynchronous, and can be lost when the process dies immediately afterwards). This
 * file is therefore the reliable copy, and the recovery screen reads it when the preferences are
 * empty — otherwise the user sees "Something went wrong" with nothing underneath and no way to
 * report what actually happened.
 */
object CrashMarker {

    fun format(timestamp: Long, message: String, stacktrace: String?): String = buildString {
        append("Crash at: ").append(timestamp).append('\n')
        append("Message: ").append(message).append('\n')
        append('\n')
        if (!stacktrace.isNullOrEmpty()) append(stacktrace)
    }

    /** Parse [format]'s output. Returns null when the text holds no usable crash record. */
    fun parse(text: String?): ParsedCrash? {
        val body = text?.trim().orEmpty()
        if (body.isEmpty()) return null

        val lines = body.lines()
        val timestamp = lines.firstOrNull { it.startsWith("Crash at: ") }
            ?.removePrefix("Crash at: ")?.trim()?.toLongOrNull()
        val messageLine = lines.firstOrNull { it.startsWith("Message: ") }
            ?.removePrefix("Message: ")?.trim()
        val structured = timestamp != null || messageLine != null
        val stacktrace = body.substringAfter("\n\n", "").trim().takeIf { it.isNotEmpty() }

        return ParsedCrash(
            timestamp = timestamp ?: 0L,
            message = messageLine?.takeIf { it.isNotEmpty() } ?: "Unknown error",
            // Unrecognised content is surfaced verbatim rather than discarded. A marker that does
            // not match the expected shape is still the only evidence of what happened, and
            // dropping it would recreate the blank recovery screen this class exists to prevent.
            stacktrace = stacktrace ?: body.takeIf { !structured },
        )
    }
}
