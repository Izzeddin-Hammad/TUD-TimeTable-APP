package com.example.timetablescraper.api

import java.util.concurrent.TimeUnit

/**
 * Three-mode sync strategy for controlling how long cached data stays valid
 * before a network fetch is allowed.
 *
 * Each strategy calculates an explicit TTL (time-to-live) in milliseconds.
 * The repository blocks network calls when the cache age is within the TTL.
 *
 * @see SyncStrategy.Daily     24-hour TTL
 * @see SyncStrategy.Weekly    7-day TTL
 * @see SyncStrategy.Custom    User-defined value + time unit
 */
sealed class SyncStrategy {

    /**
     * Returns the cache validity threshold in milliseconds.
     * A cache entry younger than this value is considered fresh.
     */
    abstract fun ttlMillis(): Long

    /**
     * Returns a human-readable description for UI display.
     */
    abstract fun displayName(): String

    /**
     * Serialize to a persistence token (stored in SharedPreferences).
     */
    abstract fun toToken(): String

    /**
     * A: 24-hour cache validity. Network calls blocked within 24h of last fetch.
     */
    data object Daily : SyncStrategy() {
        override fun ttlMillis(): Long = TimeUnit.DAYS.toMillis(1)       // 86_400_000
        override fun displayName(): String = "Daily"
        override fun toToken(): String = "DAILY"
    }

    /**
     * B: 7-day cache validity. Network calls blocked within 7 days of last fetch.
     */
    data object Weekly : SyncStrategy() {
        override fun ttlMillis(): Long = TimeUnit.DAYS.toMillis(7)       // 604_800_000
        override fun displayName(): String = "Weekly"
        override fun toToken(): String = "WEEKLY"
    }

    /**
     * C: Custom interval. User provides a positive integer and a time unit.
     *
     * @param value  The numerical value (must be > 0).
     * @param unit   The time unit — only [TimeUnit.MINUTES], [TimeUnit.HOURS],
     *               and [TimeUnit.DAYS] are supported. Defaults to HOURS.
     */
    data class Custom(
        val value: Int,
        val unit: TimeUnit = TimeUnit.HOURS
    ) : SyncStrategy() {

        init {
            require(value > 0) { "Custom sync interval must be positive, got $value" }
        }

        override fun ttlMillis(): Long = when (unit) {
            TimeUnit.MINUTES -> TimeUnit.MINUTES.toMillis(value.toLong())
            TimeUnit.HOURS   -> TimeUnit.HOURS.toMillis(value.toLong())
            TimeUnit.DAYS    -> TimeUnit.DAYS.toMillis(value.toLong())
            else             -> TimeUnit.HOURS.toMillis(value.toLong()) // safe fallback
        }

        override fun displayName(): String {
            val unitLabel = when (unit) {
                TimeUnit.MINUTES -> "min"
                TimeUnit.HOURS   -> "hr"
                TimeUnit.DAYS    -> "day"
                else             -> "unit"
            }
            return "Every $value $unitLabel"
        }

        override fun toToken(): String = "CUSTOM:$value:${unit.name}"
    }

    companion object {
        /**
         * Reconstruct a [SyncStrategy] from its token string.
         * Returns [Daily] for unrecognised tokens.
         */
        fun fromToken(token: String?): SyncStrategy {
            if (token == null) return Daily
            return when {
                token == "DAILY"           -> Daily
                token == "WEEKLY"          -> Weekly
                token.startsWith("CUSTOM:") -> {
                    val parts = token.split(":")
                    if (parts.size >= 3) {
                        val v = parts[1].toIntOrNull() ?: return Daily
                        val u = try { TimeUnit.valueOf(parts[2]) } catch (_: Exception) { TimeUnit.HOURS }
                        Custom(v, u)
                    } else Daily
                }
                else                        -> Daily
            }
        }
    }
}
