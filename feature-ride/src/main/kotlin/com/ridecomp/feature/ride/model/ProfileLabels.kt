package com.ridecomp.feature.ride.model

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.ridecomp.core.model.ActivityKind
import com.ridecomp.core.model.ActivityProfile
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
