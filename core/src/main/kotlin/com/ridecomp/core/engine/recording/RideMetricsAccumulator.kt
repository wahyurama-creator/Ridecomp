package com.ridecomp.core.engine.recording

import com.ridecomp.core.config.GpsConfig
import com.ridecomp.core.config.RecordingConfig
import com.ridecomp.core.config.Units
import com.ridecomp.core.engine.geo.AlongRoute
import com.ridecomp.core.engine.geo.DistanceTracker
import com.ridecomp.core.engine.geo.GeoMath
import com.ridecomp.core.engine.geo.GpsFilter
import com.ridecomp.core.engine.geo.GpsRejection
import com.ridecomp.core.engine.metrics.ExponentialSmoother
import com.ridecomp.core.model.LocationFix

/** How a fix or tick interval counts toward distance and moving time. */
public enum class CountingMode {
    /** Recording: counted. */
    COUNT,

    /** Auto-paused while a resume is being confirmed: counted only if the resume happens. */
    TENTATIVE,

    /** Paused: not counted. */
    NONE,
}

/** Totals restored from the last checkpoint after a crash/kill (03-ARCHITECTURE §4.5). */
public data class RecoveredTotals(
    val distanceM: Double = 0.0,
    val movingMs: Long = 0,
    val maxSpeedMps: Double = 0.0,
    val ascentM: Double = 0.0,
    val descentM: Double = 0.0,
    val lapIndex: Int = 0,
    /** Where the running lap began (end of the last saved lap, or the ride start); null = now. */
    val lapStart: RideProgress? = null,
)

/**
 * GPS-based distance, moving time and speed (05-DATA-MODEL §3.1–3.3).
 * - Speed: device speed when reported, else derived from consecutive fixes, EMA-smoothed.
 * - Max speed from the smoothed value (no GPS spikes).
 * - Moving time: sum of tick intervals spent recording; an interval longer than
 *   [RecordingConfig.MAX_TICK_INTERVAL_MS] is a stall/gap and is not counted.
 */
public class RideMetricsAccumulator(alongRoute: AlongRoute? = null) {
    private val filter = GpsFilter()
    private val distance = DistanceTracker(alongRoute = alongRoute)
    private val speedSmoother = ExponentialSmoother(GpsConfig.SPEED_EMA_ALPHA)
    private var previousFix: LocationFix? = null
    private var tentativeMovingMs: Long = 0

    // Wheel sensor (05-DATA-MODEL §3.1): when active it is the distance and speed source, GPS the fallback.
    private var wheelSpeedMps: Double? = null
    private var wheelDistanceM = 0.0
    private var tentativeWheelM = 0.0

    /** Last accepted fix. */
    public var latestFix: LocationFix? = null
        private set
    public var movingMs: Long = 0
        private set
    public var maxSpeedMps: Double = 0.0
        private set
    public val distanceM: Double get() = distance.totalM + wheelDistanceM

    /** Wheel-sensor speed of this second (null = no fresh wheel data). Call before [onTickInterval]. */
    public fun updateWheel(speedMps: Double?) {
        wheelSpeedMps = speedMps
    }

    public fun restore(totals: RecoveredTotals) {
        distance.restore(totals.distanceM)
        movingMs = totals.movingMs
        maxSpeedMps = totals.maxSpeedMps
    }

    /** Handles a raw fix; returns the rejection reason when it was dropped (to log, ERR-04). */
    public fun onFix(fix: LocationFix, mode: CountingMode): GpsRejection? {
        val rejection = filter.check(fix)
        if (rejection == null) {
            // With a wheel sensor GPS only moves its anchor; distance comes from the wheel.
            when (if (wheelSpeedMps != null) CountingMode.NONE else mode) {
                CountingMode.COUNT -> distance.add(fix, isCounting = true)
                CountingMode.TENTATIVE -> distance.addTentative(fix)
                CountingMode.NONE -> distance.add(fix, isCounting = false)
            }
            val speed = fix.speedMps ?: derivedSpeedMps(fix)
            if (speed != null) {
                val smoothed = speedSmoother.add(speed)
                if (mode == CountingMode.COUNT && smoothed > maxSpeedMps) maxSpeedMps = smoothed
            }
            previousFix = fix
            latestFix = fix
        }
        return rejection
    }

    /**
     * Adds the interval since the previous tick to moving time ([CountingMode.TENTATIVE]: kept aside until
     * the resume is confirmed). Returns true when the interval was a gap (not counted).
     */
    public fun onTickInterval(intervalMs: Long, mode: CountingMode): Boolean {
        val isGap = intervalMs > RecordingConfig.MAX_TICK_INTERVAL_MS
        if (!isGap && intervalMs > 0) countInterval(intervalMs, mode)
        return isGap
    }

    private fun countInterval(intervalMs: Long, mode: CountingMode) {
        val wheelM = (wheelSpeedMps ?: 0.0) * intervalMs / Units.MS_PER_SECOND
        when (mode) {
            CountingMode.COUNT -> {
                movingMs += intervalMs
                wheelDistanceM += wheelM
                wheelSpeedMps?.let { maxSpeedMps = maxOf(maxSpeedMps, it) }
            }
            CountingMode.TENTATIVE -> {
                tentativeMovingMs += intervalMs
                tentativeWheelM += wheelM
            }
            CountingMode.NONE -> Unit
        }
    }

    /** Auto-resume confirmed: movement measured during the confirmation becomes real. */
    public fun commitTentative() {
        distance.commitTentative()
        movingMs += tentativeMovingMs
        wheelDistanceM += tentativeWheelM
        tentativeMovingMs = 0
        tentativeWheelM = 0.0
    }

    /** Removes moving time counted while the rider had in fact already stopped (auto-pause delay). */
    public fun removeMovingMs(ms: Long) {
        movingMs = (movingMs - ms).coerceAtLeast(0)
    }

    /** The rider stopped again while auto-paused: drop the tentative movement. */
    public fun discardTentative() {
        distance.discardTentative()
        tentativeMovingMs = 0
        tentativeWheelM = 0.0
    }

    /** Smoothed speed, or null when the last fix is older than [RecordingConfig.SAMPLE_MAX_AGE_MS]. */
    public fun currentSpeedMps(nowMs: Long): Double? =
        wheelSpeedMps ?: speedSmoother.value?.takeIf { isFixFresh(nowMs) }

    /** True when this second's speed comes from a wheel sensor (reliable at standstill). */
    public val isWheelSpeed: Boolean get() = wheelSpeedMps != null

    /** True when the last accepted fix is recent enough to use. */
    public fun isFixFresh(nowMs: Long): Boolean =
        latestFix?.let { nowMs - it.timeMs <= RecordingConfig.SAMPLE_MAX_AGE_MS } == true

    /** distance / moving time (05-DATA-MODEL §3.2). */
    public fun averageSpeedMps(): Double =
        if (movingMs > 0) distanceM / (movingMs.toDouble() / Units.MS_PER_SECOND) else 0.0

    /** After a long pause, forget the old fix so the first new one is not treated as a spike. */
    public fun resetGpsHistory() {
        filter.reset()
        previousFix = null
    }

    private fun derivedSpeedMps(fix: LocationFix): Double? {
        val previous = previousFix ?: return null
        val seconds = (fix.timeMs - previous.timeMs).toDouble() / Units.MS_PER_SECOND
        val meters = GeoMath.distanceM(previous.latDeg, previous.lonDeg, fix.latDeg, fix.lonDeg)
        return if (seconds > 0) meters / seconds else null
    }
}
