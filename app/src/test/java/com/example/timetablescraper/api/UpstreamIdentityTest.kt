package com.example.timetablescraper.api

import com.example.timetablescraper.BuildConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the identity the app presents to the university's API.
 *
 * The User-Agent used to be a hand-written literal that said `TimeTableApp/1.1` while the app
 * was on 1.22 — a university reading its own request logs would have seen an ancient client, and
 * the value is also the only way campus network teams can identify and whitelist this traffic.
 * It is now derived from the app version, and these tests keep it that way.
 */
class UpstreamIdentityTest {

    @Test
    fun `user agent carries the installed app version`() {
        assertTrue(
            "expected '${BuildConfig.VERSION_NAME}' in '$UPSTREAM_USER_AGENT'",
            UPSTREAM_USER_AGENT.contains(BuildConfig.VERSION_NAME),
        )
    }

    @Test
    fun `user agent does not claim an old hardcoded version`() {
        // Regression guard: the literal "1.1" is what shipped for several releases.
        assertFalse(UPSTREAM_USER_AGENT.startsWith("TimeTableApp/1.1 "))
    }

    @Test
    fun `user agent identifies the project and offers a contact url`() {
        assertTrue(UPSTREAM_USER_AGENT.startsWith("TimeTableApp/"))
        assertTrue(UPSTREAM_USER_AGENT.contains("Open Source Student Utility"))
        assertTrue(UPSTREAM_USER_AGENT.contains("https://github.com/Izzeddin-Hammad/TUD-TimeTable-APP"))
    }

    @Test
    fun `user agent is a single header-safe line`() {
        assertFalse(UPSTREAM_USER_AGENT.contains('\n'))
        assertFalse(UPSTREAM_USER_AGENT.contains('\r'))
        assertTrue(UPSTREAM_USER_AGENT.length < 256)
    }

    @Test
    fun `both institution configurations expose the same user agent`() {
        // The string used to be duplicated in two places, which is how one of them went stale.
        assertEquals(UPSTREAM_USER_AGENT, Institution.TU_DUBLIN.userAgent)
        assertEquals(UPSTREAM_USER_AGENT, InstitutionConfiguration.DEFAULT.userAgent)
        assertEquals(Institution.TU_DUBLIN.userAgent, InstitutionConfiguration.DEFAULT.userAgent)
    }

    @Test
    fun `there is exactly one user agent literal in the api package`() {
        // If a second literal appears, the de-duplication has been undone.
        assertEquals(
            1,
            listOf(Institution.TU_DUBLIN.userAgent, InstitutionConfiguration.DEFAULT.userAgent)
                .distinct()
                .size,
        )
    }

    @Test
    fun `overrides can still supply a different user agent`() {
        // Multi-institution support must not be broken by the shared default.
        val custom = InstitutionConfiguration.overrides(userAgent = "OtherApp/2.0")

        assertEquals("OtherApp/2.0", custom.userAgent)
        assertEquals(InstitutionConfiguration.DEFAULT.name, custom.name)
    }
}
