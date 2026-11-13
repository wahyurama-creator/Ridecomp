package com.ridecomp.core.model

/** One GPS fix as delivered by a [com.ridecomp.core.source.LocationSource]. */
public data class LocationFix(
    val timeMs: Long,
    val latDeg: Double,
    val lonDeg: Double,
    val altitudeM: Double?,
    val accuracyM: Double?,
    /** Device-reported speed, when the receiver provides it. */
    val speedMps: Double?,
)

/** Air pressure from the barometer, used for altitude when available (05-DATA-MODEL §3.5). */
public data class PressureReading(val timeMs: Long, val pressureHpa: Double)

/** Lifecycle of a ride (03-ARCHITECTURE §4.2). */
public enum class RideStatus { IDLE, RECORDING, PAUSED, AUTO_PAUSED, FINISHED }

/** Persisted status of an activity row (05-DATA-MODEL §1). */
public enum class ActivityStatus { RECORDING, PAUSED, FINISHED, DISCARDED }

/** Ride events; timer start/stop pairs become FIT timer events (04-INTEGRATIONS §B.1). */
public enum class RideEventType {
    START,
    PAUSE,
    RESUME,
    AUTO_PAUSE,
    AUTO_RESUME,
    STOP,
    GAP,
    LAP,
    SENSOR_LOST,
    SENSOR_BACK,
    REMINDER_DRINK,
    REMINDER_EAT,
    REMINDER_ACK,
    BATTERY_MODE_SAVER,
    BATTERY_MODE_CRITICAL,
    BATTERY_MODE_EMERGENCY,
    INCIDENT_DETECTED,
    INCIDENT_CANCELLED,
    SOS_SENT,
    SMS_FAILED,
    OFF_ROUTE,
    BACK_ON_ROUTE,
}

public data class RideEvent(val timeMs: Long, val type: RideEventType)

/**
 * One record per second (03-ARCHITECTURE §4.3, 05-DATA-MODEL `track_points`). Null = no valid value
 * (never 0 for "unknown"). [powerW] is only real power-meter data; [estPowerW] is the estimate.
 */
public data class RideSample(
    val timeMs: Long,
    val latDeg: Double? = null,
    val lonDeg: Double? = null,
    val altitudeM: Double? = null,
    val gpsAccuracyM: Double? = null,
    val distanceM: Double = 0.0,
    val speedMps: Double? = null,
    val heartRateBpm: Int? = null,
    val cadenceRpm: Int? = null,
    val powerW: Int? = null,
    val estPowerW: Int? = null,
    val temperatureC: Double? = null,
)

/** Why a lap was closed. */
public enum class LapTrigger { MANUAL, DISTANCE }

public data class Lap(
    val index: Int,
    val startTimeMs: Long,
    val endTimeMs: Long,
    val distanceM: Double,
    val movingS: Long,
    val avgSpeedMps: Double,
    val maxSpeedMps: Double,
    val ascentM: Double,
    val trigger: LapTrigger,
    val avgHeartRateBpm: Int? = null,
    val maxHeartRateBpm: Int? = null,
    val avgCadenceRpm: Int? = null,
    val avgPowerW: Int? = null,
    val maxPowerW: Int? = null,
)

/** Aggregates of a ride, stored on the activity and in FIT session/lap messages. */
public data class RideTotals(
    val elapsedS: Long = 0,
    val movingS: Long = 0,
    val distanceM: Double = 0.0,
    val ascentM: Double = 0.0,
    val descentM: Double = 0.0,
    val avgSpeedMps: Double = 0.0,
    val maxSpeedMps: Double = 0.0,
    val avgHeartRateBpm: Int? = null,
    val maxHeartRateBpm: Int? = null,
    val avgCadenceRpm: Int? = null,
    val avgPowerW: Int? = null,
    val maxPowerW: Int? = null,
    val normalizedPowerW: Int? = null,
    val estAvgPowerW: Int? = null,
    val calories: Int? = null,
)

/** Sum of finished rides over a period (13-UI-SCREENS #11 "BULAN INI"). */
public data class PeriodTotals(val rides: Int = 0, val distanceM: Double = 0.0, val ascentM: Double = 0.0)

/** A ride as stored (domain view of `activities`). */
public data class Activity(
    val id: Long,
    val uuid: String,
    val name: String,
    val status: ActivityStatus,
    val startedAtMs: Long,
    val endedAtMs: Long?,
    val totals: RideTotals,
    val lastCheckpointAtMs: Long,
    val fitPath: String? = null,
    val fitExportedAtMs: Long? = null,
    val profileId: Long? = null,
    val routeId: Long? = null,
)
