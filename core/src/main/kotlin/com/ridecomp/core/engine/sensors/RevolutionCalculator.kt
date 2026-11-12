package com.ridecomp.core.engine.sensors

import com.ridecomp.core.config.Units

/**
 * Turns consecutive cumulative revolution data into a rate (04-INTEGRATIONS §A.2):
 * ```
 * dRev  = (rev  - prevRev)  mod 2^N       (N = 32 wheel, 16 crank)
 * dTime = (time - prevTime) mod 65536     (ticks of 1/1024 or 1/2048 s)
 * rate  = dRev / (dTime / ticksPerSecond)  revolutions per second
 * ```
 * When the sensor repeats the same event time (no new revolution) for more than 3 s the rate is 0.
 */
public class RevolutionCalculator(private val revolutionBits: Int) {
    private var previous: RevolutionData? = null
    private var lastEventAtMs: Long? = null
    private var lastRatePerSecond: Double? = null

    /**
     * Adds a measurement received at [receivedAtMs] (monotonic); returns revolutions per second, 0 after the
     * no-event timeout, or null when no rate is known yet.
     */
    public fun add(data: RevolutionData, receivedAtMs: Long): Double? {
        val before = previous
        previous = data
        val rate = before?.let { rateBetween(it, data) }
        return when {
            before == null -> null.also { lastEventAtMs = receivedAtMs }
            // Same event repeated: no new revolution since the last notification.
            rate == null -> currentRate(receivedAtMs)
            else -> rate.also {
                lastEventAtMs = receivedAtMs
                lastRatePerSecond = it
            }
        }
    }

    /** Revolutions per second between two events, or null when no new revolution happened. */
    private fun rateBetween(before: RevolutionData, data: RevolutionData): Double? {
        val dRev = (data.revolutions - before.revolutions).mod(1L shl revolutionBits)
        val dTicks = (data.eventTimeTicks - before.eventTimeTicks).mod(TIME_RANGE)
        return if (dTicks == 0 || dRev == 0L) null else dRev / (dTicks.toDouble() / data.ticksPerSecond)
    }

    /** Rate now: 0 once no new event arrived for [SensorLimits.NO_EVENT_TIMEOUT_MS]. */
    public fun currentRate(nowMs: Long): Double? {
        val last = lastEventAtMs ?: return null
        return if (nowMs - last > SensorLimits.NO_EVENT_TIMEOUT_MS) 0.0 else lastRatePerSecond
    }

    /** Forget history, e.g. after a reconnect (counters may have reset). */
    public fun reset() {
        previous = null
        lastEventAtMs = null
        lastRatePerSecond = null
    }

    public companion object {
        public const val WHEEL_BITS: Int = 32
        public const val CRANK_BITS: Int = 16
        private const val TIME_RANGE = 65_536
    }
}

/** Cadence from crank data; implausible values (> 200 rpm) are dropped (§A.2). */
public class CadenceCalculator {
    private val revolutions = RevolutionCalculator(RevolutionCalculator.CRANK_BITS)

    public fun add(crank: RevolutionData, receivedAtMs: Long): Double? =
        revolutions.add(crank, receivedAtMs)?.toRpm()?.takeIf { it <= SensorLimits.MAX_CADENCE_RPM }

    public fun current(nowMs: Long): Double? = revolutions.currentRate(nowMs)?.toRpm()

    public fun reset() {
        revolutions.reset()
    }

    private fun Double.toRpm(): Double = this * Units.SECONDS_PER_MINUTE
}

/** Wheel speed from wheel data and the wheel circumference; > 120 km/h is dropped (§A.2). */
public class WheelSpeedCalculator(private val circumferenceM: Double) {
    private val revolutions = RevolutionCalculator(RevolutionCalculator.WHEEL_BITS)

    public fun add(wheel: RevolutionData, receivedAtMs: Long): Double? =
        revolutions.add(wheel, receivedAtMs)?.times(circumferenceM)?.takeIf { it <= SensorLimits.MAX_WHEEL_SPEED_MPS }

    public fun current(nowMs: Long): Double? = revolutions.currentRate(nowMs)?.times(circumferenceM)

    public fun reset() {
        revolutions.reset()
    }
}
