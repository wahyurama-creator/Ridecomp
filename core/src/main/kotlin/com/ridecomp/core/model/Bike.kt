package com.ridecomp.core.model

import com.ridecomp.core.config.ProfileDefaults

/**
 * A bike the rider saved once and picks before each ride (D-89). Its activity profile decides the data
 * pages, GPS and FIT sport; its weight and wheel size feed estimated power and the speed sensor.
 */
public data class Bike(
    val id: Long = 0,
    /** Rider-chosen name; blank for the bike created automatically, shown as a localized "My bike". */
    val name: String = "",
    /** The bike type: an activity profile id (`activity_profiles.id`). */
    val profileId: Long,
    val weightKg: Double = ProfileDefaults.BIKE_WEIGHT_KG,
    val wheelCircumferenceMm: Int = ProfileDefaults.WHEEL_CIRCUMFERENCE_MM,
)

/** The rider on this bike: its weight and wheel size replace the rider profile's (D-89). */
public fun RiderProfile.withBike(bike: Bike): RiderProfile =
    copy(bikeWeightKg = bike.weightKg, wheelCircumferenceMm = bike.wheelCircumferenceMm)
