package com.ridecomp.core.source

/**
 * Logging abstraction (ERR-05). Messages are built lazily so disabled levels cost nothing.
 *
 * Never pass coordinates, tokens, phone numbers or health data unless the call is [debug]
 * (debug logging is disabled in release builds).
 */
public interface Logger {
    public fun debug(tag: String, message: () -> String)

    public fun info(tag: String, message: () -> String)

    public fun warn(tag: String, throwable: Throwable? = null, message: () -> String)

    public fun error(tag: String, throwable: Throwable? = null, message: () -> String)
}
