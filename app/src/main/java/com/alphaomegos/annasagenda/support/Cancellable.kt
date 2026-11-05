package com.alphaomegos.annasagenda.support

import kotlin.coroutines.cancellation.CancellationException

/**
 * runCatching for code that runs in a coroutine (05.11): the same Result, but
 * a cancellation goes on up instead of being caught as a failure — the
 * cancelled work stops there, as it must, and nothing reports «failed» for
 * what was only called off. runCatching caught it with everything else.
 */
inline fun <T> runCatchingCancellable(block: () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        Result.failure(e)
    }
