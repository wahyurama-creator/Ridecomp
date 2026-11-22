package com.ridecomp.core.engine.geo

import com.ridecomp.core.config.GpsConfig
import com.ridecomp.core.config.Units
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
 * added after resume. After a stretch without GPS the segment follows [alongRoute] when a route is
 * followed (D-150), so a winding trail is not measured as its straight shortcut.
 */
public class DistanceTracker(
    private val stationarySpeedMps: Double = GpsConfig.STATIONARY_SPEED_MPS,
    private val jitterFloorM: Double = GpsConfig.JITTER_FLOOR_M,
    private val alongRoute: AlongRoute? = null,
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

    /**
     * Movement measured while auto-paused, kept aside until the resume is confirmed: committed on
     * resume, discarded when the rider stops again (so the first meters after a stop are not lost).
     */
    public var tentativeM: Double = 0.0
        private set

    /** Adds the movement up to [fix] (already accepted by [GpsFilter]). Returns the meters added. */
    public fun add(fix: LocationFix, isCounting: Boolean): Double = measure(fix, isCounting).also { totalM += it }

    /** Like [add] but the movement goes to [tentativeM] (auto-paused, resume not yet confirmed). */
    public fun addTentative(fix: LocationFix): Double = measure(fix, isCounting = true).also { tentativeM += it }

    /** Resume confirmed: the tentative movement becomes real distance. */
    public fun commitTentative() {
        totalM += tentativeM
        tentativeM = 0.0
    }

    /** Rider stopped again: the tentative movement is dropped. */
    public fun discardTentative() {
        tentativeM = 0.0
    }

    private fun measure(fix: LocationFix, isCounting: Boolean): Double {
        val previous = anchor
        val segmentM = previous?.let { segment(it, fix) } ?: 0.0
        val reportedSpeed = fix.speedMps
        val counted = when {
            previous == null || !isCounting -> null
            reportedSpeed != null -> segmentM.takeIf { reportedSpeed >= stationarySpeedMps }
            else -> segmentM.takeIf { it > max(fix.accuracyM ?: 0.0, jitterFloorM) }
        }
        val isAnchorMoving = previous == null || !isCounting || reportedSpeed != null || counted != null
        if (isAnchorMoving) anchor = fix
        return counted ?: 0.0
    }

    /** Straight line, or along the route after a gap when that is plausible (never shorter, ≤ 3×, ≤ 120 km/h). */
    private fun segment(from: LocationFix, to: LocationFix): Double {
        val straightM = GeoMath.distanceM(from.latDeg, from.lonDeg, to.latDeg, to.lonDeg)
        val gapMs = to.timeMs - from.timeMs
        val routeM = alongRoute?.takeIf { gapMs >= GpsConfig.ROUTE_GAP_MIN_MS }
            ?.distanceM(from.latDeg, from.lonDeg, to.latDeg, to.lonDeg)
            ?.takeIf { route ->
                route >= straightM &&
                    route <= straightM * GpsConfig.ROUTE_GAP_MAX_FACTOR &&
                    route / gapMs * Units.MS_PER_SECOND <= GpsConfig.MAX_PLAUSIBLE_SPEED_MPS
            }
        return routeM ?: straightM
    }
}
