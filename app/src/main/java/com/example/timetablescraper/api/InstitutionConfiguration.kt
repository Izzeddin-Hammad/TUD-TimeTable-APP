package com.example.timetablescraper.api

/**
 * Fully dynamic institution configuration — zero hardcoded values.
 *
 * Every string (base URL, endpoint UUIDs, referer, User-Agent) is injected
 * through this interface. The app ships with a [DEFAULT] configuration for
 * TU Dublin, but any university using Scientia Publish can be supported
 * by providing a different implementation.
 *
 * To keep the API surface minimal, all values are required at construction.
 * The companion object provides production defaults and a builder-style
 * [overrides] factory for partial customization.
 */
interface InstitutionConfiguration {

    /** Human-readable institution name (e.g. "TU Dublin"). */
    val name: String

    /** Base URL for the Scientia Publish public API (no trailing slash). */
    val apiBaseUrl: String

    /** UUID identifying the institution in the Scientia system. */
    val institutionId: String

    /** UUID identifying the programme/ course type category. */
    val programmeTypeId: String

    /** Referer header sent with API requests (institution's timetable portal). */
    val referer: String

    /**
     * User-Agent string advertising this as an open-source student utility.
     * University sysadmins can use this to categorise traffic safely.
     */
    val userAgent: String

    companion object {

        /**
         * Default configuration for TU Dublin.
         *
         * The User-Agent clearly identifies this as an open-source student
         * project so university network teams can whitelist or monitor traffic.
         */
        val DEFAULT: InstitutionConfiguration = DefaultInstitution()

        /**
         * Create a configuration with override values for any subset of fields.
         * Unspecified fields fall back to [DEFAULT].
         */
        fun overrides(
            name: String? = null,
            apiBaseUrl: String? = null,
            institutionId: String? = null,
            programmeTypeId: String? = null,
            referer: String? = null,
            userAgent: String? = null
        ): InstitutionConfiguration = OverrideInstitution(
            name        = name        ?: DEFAULT.name,
            apiBaseUrl  = apiBaseUrl  ?: DEFAULT.apiBaseUrl,
            institutionId = institutionId ?: DEFAULT.institutionId,
            programmeTypeId = programmeTypeId ?: DEFAULT.programmeTypeId,
            referer     = referer     ?: DEFAULT.referer,
            userAgent   = userAgent   ?: DEFAULT.userAgent
        )
    }
}

/**
 * Default TU Dublin configuration.
 *
 * @see InstitutionConfiguration.DEFAULT
 */
internal data class DefaultInstitution(
    override val name: String = "TU Dublin",
    override val apiBaseUrl: String = "https://scientia-eu-v4-api-d4-01.azurewebsites.net/api/Public",
    override val institutionId: String = "50a55ae1-1c87-4dea-bb73-c9e67941e1fd",
    override val programmeTypeId: String = "241e4d36-93f2-4938-9e15-d4536fe3b2eb",
    override val referer: String = "https://timetables.tudublin.ie/",
    override val userAgent: String = "TimeTableApp/1.1 (Open Source Student Utility; +https://github.com/Izzeddin-Hammad/TimeTable-APP)"
) : InstitutionConfiguration

/**
 * Override wrapper — used by [InstitutionConfiguration.Companion.overrides].
 */
internal data class OverrideInstitution(
    override val name: String,
    override val apiBaseUrl: String,
    override val institutionId: String,
    override val programmeTypeId: String,
    override val referer: String,
    override val userAgent: String
) : InstitutionConfiguration
