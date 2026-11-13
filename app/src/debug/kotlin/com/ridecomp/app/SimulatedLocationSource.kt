package com.ridecomp.app

import com.ridecomp.core.config.Units
import com.ridecomp.core.model.LocationFix
import com.ridecomp.core.source.Clock
import com.ridecomp.core.source.LocationSource
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/** Synthetic ride used by the debug-only GPS simulator (07-TEST-PLAN §2). Debug source set only. */
object SimulatedRide {
    /** Loop centre (Yogyakarta) and radius: ~3.1 km per lap. */
    const val CENTER_LAT_DEG = -7.7829
    const val CENTER_LON_DEG = 110.3671
    const val RADIUS_M = 500.0
    const val BASE_ALTITUDE_M = 120.0
    const val ALTITUDE_SWING_M = 15.0
    const val ACCURACY_M = 5.0

    /** Every cycle: ride [RIDE_S] seconds, then stand still [STOP_S] seconds (traffic light). */
    const val RIDE_S = 120L
    const val STOP_S = 30L
    const val BASE_SPEED_MPS = 7.0
    const val SPEED_SWING_MPS = 2.0
    private const val METERS_PER_DEGREE = 111_195.0
    private const val SPEED_PERIOD_S = 40.0

    /** Speed at [second] of the simulation. */
    fun speedMps(second: Long): Double {
        val inCycle = second % (RIDE_S + STOP_S)
        return if (inCycle < RIDE_S) BASE_SPEED_MPS + SPEED_SWING_MPS * sin(2 * PI * second / SPEED_PERIOD_S) else 0.0
    }

    /** Fix after [distanceM] along the loop. */
    fun fixAt(distanceM: Double, speedMps: Double, timeMs: Long): LocationFix {
        val angle = distanceM / RADIUS_M
        val lat = CENTER_LAT_DEG + RADIUS_M * sin(angle) / METERS_PER_DEGREE
        val lon = CENTER_LON_DEG + RADIUS_M * cos(angle) / (METERS_PER_DEGREE * cos(Math.toRadians(CENTER_LAT_DEG)))
        return LocationFix(
            timeMs = timeMs,
            latDeg = lat,
            lonDeg = lon,
            altitudeM = BASE_ALTITUDE_M + ALTITUDE_SWING_M * sin(angle),
            accuracyM = ACCURACY_M,
            speedMps = speedMps,
        )
    }
}

/** Real-time replay of [SimulatedRide] at 1 Hz. */
class SimulatedLocationSource(private val clock: Clock) : LocationSource {
    override fun fixes(intervalMs: Long): Flow<LocationFix> = flow {
        var second = 0L
        var distanceM = 0.0
        while (true) {
            val speed = SimulatedRide.speedMps(second)
            distanceM += speed * intervalMs / Units.MS_PER_SECOND
            emit(SimulatedRide.fixAt(distanceM, speed, clock.nowMs()))
            delay(intervalMs)
            second += intervalMs / Units.MS_PER_SECOND
        }
    }

    override fun isPermissionGranted(): Boolean = true

    override fun isGpsEnabled(): Boolean = true
}

/** Uses the simulator when the developer switch is on, otherwise the real GPS. */
class DebugLocationSource(
    private val real: LocationSource,
    private val simulated: LocationSource,
    private val settings: DeveloperSettings,
) : LocationSource {
    private val isSimulating: Boolean get() = settings.isSimulatingGps

    override fun fixes(intervalMs: Long): Flow<LocationFix> =
        if (isSimulating) simulated.fixes(intervalMs) else real.fixes(intervalMs)

    override fun isPermissionGranted(): Boolean = isSimulating || real.isPermissionGranted()

    override fun isGpsEnabled(): Boolean = isSimulating || real.isGpsEnabled()
}
