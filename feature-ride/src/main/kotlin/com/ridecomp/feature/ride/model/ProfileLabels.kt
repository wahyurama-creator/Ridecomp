package com.ridecomp.feature.ride.model

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.ridecomp.core.model.ActivityKind
import com.ridecomp.core.model.ActivityProfile
import com.ridecomp.core.model.Bike
import com.ridecomp.core.util.toPlainString
import com.ridecomp.feature.ride.R

@StringRes
fun ActivityKind.labelRes(): Int = when (this) {
    ActivityKind.ROAD -> R.string.profile_kind_road
    ActivityKind.GRAVEL -> R.string.profile_kind_gravel
    ActivityKind.MTB -> R.string.profile_kind_mtb
    ActivityKind.INDOOR -> R.string.profile_kind_indoor
    ActivityKind.COMMUTE -> R.string.profile_kind_commute
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
