package com.example.timetablescraper.api

import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/**
 * Typed result wrapper that classifies every network outcome into one of
 * four categories. This allows the UI and repository to make structural
 * decisions (show cached data vs. error) without parsing exception strings.
 *
 * ## Classification rules
 *
 * | Scenario                     | [NetworkResult]             |
 * |------------------------------|-----------------------------|
 * | Successful 2xx response      | [Success]                   |
 * | HTTP 429 (Too Many Requests) | [HttpError] with 429 code   |
 * | HTTP 5xx (Server Error)      | [HttpError] with 5xx code   |
 * | Timeout / UnknownHost / SSL  | [TransportError]            |
 * | Any other failure            | [TransportError]            |
 */
sealed class NetworkResult<out T> {

    /** Successful response with parsed [data]. */
    data class Success<T>(val data: T) : NetworkResult<T>()

    /** HTTP-level error (non-2xx). */
    data class HttpError(
        val code: Int,
        val body: String = "",
        val message: String = "HTTP $code"
    ) : NetworkResult<Nothing>()

    /** Transport-level failure: timeout, DNS, SSL, connectivity loss. */
    data class TransportError(
        val reason: String,
        val exception: Throwable? = null
    ) : NetworkResult<Nothing>()

    // ── Convenience accessors ─────────────────────────────────────────

    /** Returns `true` if this is a [Success]. */
    val isSuccess: Boolean get() = this is Success

    /** Returns `true` if this is an error (either HTTP or transport). */
    val isError: Boolean get() = !isSuccess

    /**
     * Returns `true` if this is a **retryable** error —
     * transport failures and 5xx errors are retryable; 4xx (except 429) are not.
     */
    val isRetryable: Boolean get() = when (this) {
        is Success        -> false
        is HttpError      -> code >= 500 || code == 429
        is TransportError -> true
    }

    /**
     * Returns `true` if this error warrants showing an offline/cache-stale
     * warning to the user.
     */
    val isConnectivityLoss: Boolean get() = when (this) {
        is Success        -> false
        is HttpError      -> false
        is TransportError -> true
    }

    /**
     * Returns a human-readable status label for the UI:
     * - `"online"` for success
     * - `"offline"` for transport errors
     * - `"server_error"` for 5xx or 429
     * - `"blocked"` for other 4xx
     */
    val statusLabel: String get() = when (this) {
        is Success        -> "online"
        is TransportError -> "offline"
        is HttpError -> when {
            code == 429        -> "rate_limited"
            code in 400..499   -> "blocked"
            code >= 500        -> "server_error"
            else               -> "unknown"
        }
    }

    companion object {

        /**
         * Classify any [Throwable] into a [NetworkResult].
         *
         * This is the single entry point for exception → result mapping.
         * Never parse exception strings in business logic.
         */
        fun fromException(e: Throwable): NetworkResult<Nothing> {
            return when (e) {
                is SocketTimeoutException ->
                    TransportError("Connection timed out", e)
                is UnknownHostException ->
                    TransportError("Unable to resolve host — no internet?", e)
                is ConnectException ->
                    TransportError("Connection refused", e)
                is SSLException ->
                    TransportError("SSL handshake failed", e)
                else -> {
                    // Try to detect HTTP errors from exception message
                    // (legacy OkHttp throws exceptions for non-2xx)
                    val msg = e.message ?: ""
                    when {
                        msg.contains("429", ignoreCase = true) ||
                        msg.contains("too many requests", ignoreCase = true) ->
                            HttpError(code = 429, message = msg)
                        Regex("""\b(5\d{2})\b""").find(msg)?.let { (it.value.toIntOrNull() ?: 0) >= 500 } == true ->
                            HttpError(code = Regex("""\b(5\d{2})\b""").find(msg)!!.value.toInt(), message = msg)
                        Regex("""\b(4\d{2})\b""").find(msg)?.let { it.value.toIntOrNull() } != null ->
                            HttpError(code = Regex("""\b(4\d{2})\b""").find(msg)!!.value.toInt(), message = msg)
                        else ->
                            TransportError(msg, e)
                    }
                }
            }
        }

        /**
         * Build a [NetworkResult] from an HTTP status code and body.
         * Use this when you have direct access to the response object.
         */
        fun fromHttpCode(code: Int, body: String = ""): NetworkResult<Nothing> {
            return when {
                code in 200..299 -> error("fromHttpCode should not be called for 2xx")
                code == 429      -> HttpError(code = 429, body = body, message = "Rate limited (429)")
                code in 400..499 -> HttpError(code = code, body = body, message = "Client error $code")
                code >= 500      -> HttpError(code = code, body = body, message = "Server error $code")
                else             -> HttpError(code = code, body = body, message = "Unexpected status $code")
            }
        }
    }
}
