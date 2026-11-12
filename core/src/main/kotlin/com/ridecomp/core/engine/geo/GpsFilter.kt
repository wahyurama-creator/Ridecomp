package com.ridecomp.core.engine.geo

import com.ridecomp.core.config.GpsConfig
import com.ridecomp.core.config.Units
import com.ridecomp.core.model.LocationFix

/** Why a fix was rejected; rejected fixes are dropped and logged, never crash the ride (ERR-04). */
public enum class GpsRejection { INVALID_COORDINATE, POOR_ACCURACY, NOT_NEWER, SPEED_SPIKE }

/**
 * Accepts or rejects GPS fixes (03-ARCHITECTURE §5): invalid coordinates, accuracy worse than
 * [maxAccuracyM], timestamps that do not advance, and jumps implying more than [maxSpeedMps].
 *
 * Stateful: remembers the last accepted fix. Confine to one coroutine (CONC-01).
 */
public class GpsFilter(
    private val maxAccuracyM: Double = GpsConfig.MAX_ACCURACY_M,
    private val maxSpeedMps: Double = GpsConfig.MAX_PLAUSIBLE_SPEED_MPS,
) {
    private var lastAccepted: LocationFix? = null

    /** Returns null when [fix] is accepted, otherwise the rejection reason. */
    public fun check(fix: LocationFix): GpsRejection? {
        val rejection = rejectionFor(fix)
        if (rejection == null) lastAccepted = fix
        return rejection
    }

    /** Forgets history, e.g. after a long pause so the first fix is not compared with a stale one. */
    public fun reset() {
        lastAccepted = null
    }

    private fun rejectionFor(fix: LocationFix): GpsRejection? {
        val previous = lastAccepted
        val accuracy = fix.accuracyM
        return when {
            !GeoMath.isValidCoordinate(fix.latDeg, fix.lonDeg) -> GpsRejection.INVALID_COORDINATE
            accuracy != null && (!accuracy.isFinite() || accuracy > maxAccuracyM) -> GpsRejection.POOR_ACCURACY
            previous != null && fix.timeMs <= previous.timeMs -> GpsRejection.NOT_NEWER
            previous != null && impliedSpeedMps(previous, fix) > maxSpeedMps -> GpsRejection.SPEED_SPIKE
            else -> null
        }
    }

    private fun impliedSpeedMps(from: LocationFix, to: LocationFix): Double {
        val seconds = (to.timeMs - from.timeMs).toDouble() / Units.MS_PER_SECOND
        return GeoMath.distanceM(from.latDeg, from.lonDeg, to.latDeg, to.lonDeg) / seconds
    }
}
