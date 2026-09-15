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
        val published = deferred as CompletableDeferred<Result<*>>

        // Atomic put — only one caller wins. Publish *before* registering the completion callback,
        // because the losing caller's deferred is never in the map and so must not register a
        // callback that removes the key: that used to fire on `deferred.cancel()` and evict the
        // *winner's* still-running entry, letting a third concurrent caller miss the fast path and
        // issue a duplicate request — defeating the de-duplication this class exists to provide.
        val prev = inFlight.putIfAbsent(key, published)
        if (prev != null) {
            // Another coroutine won the race → await its result. Our own deferred was never
            // published, so there is nothing to clean up and nothing to cancel.
            return (prev.await().getOrThrow() as T)
        }

        // Remove the key when our deferred completes — but only while it still points at this
        // deferred, so a later request for the same key is never evicted by us.
        deferred.invokeOnCompletion { inFlight.remove(key, published) }

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
