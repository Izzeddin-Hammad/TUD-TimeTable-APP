package com.example.timetablescraper.api.cache

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the migration plan against the failure that crashed v1.24–v1.26 on every launch:
 * a destructive-fallback start version that is also the start or end of a registered migration.
 *
 * Room throws this from `RoomDatabase.Builder.build()`, i.e. while creating the database, so the
 * app could not start at all — and because the recovery screen restarts the app, the crash looked
 * like two buttons that did nothing. The rule is reproduced here from Room 2.7.1's
 * `validateMigrationsNotRequired`, so it can be checked without a device.
 */
class MigrationPlanTest {

    @Test
    fun `the shipped plan is consistent`() {
        // THE regression test. If this fails, every launch would crash at database creation.
        assertEquals(
            "the destructive fallback must not mention any version a migration covers",
            emptyList<Int>(),
            MigrationPlan.conflicts(),
        )
    }

    @Test
    fun `the plan that crashed the app is reported as a conflict`() {
        // v1.24–v1.26 shipped exactly this: fallback from 1..6 alongside a 6 -> 7 migration.
        val conflicts = MigrationPlan.conflicts(
            migrations = listOf(MigrationRange(6, 7)),
            destructiveFrom = listOf(1, 2, 3, 4, 5, 6),
        )

        assertEquals(listOf(6), conflicts)
    }

    @Test
    fun `a migration's end version is a conflict too, not only its start`() {
        // Room checks starts *and* ends, so 7 could never be a destructive start either.
        val conflicts = MigrationPlan.conflicts(
            migrations = listOf(MigrationRange(6, 7)),
            destructiveFrom = listOf(7),
        )

        assertEquals(listOf(7), conflicts)
    }

    @Test
    fun `unmigrated old versions may reset destructively`() {
        assertTrue(
            MigrationPlan.conflicts(
                migrations = listOf(MigrationRange(6, 7)),
                destructiveFrom = listOf(1, 2, 3, 4, 5),
            ).isEmpty(),
        )
    }

    @Test
    fun `the current version is never offered as a destructive start`() {
        // Resetting "from the current version" would be nonsense as well as a Room error.
        assertTrue(MigrationPlan.CURRENT_VERSION !in MigrationPlan.DESTRUCTIVE_FALLBACK_FROM)
    }

    @Test
    fun `the fallback covers exactly the versions below the first migration`() {
        val lowestMigratedVersion = MigrationPlan.migratedVersions().min()

        assertEquals((1 until lowestMigratedVersion).toList(), MigrationPlan.DESTRUCTIVE_FALLBACK_FROM.sorted())
        assertTrue(MigrationPlan.DESTRUCTIVE_FALLBACK_FROM.all { it < lowestMigratedVersion })
    }

    @Test
    fun `migrated versions include both ends of every migration`() {
        assertEquals(setOf(6, 7), MigrationPlan.migratedVersions(listOf(MigrationRange(6, 7))))
        // Starts and ends only — this is not a range expansion, and Room collects them the same way.
        assertEquals(
            setOf(1, 2, 3, 6, 7),
            MigrationPlan.migratedVersions(listOf(MigrationRange(1, 2), MigrationRange(2, 3), MigrationRange(6, 7))),
        )
    }

    @Test
    fun `a plan with no migrations conflicts with nothing`() {
        assertTrue(MigrationPlan.conflicts(migrations = emptyList(), destructiveFrom = listOf(1, 2, 3)).isEmpty())
    }
}
