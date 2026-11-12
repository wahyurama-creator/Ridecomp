package com.ridecomp.core.source

/**
 * Source of time for the whole app (KT-12). Never call `System.currentTimeMillis()` directly;
 * inject a [Clock] so logic stays deterministic in tests.
 */
public interface Clock {
    /** Wall-clock time as epoch milliseconds UTC. Used for stored timestamps. */
    public fun nowMs(): Long

    /** Monotonic time in milliseconds (unaffected by user/NTP clock changes). Used for durations and timeouts. */
    public fun monotonicMs(): Long
}
