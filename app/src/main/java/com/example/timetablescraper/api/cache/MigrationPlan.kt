package com.example.timetablescraper.api.cache

/**
 * The database migration plan expressed as data, so the invariants Room enforces can be
 * unit-tested instead of being discovered on a user's device.
 *
 * ## Why this exists
 *
 * Room refuses to build a database when a destructive-fallback start version is also the start
 * **or end** version of a registered migration. From Room 2.7.1,
 * `RoomDatabase.validateMigrationsNotRequired`:
 *
 * ```kotlin
 * for (version in migrationStartAndEndVersions)                    // {6, 7} here
 *     require(!migrationsNotRequiredFrom.contains(version)) {
 *         "Inconsistency detected. A Migration was supplied to addMigration() that has a " +
 *         "start or end version equal to a start version supplied to " +
 *         "fallbackToDestructiveMigrationFrom(). Start version is: $version"
 *     }
 * ```
 *
 * That `require` ran inside `RoomDatabase.Builder.build()` — while *creating* the database — so
 * listing version 6 as a destructive-fallback start (while `MIGRATION_6_7` was registered)
 * crashed the app on every launch, before any UI could appear, with no way past the recovery
 * screen. The numbers now live here, [conflicts] mirrors Room's rule exactly, and
 * `MigrationPlanTest` fails the build if the plan is ever inconsistent again.
 */
object MigrationPlan {

    /** Matches `@Database(version = …)`. */
    const val CURRENT_VERSION: Int = 7

    /** Every migration registered with `addMigrations`, as start → end. */
    val MIGRATIONS: List<MigrationRange> = listOf(MigrationRange(start = 6, end = 7))

    /**
     * Versions whose databases may be reset destructively rather than migrated.
     *
     * Only versions 1–5: their schemas were never exported (`exportSchema = false`) and cannot be
     * reconstructed, so no migration can be written for them. Version 6 is excluded deliberately —
     * it is the start of the registered 6 → 7 migration, and appearing in both is what Room
     * rejects.
     */
    val DESTRUCTIVE_FALLBACK_FROM: List<Int> = listOf(1, 2, 3, 4, 5)

    /** Mirrors the `dropAllTables` argument of `fallbackToDestructiveMigrationFrom`. */
    const val DESTRUCTIVE_DROP_ALL_TABLES: Boolean = true

    /** Every version a registered migration mentions, as Room collects them (starts and ends). */
    fun migratedVersions(migrations: List<MigrationRange> = MIGRATIONS): Set<Int> =
        migrations.flatMap { listOf(it.start, it.end) }.toSet()

    /**
     * Versions in [destructiveFrom] that Room would reject, sorted. An empty result means the
     * plan is valid; anything else would throw at `build()` time on a real device.
     */
    fun conflicts(
        migrations: List<MigrationRange> = MIGRATIONS,
        destructiveFrom: List<Int> = DESTRUCTIVE_FALLBACK_FROM,
    ): List<Int> = migratedVersions(migrations).intersect(destructiveFrom.toSet()).sorted()
}

/** A registered migration, as passed to `addMigrations`. */
data class MigrationRange(val start: Int, val end: Int)
