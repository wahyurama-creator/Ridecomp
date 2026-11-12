package com.ridecomp.core.util

import kotlin.coroutines.cancellation.CancellationException

/**
 * Like [runCatching] but never swallows [CancellationException] (ERR-02), so structured
 * concurrency keeps working.
 */
@Suppress("TooGenericExceptionCaught") // Intentional: converts any failure into a Result, rethrows cancellation.
public inline fun <R> runCatchingCancellable(block: () -> R): Result<R> = try {
    Result.success(block())
} catch (cancellation: CancellationException) {
    throw cancellation
} catch (throwable: Throwable) {
    Result.failure(throwable)
}
