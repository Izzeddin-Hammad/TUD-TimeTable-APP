package com.example.timetablescraper.util

/**
 * Type-tolerant readers for `SharedPreferences`.
 *
 * `SharedPreferences.getInt`/`getString`/`getBoolean`/`getLong`/`getStringSet` do **not** coerce:
 * values are stored in a plain `Map<String, Object>` and cast on read, so a key written with one
 * primitive type and read as another throws `ClassCastException`. In this app that is not a
 * theoretical concern — it is a crash on the launch path:
 *
 *  - `MainActivity` composes `SyncPreferences.getStarredCourse(context)` unguarded, so a corrupt
 *    or type-drifted `starred_identity` kills the app *during composition*, producing a crash
 *    loop that only "Clear Cache & Restart" can break;
 *  - `SettingsScreen` reads four settings unguarded in composition;
 *  - `TimetableSyncWorker` reads the sync strategy unguarded, so a bad value turns every
 *    background sync into a retry loop.
 *
 * These helpers take the `Map` from `SharedPreferences.all` (rather than the interface itself) so
 * the recovery logic is pure, unit-testable, and impossible to bypass by accident. Every reader
 * degrades to its caller's default instead of throwing, and tolerates the *legitimate* drifts —
 * `Int` where a `Long` is expected, or a numeric value stored as a `String`.
 */
object SafePrefs {

    /** The string stored at [key], or [default] when absent or of an unrelated type. */
    fun string(all: Map<String, *>, key: String, default: String? = null): String? =
        when (val raw = all[key]) {
            null -> default
            is String -> raw
            // A String-looking value is safe to accept: the key only ever holds text.
            else -> default
        }

    /** The int stored at [key], or [default]. Accepts Int/Long/Short/Byte and numeric strings. */
    fun int(all: Map<String, *>, key: String, default: Int): Int =
        when (val raw = all[key]) {
            null -> default
            is Int -> raw
            is Long -> raw.coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong()).toInt()
            is Short -> raw.toInt()
            is Byte -> raw.toInt()
            is Double -> raw.takeIf { it.isFinite() }?.toInt() ?: default
            is String -> raw.trim().toIntOrNull() ?: default
            else -> default
        }

    /** The long stored at [key], or [default]. Accepts any integral type and numeric strings. */
    fun long(all: Map<String, *>, key: String, default: Long): Long =
        when (val raw = all[key]) {
            null -> default
            is Long -> raw
            is Int -> raw.toLong()
            is Short -> raw.toLong()
            is Byte -> raw.toLong()
            is String -> raw.trim().toLongOrNull() ?: default
            else -> default
        }

    /** The float stored at [key], or [default]. Accepts any numeric type and numeric strings. */
    fun float(all: Map<String, *>, key: String, default: Float): Float =
        when (val raw = all[key]) {
            null -> default
            is Float -> raw.takeIf { it.isFinite() } ?: default
            is Double -> raw.takeIf { it.isFinite() }?.toFloat() ?: default
            is Int -> raw.toFloat()
            is Long -> raw.toFloat()
            is Short -> raw.toFloat()
            is Byte -> raw.toFloat()
            is String -> raw.trim().toFloatOrNull()?.takeIf { it.isFinite() } ?: default
            else -> default
        }

    /** The boolean stored at [key], or [default]. Accepts `"true"`/`"false"` and numbers. */
    fun boolean(all: Map<String, *>, key: String, default: Boolean): Boolean =
        when (val raw = all[key]) {
            null -> default
            is Boolean -> raw
            is String -> when (raw.trim().lowercase()) {
                "true", "1", "yes" -> true
                "false", "0", "no" -> false
                else -> default
            }
            is Number -> raw.toDouble() != 0.0
            else -> default
        }

    /**
     * The string set stored at [key], or [default].
     *
     * Always returns a **new** set. Android documents that the `Set` handed back by
     * `getStringSet` must not be mutated — mutating it corrupts the stored value in memory
     * without ever being persisted. Callers can therefore treat this result as theirs.
     */
    fun stringSet(all: Map<String, *>, key: String, default: Set<String> = emptySet()): Set<String> =
        when (val raw = all[key]) {
            null -> default.toSet()
            is Set<*> -> raw.filterIsInstance<String>().toSet()
            is Collection<*> -> raw.filterIsInstance<String>().toSet()
            is Array<*> -> raw.filterIsInstance<String>().toSet()
            else -> default.toSet()
        }
}
