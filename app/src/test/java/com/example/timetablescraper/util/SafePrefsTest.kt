package com.example.timetablescraper.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Storage-drift tests. Every case here is a value the app could plausibly find in
 * `SharedPreferences` and that used to throw `ClassCastException` on read — a crash on the
 * launch path, where the only escape used to be wiping app data.
 */
class SafePrefsTest {

    @Test
    fun `missing keys return the caller's default`() {
        val all = emptyMap<String, Any>()

        assertEquals("fallback", SafePrefs.string(all, "k", "fallback"))
        assertEquals(7, SafePrefs.int(all, "k", 7))
        assertEquals(9L, SafePrefs.long(all, "k", 9L))
        assertTrue(SafePrefs.boolean(all, "k", true))
        assertEquals(setOf("a"), SafePrefs.stringSet(all, "k", setOf("a")))
        assertEquals(null, SafePrefs.string(all, "k"))
    }

    @Test
    fun `a string key holding a non-string does not throw`() {
        // Previously: ClassCastException inside composition on the boot path.
        val all = mapOf<String, Any>("starred_identity" to 42)

        assertEquals(null, SafePrefs.string(all, "starred_identity"))
        assertEquals("default", SafePrefs.string(all, "starred_identity", "default"))
    }

    @Test
    fun `an int key holding a string or long does not throw`() {
        val all = mapOf<String, Any>("value" to "36", "big" to 5_000_000_000L, "junk" to "not a number")

        assertEquals(36, SafePrefs.int(all, "value", 1))
        assertEquals(Int.MAX_VALUE, SafePrefs.int(all, "big", 1))   // clamped, no overflow wrap
        assertEquals(1, SafePrefs.int(all, "junk", 1))
    }

    @Test
    fun `a long key accepts an int so interval changes do not crash`() {
        val all = mapOf<String, Any>("last_pull_refresh" to 1_757_923_200)

        assertEquals(1_757_923_200L, SafePrefs.long(all, "last_pull_refresh", 0L))
    }

    @Test
    fun `a boolean key tolerates strings and numbers`() {
        val all = mapOf<String, Any>(
            "a" to "true", "b" to "FALSE", "c" to "1", "d" to 0, "e" to 3, "f" to "banana",
        )

        assertTrue(SafePrefs.boolean(all, "a", false))
        assertFalse(SafePrefs.boolean(all, "b", true))
        assertTrue(SafePrefs.boolean(all, "c", false))
        assertFalse(SafePrefs.boolean(all, "d", true))
        assertTrue(SafePrefs.boolean(all, "e", false))
        assertTrue(SafePrefs.boolean(all, "f", true))   // unparseable -> default
    }

    @Test
    fun `a string set key holding a plain string degrades to the default`() {
        val all = mapOf<String, Any>("active_weeks" to "2025-09-15")

        assertEquals(emptySet<String>(), SafePrefs.stringSet(all, "active_weeks"))
        assertEquals(setOf("x"), SafePrefs.stringSet(all, "active_weeks", setOf("x")))
    }

    @Test
    fun `a string set filters out non string members`() {
        val all = mapOf<String, Any>("active_weeks" to setOf("2025-09-15", 42, true, "2025-09-22"))

        assertEquals(setOf("2025-09-15", "2025-09-22"), SafePrefs.stringSet(all, "active_weeks"))
    }

    @Test
    fun `the returned set is a defensive copy`() {
        // Android documents that the Set from getStringSet must not be mutated: doing so
        // corrupts the in-memory value without persisting it. Callers must be able to
        // treat this result as their own.
        val stored = mutableSetOf("2025-09-15")
        val all = mapOf<String, Any>("active_weeks" to stored)

        val returned = SafePrefs.stringSet(all, "active_weeks")
        returned.toMutableSet().add("2025-09-22")

        assertEquals(setOf("2025-09-15"), stored)
    }

    @Test
    fun `a corrupt custom interval token cannot crash the launch path`() {
        // End-to-end shape of the defect fixed earlier in SyncStrategy.fromToken:
        // the preference is read, then handed to the strategy parser, and must never throw.
        val all = mapOf<String, Any>("sync_strategy_token" to "CUSTOM:0:HOURS")

        val token = SafePrefs.string(all, "sync_strategy_token")
        assertEquals("CUSTOM:0:HOURS", token)
        assertEquals(com.example.timetablescraper.api.SyncStrategy.Daily,
            com.example.timetablescraper.api.SyncStrategy.fromToken(token))
    }

    @Test
    fun `every reader survives a value of every wrong type`() {
        val keys = listOf("k")
        val values: List<Any> = listOf("text", 1, 1L, true, 1.5, setOf("s"), listOf("l"), emptyMap<String, Any>())

        for (value in values) {
            val all = mapOf("k" to value)
            // Must not throw for any type/value combination.
            SafePrefs.string(all, "k", "d")
            SafePrefs.int(all, "k", 1)
            SafePrefs.long(all, "k", 1L)
            SafePrefs.boolean(all, "k", false)
            SafePrefs.stringSet(all, "k", emptySet())
        }
        assertEquals(1, keys.size)
    }
}
