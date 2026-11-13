package com.ridecomp.data.mapper

import com.ridecomp.core.model.ActivityKind
import com.ridecomp.core.model.ActivityProfile
import com.ridecomp.core.model.RidingPosition
import com.ridecomp.core.model.ScreenMode
import com.ridecomp.core.model.Surface
import com.ridecomp.data.db.entity.ActivityProfileEntity
import com.ridecomp.data.json.DataPagesJson

internal fun ActivityProfileEntity.toDomain() = ActivityProfile(
    id = id,
    name = name,
    // An unknown kind (newer app version) is treated as a custom profile, never dropped.
    kind = kind.toEnumOrNull<ActivityKind>() ?: ActivityKind.CUSTOM,
    isGpsEnabled = isGpsEnabled,
    dataPages = dataPagesJson?.let(DataPagesJson::decode),
    isAutoPauseEnabled = isAutoPauseEnabled,
    autoLapM = autoLapM,
    screenMode = screenMode?.toEnumOrNull<ScreenMode>(),
    surface = surface.toEnumOrNull<Surface>() ?: Surface.ROAD,
    ridingPosition = ridingPosition.toEnumOrNull<RidingPosition>() ?: RidingPosition.HOODS,
    fitSport = fitSport,
    fitSubSport = fitSubSport,
    sortOrder = sortOrder,
)

internal fun ActivityProfile.toEntity() = ActivityProfileEntity(
    id = id,
    name = name,
    kind = kind.toDbValue(),
    isGpsEnabled = isGpsEnabled,
    dataPagesJson = dataPages?.let(DataPagesJson::encode),
    isAutoPauseEnabled = isAutoPauseEnabled,
    autoPauseSpeedMps = null,
    autoPauseDelayS = null,
    idlePromptMin = null,
    drinkIntervalMin = null,
    eatIntervalMin = null,
    reminderByDistanceM = null,
    autoLapM = autoLapM,
    screenMode = screenMode?.toDbValue(),
    surface = surface.toDbValue(),
    ridingPosition = ridingPosition.toDbValue(),
    fitSport = fitSport,
    fitSubSport = fitSubSport,
    sortOrder = sortOrder,
)
