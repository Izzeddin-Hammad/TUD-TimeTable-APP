package com.example.timetablescraper.api

/**
 * Typed exception for Scientia API HTTP errors.
 * Carries the HTTP status code and response body for structured handling.
 *
 * All API-layer failures throw this instead of raw [Exception] with
 * string-parsed codes.  Callers (repository, UI) can branch on [httpCode].
 */
class TimetableApiException(
    val httpCode: Int,
    val body: String = "",
    message: String = "API error $httpCode"
) : Exception(message) {

    val isRetryable: Boolean get() = httpCode >= 500 || httpCode == 429

    val isConnectivityLoss: Boolean get() = false

    companion object {
        /** Factory for response objects when you have the raw OkHttp response. */
        fun fromResponse(code: Int, body: String?): TimetableApiException {
            val msg = when {
                code == 429 -> "Rate limited (429)"
                code in 400..499 -> "Client error $code"
                code >= 500 -> "Server error $code"
                else -> "Unexpected status $code"
            }
            return TimetableApiException(code, body ?: "", msg)
        }
    }
}
