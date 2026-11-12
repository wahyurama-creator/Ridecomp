package com.ridecomp.core.engine.geo

import com.ridecomp.core.config.GpsConfig
import com.ridecomp.core.model.LocationFix
import kotlin.math.max

/**
 * Accumulates GPS distance (05-DATA-MODEL §3.1, 03-ARCHITECTURE §5) while ignoring jitter when standing
 * still:
 * - when the receiver reports a speed (Doppler, reliable near zero), a segment counts only if that speed
 *   is at least [stationarySpeedMps];
 * - otherwise a segment counts only once the position moved more than max(accuracy, [jitterFloorM]) from
 *   the last counted point (the anchor stays put until then, so slow riding is still counted).
 *
 * While not [isCounting][add] (paused) the anchor follows the rider, so movement during a pause is never
 * added after resume.
 */
public class DistanceTracker(
    private val stationarySpeedMps: Double = GpsConfig.STATIONARY_SPEED_MPS,
    private val jitterFloorM: Double = GpsConfig.JITTER_FLOOR_M,
) {
    private var anchor: LocationFix? = null

    /** Total counted distance in meters. */
    public var totalM: Double = 0.0
        private set

    /** Restores the total after a crash/kill recovery. */
    public fun restore(totalM: Double) {
        this.totalM = totalM
        anchor = null
    }

    /** Adds the movement up to [fix] (already accepted by [GpsFilter]). Returns the meters added. */
    public fun add(fix: LocationFix, isCounting: Boolean): Double {
        val previous = anchor
        val segmentM = previous?.let { GeoMath.distanceM(it.latDeg, it.lonDeg, fix.latDeg, fix.lonDeg) } ?: 0.0
        val reportedSpeed = fix.speedMps
        val counted = when {
            previous == null || !isCounting -> null
            reportedSpeed != null -> segmentM.takeIf { reportedSpeed >= stationarySpeedMps }
            else -> segmentM.takeIf { it > max(fix.accuracyM ?: 0.0, jitterFloorM) }
        }
        val isAnchorMoving = previous == null || !isCounting || reportedSpeed != null || counted != null
        if (isAnchorMoving) anchor = fix
        val addedM = counted ?: 0.0
        totalM += addedM
        return addedM
    }
}
