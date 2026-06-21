package com.example.timetablescraper.api

/**
 * Configuration for the Scientia Publish API at TU Dublin.
 *
 * ## Migration Guide
 * This class now implements [InstitutionConfiguration] for full dynamic injection.
 * Prefer [InstitutionConfiguration.Companion.DEFAULT] and
 * [InstitutionConfiguration.Companion.overrides] for new code.
 *
 * @see InstitutionConfiguration
 */
data class Institution(
    override val name: String,
    override val apiBaseUrl: String,
    override val institutionId: String,
    override val programmeTypeId: String,
    override val referer: String,
    override val userAgent: String = "TimeTableApp/1.1 (Open Source Student Utility; +https://github.com/Izzeddin-Hammad/TimeTable-APP)"
) : InstitutionConfiguration {

    @Deprecated(
        message = "Use apiBaseUrl instead of apiBase",
        replaceWith = ReplaceWith("apiBaseUrl")
    )
    val apiBase: String get() = apiBaseUrl

    companion object {
        val TU_DUBLIN = Institution(
            name = "TU Dublin",
            apiBaseUrl = "https://scientia-eu-v4-api-d4-01.azurewebsites.net/api/Public",
            institutionId = "50a55ae1-1c87-4dea-bb73-c9e67941e1fd",
            programmeTypeId = "241e4d36-93f2-4938-9e15-d4536fe3b2eb",
            referer = "https://timetables.tudublin.ie/"
        )

        /** Default institution — points to TU Dublin. */
        val DEFAULT: InstitutionConfiguration = TU_DUBLIN

        /**
         * Convenience accessor for the default config's search endpoint.
         * Returns the full CategoryTypes/FilterWithCache URL.
         */
        fun defaultSearchUrl(config: InstitutionConfiguration = DEFAULT): String {
            return "${config.apiBaseUrl}/CategoryTypes/${config.programmeTypeId}" +
                    "/Categories/FilterWithCache/${config.institutionId}"
        }

        /**
         * Convenience accessor for the default config's events endpoint.
         * Returns the full CategoryTypes/Categories/Events/Filter URL.
         */
        fun defaultEventsBaseUrl(config: InstitutionConfiguration = DEFAULT): String {
            return "${config.apiBaseUrl}/CategoryTypes/Categories/Events/Filter/${config.institutionId}"
        }
    }
}
