package com.ridecomp.core.model

import com.ridecomp.core.config.Units

/**
 * Parts that wear with distance (T7.3, D-93), with a usual service interval as the suggested limit.
 * Stable names: stored in `bike_components.type`.
 */
public enum class ComponentType(public val defaultLimitKm: Int?) {
    CHAIN(3_000),
    TIRES(4_000),
    BRAKE_PADS(2_000),
    CASSETTE(10_000),
    SERVICE(1_500),
    OTHER(null),
}

/** A part on a bike; its distance is counted from the rides on that bike since [installedAtMs]. */
public data class BikeComponent(
    val id: Long = 0,
    val bikeId: Long,
    val type: ComponentType,
    /** Rider's name ("Continental GP5000"); blank shows the type name. */
    val name: String = "",
    val installedAtMs: Long,
    /** Distance it already had when added (a used part). */
    val initialDistanceM: Double = 0.0,
    /** Service/replace reminder; null = none. */
    val limitM: Double? = type.defaultLimitKm?.let { it * Units.METERS_PER_KM },
)

/** A part with the distance it has done: [BikeComponent.initialDistanceM] + finished rides on its bike since fitted. */
public data class ComponentWear(val component: BikeComponent, val distanceM: Double) {
    val isDue: Boolean get() = component.limitM?.let { distanceM >= it } == true

    /** 0..1 of the limit (capped), null without a limit. */
    val fraction: Float? get() = component.limitM?.takeIf {
        it > 0
    }?.let { (distanceM / it).coerceIn(0.0, 1.0).toFloat() }
}
