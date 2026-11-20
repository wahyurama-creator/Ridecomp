package com.ridecomp.feature.training.model

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.ridecomp.core.config.Units
import com.ridecomp.core.engine.efforts.BestEffortKey
import com.ridecomp.core.engine.efforts.BestEffortType
import com.ridecomp.core.model.ActivityKind
import com.ridecomp.core.util.UnitFormatter
import com.ridecomp.feature.training.R
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/** "10 km tercepat", "Tercepat 1 mnt", "Power terbaik 5 mnt", "Ride terjauh" (12-FEATURE-SPECS §8, T11.8). */
@Composable
fun BestEffortKey.label(): String = when (type) {
    // Record distances are fixed metric distances, also in imperial units (like other ride computers).
    BestEffortType.DISTANCE -> stringResource(
        R.string.record_fastest_distance,
        (target / Units.METERS_PER_KM).roundToInt(),
    )
    BestEffortType.SPEED -> stringResource(R.string.record_best_speed, window())
    BestEffortType.POWER -> stringResource(R.string.record_best_power, window())
    BestEffortType.OTHER -> stringResource(otherLabelRes())
}

/** The record value with its unit: a time over a distance, speed, watts, distance or elevation. */
fun BestEffortKey.valueText(value: Double, formatter: UnitFormatter): String = when (type) {
    BestEffortType.DISTANCE -> formatter.duration((value * Units.MS_PER_SECOND).roundToLong())
    BestEffortType.SPEED -> "${formatter.speed(value)} ${formatter.speedUnit}"
    BestEffortType.POWER -> "${value.roundToInt()} $WATT"
    BestEffortType.OTHER -> when (this) {
        BestEffortKey.LONGEST_RIDE -> "${formatter.distance(value)} ${formatter.distanceUnit}"
        BestEffortKey.MOST_ASCENT -> "${formatter.elevation(value)} ${formatter.elevationUnit}"
        else -> "${formatter.speed(value)} ${formatter.speedUnit}"
    }
}

@Composable
private fun BestEffortKey.window(): String {
    val seconds = target.roundToLong()
    return if (seconds < Units.SECONDS_PER_MINUTE) {
        stringResource(R.string.record_window_seconds, seconds)
    } else {
        stringResource(R.string.record_window_minutes, seconds / Units.SECONDS_PER_MINUTE)
    }
}

@StringRes
private fun BestEffortKey.otherLabelRes(): Int = when (this) {
    BestEffortKey.LONGEST_RIDE -> R.string.record_longest_ride
    BestEffortKey.MOST_ASCENT -> R.string.record_most_ascent
    else -> R.string.record_fastest_avg_speed
}

/** Section title of a record group. */
@StringRes
fun BestEffortType.titleRes(): Int = when (this) {
    BestEffortType.DISTANCE -> R.string.records_section_distance
    BestEffortType.SPEED -> R.string.records_section_speed
    BestEffortType.POWER -> R.string.records_section_power
    BestEffortType.OTHER -> R.string.records_section_other
}

/** Profile kind chip label. */
@StringRes
fun ActivityKind.labelRes(): Int = when (this) {
    ActivityKind.ROAD -> R.string.records_kind_road
    ActivityKind.GRAVEL -> R.string.records_kind_gravel
    ActivityKind.MTB -> R.string.records_kind_mtb
    ActivityKind.INDOOR -> R.string.records_kind_indoor
    ActivityKind.COMMUTE -> R.string.records_kind_commute
    ActivityKind.FOLDING -> R.string.records_kind_folding
    ActivityKind.CUSTOM -> R.string.records_kind_custom
}

internal const val WATT = "W"
