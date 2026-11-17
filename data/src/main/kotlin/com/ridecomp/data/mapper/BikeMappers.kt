package com.ridecomp.data.mapper

import com.ridecomp.core.model.Bike
import com.ridecomp.data.db.entity.BikeEntity

internal fun BikeEntity.toDomain() = Bike(
    id = id,
    name = name,
    profileId = profileId,
    weightKg = weightKg,
    wheelCircumferenceMm = wheelCircumferenceMm,
)

internal fun Bike.toEntity() = BikeEntity(
    id = id,
    name = name,
    profileId = profileId,
    weightKg = weightKg,
    wheelCircumferenceMm = wheelCircumferenceMm,
)
