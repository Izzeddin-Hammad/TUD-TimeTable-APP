package com.example.timetablescraper

import android.app.Application
import com.example.timetablescraper.api.Institution
import com.example.timetablescraper.api.InstitutionConfiguration
import com.example.timetablescraper.api.SyncStrategy
import com.example.timetablescraper.api.TimetableApiService
import com.example.timetablescraper.api.TimetableRepository
import com.example.timetablescraper.api.cache.TimetableDatabase
import com.example.timetablescraper.worker.SyncNotificationManager
import com.example.timetablescraper.worker.TimetableSyncWorker

/**
 * Application-level singleton that owns the database, repository,
 * and schedules the background sync worker.
 *
 * All configuration is injected dynamically — zero hardcoded values.
 * The [InstitutionConfiguration] can be swapped out to support any
 * university running Scientia Publish.
 */
class TimetableApplication : Application() {

    /** Lazily-initialized Room database (thread-safe singleton). */
    val database: TimetableDatabase by lazy {
        TimetableDatabase.getInstance(this)
    }

    /** The active institution configuration (injectable). */
    val institutionConfig: InstitutionConfiguration by lazy {
        Institution.DEFAULT
    }

    /**
     * The API service. There is deliberately exactly **one** instance app-wide — this is the same
     * object the screens and the sync worker reach as [TimetableApiService.DEFAULT].
     *
     * It used to build a *second* service from the injected config while everything else called the
     * `DEFAULT` singleton. Each instance constructs its own OkHttp client and its own rate-limit
     * token bucket, so the documented "5 requests / 10 s" limit was really 10/10 s and the
     * connection pool was duplicated. Supporting a different institution would need `DEFAULT`'s
     * lazy initialiser to read [institutionConfig], rather than adding a second instance back here.
     */
    val apiService: TimetableApiService by lazy {
        TimetableApiService.DEFAULT
    }

    /**
     * Repository that wraps cache + API with a sync strategy.
     *
     * The strategy is read from [SyncPreferences] on first access so
     * user preferences are respected. If the user hasn't configured one,
     * [SyncStrategy.Daily] is the safe default.
     */
    val repository: TimetableRepository by lazy {
        val strategy = runCatching {
            SyncPreferences.getSyncStrategy(this)
        }.getOrDefault(SyncStrategy.Daily)

        TimetableRepository(
            database = database,
            apiService = apiService,
            syncStrategy = strategy
        )
    }

    override fun onCreate() {
        super.onCreate()
        instance = this

        // ── Register the outermost crash safety net ─────────────────────
        CrashHandler.register(this)

        // Create notification channel for sync progress/completion
        SyncNotificationManager.createChannel(this)

        // Schedule periodic background sync based on user preferences.
        // Idempotent — calling again updates the existing schedule.
        TimetableSyncWorker.schedule(this)
    }

    companion object {
        lateinit var instance: TimetableApplication
            private set
    }
}
