package com.ridecomp.core.util

/**
 * Result type for expected failures (ERR-03): sensor lost, broken GPX, permission denied…
 * Exceptions are reserved for programming errors.
 */
public sealed interface Outcome<out T, out E> {
    public data class Success<out T>(val value: T) : Outcome<T, Nothing>

    public data class Failure<out E>(val error: E) : Outcome<Nothing, E>
}

/** True when this is [Outcome.Success]. */
public val Outcome<*, *>.isSuccess: Boolean get() = this is Outcome.Success

/** The success value, or null on failure. */
public fun <T, E> Outcome<T, E>.getOrNull(): T? = (this as? Outcome.Success)?.value

/** The error, or null on success. */
public fun <T, E> Outcome<T, E>.errorOrNull(): E? = (this as? Outcome.Failure)?.error

/** Transforms the success value, keeping failures untouched. */
public inline fun <T, E, R> Outcome<T, E>.map(transform: (T) -> R): Outcome<R, E> = when (this) {
    is Outcome.Success -> Outcome.Success(transform(value))
    is Outcome.Failure -> this
}

/** Chains another operation that can fail. */
public inline fun <T, E, R> Outcome<T, E>.flatMap(transform: (T) -> Outcome<R, E>): Outcome<R, E> = when (this) {
    is Outcome.Success -> transform(value)
    is Outcome.Failure -> this
}

/** Transforms the error, keeping successes untouched. */
public inline fun <T, E, F> Outcome<T, E>.mapError(transform: (E) -> F): Outcome<T, F> = when (this) {
    is Outcome.Success -> this
    is Outcome.Failure -> Outcome.Failure(transform(error))
}

/** Reduces both branches to one value. */
public inline fun <T, E, R> Outcome<T, E>.fold(onSuccess: (T) -> R, onFailure: (E) -> R): R = when (this) {
    is Outcome.Success -> onSuccess(value)
    is Outcome.Failure -> onFailure(error)
}

/** Runs [action] on success and returns this outcome unchanged. */
public inline fun <T, E> Outcome<T, E>.onSuccess(action: (T) -> Unit): Outcome<T, E> = also {
    if (it is Outcome.Success) action(it.value)
}

/** Runs [action] on failure and returns this outcome unchanged. */
public inline fun <T, E> Outcome<T, E>.onFailure(action: (E) -> Unit): Outcome<T, E> = also {
    if (it is Outcome.Failure) action(it.error)
}

/** Wraps a value as success. */
public fun <T> T.asSuccess(): Outcome<T, Nothing> = Outcome.Success(this)

/** Wraps a value as failure. */
public fun <E> E.asFailure(): Outcome<Nothing, E> = Outcome.Failure(this)
