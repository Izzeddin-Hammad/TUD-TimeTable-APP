package com.example.timetablescraper.util

import android.content.Context
import com.example.timetablescraper.CrashHandler
import com.example.timetablescraper.SyncPreferences
import com.example.timetablescraper.api.cache.TimetableDatabase
import com.example.timetablescraper.update.UpdateManager

/**
 * The in-app "erase everything".
 *
 * This app claims to collect nothing, which is only meaningful if the student can also remove what
 * *does* exist on their device. Until this existed, no single control did: the cached timetable,
 * bookmarked courses and search history each had their own delete, but the crash record, the theme
 * and its custom hue, the anchor weeks, the cached-week markers and the saved update download id
 * survived every in-app button — only Android's "Clear storage" removed them.
 *
 * Every step is best-effort and none is allowed to throw: the caller restarts straight afterwards.
 */
object LocalData {

    fun eraseEverything(context: Context) {
        // The cache database, deleted as files rather than through Room: opening it is not needed,
        // and may not even be possible when the database is what is broken.
        runCatching { TimetableDatabase.deleteFiles(context) }

        // Every SharedPreference this app has written — the crash record, bookmarks, view state,
        // theme and custom hue, and the update download id.
        runCatching { SyncPreferences.clearAllPreferences(context) }

        // …and the update bookkeeping, which lives in its own preferences file.
        runCatching { UpdateManager.clearPrefs(context) }

        // The crash marker that lives in filesDir (clearCrashFlag also drops the preference copy).
        runCatching { CrashHandler.clearCrashFlag(context) }

        // A downloaded update APK left in the app's external files directory.
        runCatching {
            val apk = UpdateManager.getApkFile(context)
            if (apk.exists()) apk.delete()
        }
    }
}
