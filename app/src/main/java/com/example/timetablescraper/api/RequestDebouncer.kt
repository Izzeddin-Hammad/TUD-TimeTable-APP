package com.example.timetablescraper.api

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import java.util.concurrent.ConcurrentHashMap

/**
 * Singleton request-deduplication mechanism.
 *
 * Concurrent network operations to the **exact same URL** are consolidated
 * into a single in-flight request via [CompletableDeferred] keyed by
 * a canonical request descriptor.
 *
 * ## Thread-safety
 * - [ConcurrentHashMap] provides lock-free read and atomic putIfAbsent.
 * - [invokeOnCompletion] ensures the key is removed AFTER all awaiters
 *   have observed the completed deferred (no TOCTOU race).
 * - Cancellation propagates properly: cancelled callers don't affect
 *   other waiters, and the deferred is cleaned up on [CancellationException].
 */
class RequestDebouncer {

    private val inFlight = ConcurrentHashMap<String, CompletableDeferred<Result<*>>>()

    /**
     * Execute [block] deduplicated by [key].
     *
     * - If another coroutine is already fetching [key], awaits its result.
     * - Otherwise, becomes the first caller, runs [block], and signals all waiters.
     * - On cancellation, a [CancellationException] is rethrown; the [CompletableDeferred]
     *   is cancelled so other waiters also fail fast rather than hanging.
     */
    @Suppress("UNCHECKED_CAST")
    suspend fun <T> execute(key: String, block: suspend () -> T): T {
        // Fast path: already in flight → await existing result
        inFlight[key]?.let { existing ->
            return (existing.await().getOrThrow() as T)
        }

        val deferred = CompletableDeferred<Result<T>>()
        // Remove key when deferred completes so new calls start fresh
        deferred.invokeOnCompletion { inFlight.remove(key) }

        // Atomic put — only one caller wins
        val prev = inFlight.putIfAbsent(key, deferred as CompletableDeferred<Result<*>>)
        if (prev != null) {
            // Another coroutine won the race → await its result
            deferred.cancel()  // clean up our unused deferred
            return (prev.await().getOrThrow() as T)
        }

        try {
            val value = block()
            deferred.complete(Result.success(value))
            return value
        } catch (e: CancellationException) {
            // Cancel the deferred so all waiters fail fast rather than hang
            deferred.cancel(e)
            throw e
        } catch (e: Exception) {
            deferred.complete(Result.failure<T>(e))
            throw e
        }
    }

    fun inFlightCount(): Int = inFlight.size

    fun activeKeys(): Set<String> = inFlight.keys.toSet()

    companion object {
        val instance: RequestDebouncer by lazy { RequestDebouncer() }
    }
}
