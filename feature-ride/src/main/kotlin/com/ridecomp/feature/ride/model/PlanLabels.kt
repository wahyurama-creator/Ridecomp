package com.ridecomp.feature.ride.model

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.ridecomp.core.model.BuiltInPlan
import com.ridecomp.core.model.Intensity
import com.ridecomp.core.model.PlanRef
import com.ridecomp.core.model.PlanSession
import com.ridecomp.core.model.TrainingPlan
import com.ridecomp.feature.ride.R

/** "Zona 2 · Daya tahan" (same words as the Training tab, D-94). */
@StringRes
fun Intensity.titleRes(): Int = when (this) {
    Intensity.RECOVERY -> R.string.session_intensity_recovery
    Intensity.ENDURANCE -> R.string.session_intensity_endurance
    Intensity.TEMPO -> R.string.session_intensity_tempo
    Intensity.THRESHOLD -> R.string.session_intensity_threshold
    Intensity.VO2MAX -> R.string.session_intensity_vo2max
}

/** How the zone feels, for riders without sensors. */
@StringRes
fun Intensity.feelRes(): Int = when (this) {
    Intensity.RECOVERY -> R.string.session_feel_recovery
    Intensity.ENDURANCE -> R.string.session_feel_endurance
    Intensity.TEMPO -> R.string.session_feel_tempo
    Intensity.THRESHOLD -> R.string.session_feel_threshold
    Intensity.VO2MAX -> R.string.session_feel_vo2max
}

/** "Zona 2 · Daya tahan · 45 mnt". */
@Composable
fun PlanSession.summary(): String =
    stringResource(R.string.session_summary, stringResource(intensity.titleRes()), durationMin)

@Composable
fun TrainingPlan.displayName(): String = when (val ref = ref) {
    is PlanRef.BuiltIn -> stringResource(
        when (ref.plan) {
            BuiltInPlan.ZONE2_BASE -> R.string.session_plan_zone2_base
            BuiltInPlan.RACE_PREP -> R.string.session_plan_race_prep
            BuiltInPlan.GRAN_FONDO -> R.string.session_plan_gran_fondo
        },
    )
    is PlanRef.Custom -> name.ifBlank { stringResource(R.string.session_plan_custom) }
}
