package com.example.timetablescraper

import android.content.Context
import android.util.Log
import com.example.timetablescraper.util.CrashFlags
import com.example.timetablescraper.util.CrashMarker
import com.example.timetablescraper.util.ParsedCrash
import com.example.timetablescraper.util.SafePrefs
import java.io.File

/**
 * Global uncaught exception handler that persists crash info for recovery
 * on next launch. Registered in [TimetableApplication.onCreate].
 *
 * ## Architecture
 *
 * ```
 * ┌─ Thread.uncaughtException ─────────────────────────────────────┐
 * │  1. Write crash info → marker file + SharedPreferences         │
 * │  2. Chain to previous handler (OS kills the process)           │
 * └───────────────────────────────────────────────────────────────┘
 *                          ↓  (process restarts)
 * ┌─ MainActivity.onCreate ────────────────────────────────────────┐
 * │  CrashHandler.hasCrashOccurred() == true                       │
 * │    → FatalErrorScreen("Something went wrong")                  │
 * │      → "Clear Cache & Restart" clears the timetable cache and  │
 * │        the cache-derived preferences, then restarts            │
 * └───────────────────────────────────────────────────────────────┘
 * ```
 *
 * This is the outermost safety net.  Inner layers (coroutine exception
 * handlers, try-catch in LaunchedEffect, runCatching at scope roots)
 * prevent most crashes from reaching this handler at all.
 *
 * ## Durability rules (each one learned from a real defect)
 *
 * 1. The **marker file** is written first and synchronously. It is the copy that survives.
 * 2. The preferences copy uses `commit()`, never `apply()`: the process is about to die, so an
 *    asynchronous write can be lost — which produced a recovery screen with nothing on it.
 * 3. [getCrashInfo] falls back to the marker when the preferences copy is missing, so the screen
 *    can always say *what* went wrong instead of showing an empty panel.
 * 4. Clearing records a timestamp, so a marker that cannot be deleted is recognised as stale
 *    rather than trapping the user on the recovery screen on every launch.
 */
