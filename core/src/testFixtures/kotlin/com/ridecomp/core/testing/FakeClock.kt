package com.ridecomp.core.testing

import com.ridecomp.core.source.Clock

/** Manually driven [Clock] for deterministic tests (TEST-03). */
public class FakeClock(private var nowMs: Long = DEFAULT_START_MS, private var monotonicMs: Long = 0L) : Clock {
    override fun nowMs(): Long = nowMs

    override fun monotonicMs(): Long = monotonicMs

    /** Moves both wall and monotonic time forward by [deltaMs]. */
    public fun advanceBy(deltaMs: Long) {
        require(deltaMs >= 0) { "deltaMs must be >= 0" }
        nowMs += deltaMs
        monotonicMs += deltaMs
    }

    /** Sets wall-clock time only (simulates a user/NTP clock change). */
    public fun setWallClock(epochMs: Long) {
        nowMs = epochMs
    }

    public companion object {
        /** 2026-01-01T00:00:00Z. */
        public const val DEFAULT_START_MS: Long = 1_767_225_600_000L
    }
}
