package com.ridecomp.feature.ride.model

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import com.ridecomp.core.config.Units
import com.ridecomp.core.model.ActivityKind
import com.ridecomp.core.model.ActivityProfile
import com.ridecomp.core.model.Bike
import com.ridecomp.core.model.BikeComponent
import com.ridecomp.core.model.ComponentType
import com.ridecomp.core.model.ComponentWear
import com.ridecomp.core.util.toPlainString
import com.ridecomp.feature.ride.R
import java.text.NumberFormat
import kotlin.math.roundToLong

@StringRes
fun ActivityKind.labelRes(): Int = when (this) {
    ActivityKind.ROAD -> R.string.profile_kind_road
    ActivityKind.GRAVEL -> R.string.profile_kind_gravel
    ActivityKind.MTB -> R.string.profile_kind_mtb
    ActivityKind.INDOOR -> R.string.profile_kind_indoor
    ActivityKind.COMMUTE -> R.string.profile_kind_commute
    ActivityKind.FOLDING -> R.string.profile_kind_folding
    ActivityKind.CUSTOM -> R.string.profile_kind_custom
}

/** Rider-chosen name, or the localized kind name for built-ins. */
@Composable
fun ActivityProfile.displayName(): String = name.ifBlank { stringResource(kind.labelRes()) }

/** Rider-chosen name, or "My bike" for the bike created with the app (D-89). */
@Composable
fun Bike.displayName(): String = name.ifBlank { stringResource(R.string.bike_default_name) }

/** "Road · 9 kg · 2105 mm": type, weight and wheel size of a bike. */
@Composable
fun bikeSummary(bike: Bike, profile: ActivityProfile?): String = stringResource(
    R.string.bike_summary,
    profile?.displayName() ?: stringResource(ActivityKind.ROAD.labelRes()),
    bike.weightKg.toPlainString(),
    bike.wheelCircumferenceMm,
)

@StringRes
fun ComponentType.labelRes(): Int = when (this) {
    ComponentType.CHAIN -> R.string.component_chain
    ComponentType.TIRES -> R.string.component_tires
    ComponentType.BRAKE_PADS -> R.string.component_brake_pads
    ComponentType.CASSETTE -> R.string.component_cassette
    ComponentType.SERVICE -> R.string.component_service
    ComponentType.OTHER -> R.string.component_other
}

/** Rider-chosen name, or the localized type name (T7.3). */
@Composable
fun BikeComponent.displayName(): String = name.ifBlank { stringResource(type.labelRes()) }

/** "3.120 / 3.000 km" or "1.234 km" without a limit, whole km grouped for the locale (T7.3). */
@Composable
fun ComponentWear.distanceText(): String {
    val locale = LocalConfiguration.current.locales[0]
    val format = remember(locale) { NumberFormat.getIntegerInstance(locale) }
    val done = format.format((distanceM / Units.METERS_PER_KM).roundToLong())
    val limit = component.limitM ?: return stringResource(R.string.component_km, done)
    return stringResource(R.string.component_km_of, done, format.format((limit / Units.METERS_PER_KM).roundToLong()))
}