class CrashHandler private constructor(
    private val context: Context
) : Thread.UncaughtExceptionHandler {

    private val previousHandler: Thread.UncaughtExceptionHandler? =
        Thread.getDefaultUncaughtExceptionHandler()

    init {
        if (previousHandler is CrashHandler) {
            throw IllegalStateException("CrashHandler already registered — call register() once")
        }
    }

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        try {
            Log.e(TAG, "Uncaught exception on thread: ${thread.name}", throwable)
            persistCrash(throwable)
        } catch (_: Exception) {
            // If the persister itself fails, there is nothing more we can do
        }
        // Chain to the OS default handler (which kills the process)
        previousHandler?.uncaughtException(thread, throwable)
    }

    private fun persistCrash(throwable: Throwable) {
        val timestamp = System.currentTimeMillis()
        val message = throwable.message ?: "Unknown error"
        val stacktrace = throwable.stackTraceToString()

        // 1. Marker file — synchronous, and the copy that survives an abrupt process death.
        try {
            File(context.filesDir, CRASH_MARKER_FILE)
                .writeText(CrashMarker.format(timestamp, message, stacktrace))
        } catch (_: Exception) {
            // Best effort; the preferences copy below is the other half of the redundancy.
        }

        // 2. SharedPreferences — committed synchronously, because the process is about to be
        //    killed and an asynchronous write would simply never land.
        try {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_CRASH_OCCURRED, true)
                .putString(KEY_CRASH_MESSAGE, message)
                .putString(KEY_CRASH_STACKTRACE, stacktrace)
                .putLong(KEY_CRASH_TIMESTAMP, timestamp)
                .commit()
        } catch (_: Exception) {
            // Nothing further we can do from inside a crash handler.
        }
    }

    companion object {
        private const val TAG = "CrashHandler"
        private const val PREFS_NAME = "crash_prefs"
        private const val KEY_CRASH_OCCURRED = "crash_occurred"
        private const val KEY_CRASH_MESSAGE = "crash_message"
        private const val KEY_CRASH_STACKTRACE = "crash_stacktrace"
        private const val KEY_CRASH_TIMESTAMP = "crash_timestamp"
        private const val KEY_CRASH_CLEARED_AT = "crash_cleared_at"
        private const val CRASH_MARKER_FILE = ".crash_marker"

        /** Register the global handler. Call once from [TimetableApplication.onCreate]. */
        @JvmStatic
        fun register(context: Context) {
            val handler = CrashHandler(context.applicationContext)
            Thread.setDefaultUncaughtExceptionHandler(handler)
            Log.i(TAG, "Global uncaught exception handler registered")
        }

        /**
         * Read the persisted crash info, or null if no crash is recorded.
         *
         * Reads the preferences copy first, then falls back to the marker file. The fallback is
         * what stops the recovery screen from being blank: prefs written by an older build used
         * `apply()` and can be missing entirely even though the crash happened.
         */
        @JvmStatic
        fun getCrashInfo(context: Context): CrashInfo? {
            if (!hasCrashOccurred(context)) return null

            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).all
            val message = SafePrefs.string(prefs, KEY_CRASH_MESSAGE)
            val stacktrace = SafePrefs.string(prefs, KEY_CRASH_STACKTRACE)
            val timestamp = SafePrefs.long(prefs, KEY_CRASH_TIMESTAMP, 0L)

            if (message.isNullOrBlank() && stacktrace.isNullOrBlank()) {
                readMarker(context)?.let { fromMarker ->
                    return CrashInfo(
                        message = fromMarker.message,
                        stacktrace = fromMarker.stacktrace,
                        timestamp = fromMarker.timestamp.takeIf { it > 0L } ?: timestamp,
                    )
                }
            }

            return CrashInfo(
                message = message ?: "Unknown error",
                stacktrace = stacktrace,
                timestamp = timestamp,
            )
        }

        /**
         * Whether a previous session ended in a crash.
         *
         * Delegates the decision to [CrashFlags.shouldShowRecovery], which also treats a marker
         * older than the last clear as stale — that is what stops an undeletable marker from
         * bringing the recovery screen back on every launch.
         */
        @JvmStatic
        fun hasCrashOccurred(context: Context): Boolean {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val marker = File(context.filesDir, CRASH_MARKER_FILE)
            return CrashFlags.shouldShowRecovery(
                prefsFlag = SafePrefs.boolean(prefs.all, KEY_CRASH_OCCURRED, false),
                markerExists = marker.exists(),
                markerLength = runCatching { marker.length() }.getOrDefault(0L),
                markerLastModified = runCatching { marker.lastModified() }.getOrDefault(0L),
                clearedAt = SafePrefs.long(prefs.all, KEY_CRASH_CLEARED_AT, 0L),
            )
        }

        /**
         * Clear the crash flags after successful recovery or restart.
         *
         * Order matters: the preferences are cleared and the clear is *timestamped* before the
         * marker is touched. If the marker cannot be deleted, the timestamp makes it stale, so
         * `hasCrashOccurred` stops reporting a crash either way — the user is never stuck.
         */
        @JvmStatic
        fun clearCrashFlag(context: Context) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

            val cleared = runCatching { prefs.edit().clear().commit() }.getOrDefault(false)
            if (!cleared) Log.w(TAG, "Crash preferences could not be cleared")

            runCatching {
                prefs.edit().putLong(KEY_CRASH_CLEARED_AT, System.currentTimeMillis()).commit()
            }.onFailure { Log.w(TAG, "Could not record the crash-clear timestamp", it) }

            val marker = File(context.filesDir, CRASH_MARKER_FILE)
            if (!marker.exists()) return

            val deleted = runCatching { marker.delete() }.getOrDefault(false)
            if (!deleted) {
                // Truncate as a secondary signal, and rely on the timestamp above so this cannot
                // resurrect the recovery screen.
                runCatching { marker.writeText("") }
                    .onFailure { Log.w(TAG, "Crash marker could not be truncated", it) }
                Log.w(TAG, "Crash marker could not be deleted; it is now ignored as stale")
            }
        }

        /** The marker file's parsed contents, or null when it holds no usable record. */
        private fun readMarker(context: Context): ParsedCrash? = runCatching {
            val marker = File(context.filesDir, CRASH_MARKER_FILE)
            if (!marker.exists() || marker.length() <= 0L) return null
            CrashMarker.parse(marker.readText())
        }.getOrNull()
    }

    data class CrashInfo(
        val message: String?,
        val stacktrace: String?,
        val timestamp: Long
    )
}
