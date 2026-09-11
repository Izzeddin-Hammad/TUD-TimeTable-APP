package com.example.timetablescraper.api.cache

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Method

/**
 * Checks the migration plan against **Room's own validator**, not a copy of its rules.
 *
 * `MigrationPlanTest` reproduces Room's logic for readability and fast feedback; this test calls
 * the real thing, so a change in Room's behaviour surfaces here instead of on a user's device.
 *
 * The method is `internal` in Kotlin and therefore public (with a file-facade class name) in
 * bytecode. Reflection is used deliberately and only here:
 *
 *  - the previous reflective test in this repo (`UpdateCheckerTest`) reached into *our own*
 *    private method, which pinned our implementation's shape and broke on harmless refactors —
 *    that anti-pattern was removed;
 *  - this test calls a **third-party library's** validator to check **our configuration data**
 *    against **the library's rule**. A Room upgrade that renames or removes this method turns
 *    this test red on purpose, so the check gets updated rather than silently disappearing.
 */
class MigrationPlanRoomRuleTest {

    private val roomValidator: Method by lazy {
        val facade = Class.forName("androidx.room.RoomDatabaseKt__RoomDatabaseKt")
        facade.getDeclaredMethod("validateMigrationsNotRequired", Set::class.java, Set::class.java)
            // internal in Kotlin, so the facade class/method are not public API
            .apply { isAccessible = true }
    }

    /** Runs Room's validator; throws whatever Room itself would throw at `build()` time. */
    private fun validateWithRoom(migratedVersions: Set<Int>, destructiveFrom: Set<Int>) {
        roomValidator.invoke(null, migratedVersions, destructiveFrom)
    }

    /**
     * Room's own failure for a plan, or null when Room accepts it.
     * `Method.invoke` wraps the real throwable, so unwrap it to assert on the cause.
     */
    private fun roomFailure(migratedVersions: Set<Int>, destructiveFrom: Set<Int>): Throwable? =
        runCatching { validateWithRoom(migratedVersions, destructiveFrom) }
            .exceptionOrNull()
            ?.let { if (it is java.lang.reflect.InvocationTargetException) it.cause ?: it else it }

    @Test
    fun `Room accepts the plan this app ships`() {
        // THE proof of the fix: Room's own validation of the real numbers. If this throws,
        // the database cannot be created and every launch would crash before any UI appears.
        val failure = roomFailure(
            migratedVersions = MigrationPlan.migratedVersions(),
            destructiveFrom = MigrationPlan.DESTRUCTIVE_FALLBACK_FROM.toSet(),
        )

        assertEquals("Room rejected the shipped plan: ${failure?.message}", null, failure)
    }

    @Test
    fun `Room rejects the plan that shipped in v1_24 through v1_26`() {
        // Documents the defect: 6 is covered by MIGRATION_6_7, so Room's require() fired.
        val failure = roomFailure(migratedVersions = setOf(6, 7), destructiveFrom = setOf(1, 2, 3, 4, 5, 6))

        assertTrue("expected Room's IllegalArgumentException, got $failure", failure is IllegalArgumentException)
        assertTrue(failure!!.message!!.contains("Inconsistency detected"))
        assertTrue(failure.message!!.contains("Start version is: 6"))
    }

    @Test
    fun `our own rule agrees with Room's rule on every version 1 to 8`() {
        // Keep the fast, readable test honest against the real validator.
        for (destructive in 1..8) {
            val ourVerdict = MigrationPlan.conflicts(MigrationPlan.MIGRATIONS, listOf(destructive))
            val roomRejects = roomFailure(MigrationPlan.migratedVersions(), setOf(destructive)) != null

            assertEquals(
                "disagreement with Room for destructive start version $destructive",
                roomRejects,
                ourVerdict.isNotEmpty(),
            )
        }
    }
}
