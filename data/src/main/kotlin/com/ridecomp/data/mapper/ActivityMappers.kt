package com.ridecomp.data.mapper

import com.ridecomp.core.model.Activity
import com.ridecomp.core.model.ActivityStatus
import com.ridecomp.core.model.Lap
import com.ridecomp.core.model.LapTrigger
import com.ridecomp.core.model.RideEvent
import com.ridecomp.core.model.RideEventType
import com.ridecomp.core.model.RideSample
import com.ridecomp.core.model.RideTotals
import com.ridecomp.data.db.DbNames
import com.ridecomp.data.db.entity.ActivityEntity
import com.ridecomp.data.db.entity.EventEntity
import com.ridecomp.data.db.entity.LapEntity
import com.ridecomp.data.db.entity.TrackPointEntity
import java.util.Locale

/**
 * Explicit mappers between Room entities and domain models (ARCH-05). Enum values are stored as stable
 * lowercase strings (05-DATA-MODEL); unknown stored values are skipped, never crash (ERR-04).
 */
internal fun ActivityStatus.toDb(): String = when (this) {
    ActivityStatus.RECORDING -> DbNames.ActivityStatus.RECORDING
    ActivityStatus.PAUSED -> DbNames.ActivityStatus.PAUSED
    ActivityStatus.FINISHED -> DbNames.ActivityStatus.FINISHED
    ActivityStatus.DISCARDED -> DbNames.ActivityStatus.DISCARDED
}

internal fun String.toActivityStatus(): ActivityStatus? = when (this) {
    DbNames.ActivityStatus.RECORDING -> ActivityStatus.RECORDING
    DbNames.ActivityStatus.PAUSED -> ActivityStatus.PAUSED
    DbNames.ActivityStatus.FINISHED -> ActivityStatus.FINISHED
    DbNames.ActivityStatus.DISCARDED -> ActivityStatus.DISCARDED
    else -> null
}

/** Lowercase stored form of an enum ("AUTO_PAUSE" → "auto_pause"). */
internal fun Enum<*>.toDbValue(): String = name.lowercase(Locale.ROOT)

/** Parses a stored enum value; null when unknown. */
internal inline fun <reified T : Enum<T>> String.toEnumOrNull(): T? =
    enumValues<T>().firstOrNull { it.name.equals(this, ignoreCase = true) }

internal fun ActivityEntity.toDomain(): Activity? = status.toActivityStatus()?.let { activityStatus ->
    Activity(
        id = id,
        uuid = uuid,
        name = name,
        status = activityStatus,
        startedAtMs = startedAt,
        endedAtMs = endedAt,
        totals = RideTotals(
            elapsedS = elapsedS,
            movingS = movingS,
            distanceM = distanceM,
            ascentM = ascentM,
            descentM = descentM,
            avgSpeedMps = avgSpeedMps,
            maxSpeedMps = maxSpeedMps,
            avgHeartRateBpm = avgHr,
            maxHeartRateBpm = maxHr,
            avgCadenceRpm = avgCadence,
            avgPowerW = avgPower,
            maxPowerW = maxPower,
            normalizedPowerW = npPower,
            estAvgPowerW = estAvgPower,
            calories = calories,
        ),
        lastCheckpointAtMs = lastCheckpointAt,
        fitPath = fitPath,
        profileId = profileId,
        routeId = routeId,
        fitExportedAtMs = fitExportedAt,
    )
}

/** Copies [totals] into the entity, keeping identity and metadata. */
internal fun ActivityEntity.withTotals(totals: RideTotals): ActivityEntity = copy(
    elapsedS = totals.elapsedS,
    movingS = totals.movingS,
    distanceM = totals.distanceM,
    ascentM = totals.ascentM,
    descentM = totals.descentM,
    avgSpeedMps = totals.avgSpeedMps,
    maxSpeedMps = totals.maxSpeedMps,
    avgHr = totals.avgHeartRateBpm,
    maxHr = totals.maxHeartRateBpm,
    avgCadence = totals.avgCadenceRpm,
    avgPower = totals.avgPowerW,
    maxPower = totals.maxPowerW,
    npPower = totals.normalizedPowerW,
    estAvgPower = totals.estAvgPowerW,
    calories = totals.calories,
)

internal fun RideSample.toEntity(activityId: Long) = TrackPointEntity(
    activityId = activityId,
    timeMs = timeMs,
    lat = latDeg,
    lon = lonDeg,
    altM = altitudeM,
    gpsAccuracyM = gpsAccuracyM,
    distanceM = distanceM,
    speedMps = speedMps,
    hr = heartRateBpm,
    cadence = cadenceRpm,
    power = powerW,
    estPower = estPowerW,
    tempC = temperatureC,
)

internal fun TrackPointEntity.toDomain() = RideSample(
    timeMs = timeMs,
    latDeg = lat,
    lonDeg = lon,
    altitudeM = altM,
    gpsAccuracyM = gpsAccuracyM,
    distanceM = distanceM,
    speedMps = speedMps,
    heartRateBpm = hr,
    cadenceRpm = cadence,
    powerW = power,
    estPowerW = estPower,
    temperatureC = tempC,
)

internal fun RideEvent.toEntity(activityId: Long) =
    EventEntity(activityId = activityId, timeMs = timeMs, type = type.toDbValue())

internal fun EventEntity.toDomain(): RideEvent? = type.toEnumOrNull<RideEventType>()?.let { RideEvent(timeMs, it) }

internal fun Lap.toEntity(activityId: Long) = LapEntity(
    activityId = activityId,
    lapIndex = index,
    startTimeMs = startTimeMs,
    endTimeMs = endTimeMs,
    distanceM = distanceM,
    movingS = movingS,
    avgSpeedMps = avgSpeedMps,
    maxSpeedMps = maxSpeedMps,
    avgHr = avgHeartRateBpm,
    maxHr = maxHeartRateBpm,
    avgCadence = avgCadenceRpm,
    avgPower = avgPowerW,
    maxPower = maxPowerW,
    ascentM = ascentM,
    trigger = trigger.toDbValue(),
)

internal fun LapEntity.toDomain() = Lap(
    index = lapIndex,
    startTimeMs = startTimeMs,
    endTimeMs = endTimeMs,
    distanceM = distanceM,
    movingS = movingS,
    avgSpeedMps = avgSpeedMps,
    maxSpeedMps = maxSpeedMps,
    ascentM = ascentM,
    trigger = trigger.toEnumOrNull<LapTrigger>() ?: LapTrigger.MANUAL,
    avgHeartRateBpm = avgHr,
    maxHeartRateBpm = maxHr,
    avgCadenceRpm = avgCadence,
    avgPowerW = avgPower,
    maxPowerW = maxPower,
)
