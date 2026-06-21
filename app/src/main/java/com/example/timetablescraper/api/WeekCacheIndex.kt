package com.example.timetablescraper.api

import com.example.timetablescraper.api.cache.TimetableDao

/**
 * Indexable key-value abstraction over the Room-backed week cache.
 *
 * The cache is organised as a two-level map:
 *   courseIdentity → weekStart (yyyy-MM-dd) → List&lt;ApiEvent&gt;
 *
 * This mirrors a conceptual `cache[studentId][weekNumber]` layout and
 * provides structural methods for cache-awareness without raw SQL.
 *
 * Navigate between weeks: each week's data is stored and retrieved
 * independently. Switching from W5 → W3 → W5 triggers **zero** network
 * requests for W3 or W5 if they are already cached and fresh.
 *
 * @see TimetableDao
 */
class WeekCacheIndex(private val dao: TimetableDao) {

    /**
     * Returns `true` if a specific (course, week) pair exists in the cache.
     *
     * @param courseIdentity  The course/programme identity string.
     * @param weekStart       The Monday date as `"yyyy-MM-dd"`.
     */
    suspend fun containsKey(courseIdentity: String, weekStart: String): Boolean {
        return dao.getEventCount(courseIdentity, weekStart) > 0
    }

    /**
     * Returns the number of distinct weeks cached for a given course.
     */
    suspend fun weekCount(courseIdentity: String): Int {
        return dao.countDistinctWeeksForCourse(courseIdentity)
    }

    /**
     * Returns the set of week starts that have cached data for this course.
     */
    suspend fun cachedWeeks(courseIdentity: String): List<String> {
        return dao.getDistinctWeekStarts(courseIdentity)
    }

    /**
     * Returns the age of the most recent fetch for this course+week in millis,
     * or `null` if not cached.
     */
    suspend fun ageMillis(courseIdentity: String, weekStart: String): Long? {
        val fetchedAt = dao.getLastFetchedAt(courseIdentity, weekStart) ?: return null
        return System.currentTimeMillis() - fetchedAt
    }
}

/**
 * Extension functions added to [TimetableDao] to support the index.
 */
internal suspend fun TimetableDao.getEventCount(courseIdentity: String, weekStart: String): Int {
    return countEvents(courseIdentity, weekStart)
}

internal suspend fun TimetableDao.countDistinctWeeksForCourse(courseIdentity: String): Int {
    return countWeeksForCourse(courseIdentity)
}
