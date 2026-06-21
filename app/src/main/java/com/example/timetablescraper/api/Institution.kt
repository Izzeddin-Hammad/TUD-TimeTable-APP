package com.example.timetablescraper.api

/**
 * Configuration for the Scientia Publish API at TU Dublin.
 */
data class Institution(
    val name: String,
    val apiBase: String,
    val institutionId: String,
    val programmeTypeId: String,
    val referer: String
) {
    companion object {
        val TU_DUBLIN = Institution(
            name = "TU Dublin",
            apiBase = "https://scientia-eu-v4-api-d4-01.azurewebsites.net/api/Public",
            institutionId = "50a55ae1-1c87-4dea-bb73-c9e67941e1fd",
            programmeTypeId = "241e4d36-93f2-4938-9e15-d4536fe3b2eb",
            referer = "https://timetables.tudublin.ie/"
        )
        val DEFAULT = TU_DUBLIN
    }
}
