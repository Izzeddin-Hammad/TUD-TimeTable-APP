package com.example.timetablescraper.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

/**
 * Contract tests for [SyncStrategy] — the cache-validity model that gates every network
 * fetch in the app.
 *
 * These are pure-JVM tests: [SyncStrategy] has no Android or Compose dependencies, so this
 * class runs under `./gradlew test` with no device, no Robolectric, and no shadowing.
 *
 * Scope note: the *persistence* half of this model (the legacy `sync_interval_hours`
 * migration, file `timetable_sync_prefs`) lives in [SyncPreferences] and needs Android
 * instrumentation — see `SyncPreferencesTest` in `app/src/androidTest`.
 */
class SyncStrategyContractTest {

    // ── TTL values ─────────────────────────────────────────────────────────────

    @Test
    fun `Daily strategy is a 24 hour TTL`() {
        // Act
        val ttl = SyncStrategy.Daily.ttlMillis()

        // Assert
        assertEquals(86_400_000L, ttl)
        assertEquals(TimeUnit.DAYS.toMillis(1), ttl)
    }

    @Test
    fun `Weekly strategy is a 7 day TTL`() {
        // Act
        val ttl = SyncStrategy.Weekly.ttlMillis()

        // Assert
        assertEquals(604_800_000L, ttl)
        assertEquals(TimeUnit.DAYS.toMillis(7), ttl)
    }

    @Test
    fun `Weekly TTL is strictly longer than Daily TTL`() {
        // Assert — ordering matters: a Weekly install must never refetch more often than a Daily one
        assertTrue(SyncStrategy.Weekly.ttlMillis() > SyncStrategy.Daily.ttlMillis())
    }

    @Test
    fun `Custom strategy converts each supported unit to milliseconds`() {
        // Assert
        assertEquals(90 * 60_000L, SyncStrategy.Custom(90, TimeUnit.MINUTES).ttlMillis())
        assertEquals(36 * 3_600_000L, SyncStrategy.Custom(36, TimeUnit.HOURS).ttlMillis())
        assertEquals(2 * 86_400_000L, SyncStrategy.Custom(2, TimeUnit.DAYS).ttlMillis())
    }

    @Test
    fun `Custom strategy defaults to hours when no unit is supplied`() {
        // Act
        val strategy = SyncStrategy.Custom(5)

        // Assert
        assertEquals(SyncStrategy.Custom(5, TimeUnit.HOURS).ttlMillis(), strategy.ttlMillis())
    }

    @Test
    fun `Custom strategy rejects a non-positive interval`() {
        // Assert — the init block guards the model against 0/negative intervals
        assertThrows(IllegalArgumentException::class.java) { SyncStrategy.Custom(0, TimeUnit.HOURS) }
        assertThrows(IllegalArgumentException::class.java) { SyncStrategy.Custom(-5, TimeUnit.DAYS) }
    }

    @Test
    fun `Custom display name reports the value and a short unit label`() {
        // Assert
        assertEquals("Every 3 hr", SyncStrategy.Custom(3, TimeUnit.HOURS).displayName())
        assertEquals("Every 90 min", SyncStrategy.Custom(90, TimeUnit.MINUTES).displayName())
        assertEquals("Every 2 day", SyncStrategy.Custom(2, TimeUnit.DAYS).displayName())
        assertEquals("Daily", SyncStrategy.Daily.displayName())
        assertEquals("Weekly", SyncStrategy.Weekly.displayName())
    }

    // ── Token round-trip ───────────────────────────────────────────────────────

    @Test
    fun `Every built-in strategy survives a token round-trip`() {
        // Assert
        assertEquals(SyncStrategy.Daily, SyncStrategy.fromToken(SyncStrategy.Daily.toToken()))
        assertEquals(SyncStrategy.Weekly, SyncStrategy.fromToken(SyncStrategy.Weekly.toToken()))
    }

    @Test
    fun `Custom strategy survives a token round-trip`() {
        // Arrange
        val original = SyncStrategy.Custom(36, TimeUnit.HOURS)

        // Act
        val restored = SyncStrategy.fromToken(original.toToken())

        // Assert — equality must include the unit, not just the numeric value
        assertEquals(original, restored)
        assertEquals("CUSTOM:36:HOURS", original.toToken())
        assertEquals(TimeUnit.HOURS, (restored as SyncStrategy.Custom).unit)
    }

    @Test
    fun `Custom token encodes value and unit so days and hours cannot be confused`() {
        // Assert — 2 days must not deserialize as 2 hours
        val twoDays = SyncStrategy.Custom(2, TimeUnit.DAYS)
        val restored = SyncStrategy.fromToken(twoDays.toToken()) as SyncStrategy.Custom
        assertEquals(2, restored.value)
        assertEquals(TimeUnit.DAYS, restored.unit)
        assertTrue(restored.ttlMillis() > SyncStrategy.Custom(2, TimeUnit.HOURS).ttlMillis())
    }

    // ── Corrupt / unknown tokens (read on every app launch) ────────────────────

    @Test
    fun `Null token defaults to Daily`() {
        // Assert
        assertEquals(SyncStrategy.Daily, SyncStrategy.fromToken(null))
    }

    @Test
    fun `Unrecognised token defaults to Daily rather than throwing`() {
        // Assert — a prefs file written by another build must never crash app launch
        assertEquals(SyncStrategy.Daily, SyncStrategy.fromToken(""))
        assertEquals(SyncStrategy.Daily, SyncStrategy.fromToken("BOGUS"))
        assertEquals(SyncStrategy.Daily, SyncStrategy.fromToken("MONTHLY"))
        assertEquals(SyncStrategy.Daily, SyncStrategy.fromToken("daily"))
    }

    @Test
    fun `Malformed Custom token defaults to Daily`() {
        // Assert
        assertEquals(SyncStrategy.Daily, SyncStrategy.fromToken("CUSTOM:"))
        assertEquals(SyncStrategy.Daily, SyncStrategy.fromToken("CUSTOM:abc:HOURS"))
        assertEquals(SyncStrategy.Daily, SyncStrategy.fromToken("CUSTOM"))
    }

    @Test
    fun `Custom token with an unsupported unit falls back to hours`() {
        // Act
        val restored = SyncStrategy.fromToken("CUSTOM:5:FORTNIGHTS")

        // Assert — unit is normalised, value is preserved
        assertEquals(SyncStrategy.Custom(5, TimeUnit.HOURS), restored)
    }

    @Test
    fun `Custom token with a non-positive value degrades to Daily instead of throwing`() {
        // A corrupt or hand-edited token ("CUSTOM:0:HOURS", "CUSTOM:-5:HOURS") must not be
        // able to crash the launch path that reads the sync strategy out of SharedPreferences.
        // fromToken is documented as "Returns Daily for unrecognised tokens"; a non-positive
        // value is unrecognised and must therefore degrade the same way.
        assertEquals(SyncStrategy.Daily, SyncStrategy.fromToken("CUSTOM:0:HOURS"))
        assertEquals(SyncStrategy.Daily, SyncStrategy.fromToken("CUSTOM:-5:HOURS"))
        assertEquals(SyncStrategy.Daily, SyncStrategy.fromToken("CUSTOM:0:DAYS"))
    }
}
