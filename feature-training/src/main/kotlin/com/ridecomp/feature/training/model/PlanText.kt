package com.ridecomp.feature.training.model

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.ridecomp.core.model.BuiltInPlan
import com.ridecomp.core.model.Intensity
import com.ridecomp.core.model.PlanRef
import com.ridecomp.core.model.PlanSession
import com.ridecomp.core.model.TrainingPlan
import com.ridecomp.feature.training.R

@StringRes
fun BuiltInPlan.nameRes(): Int = when (this) {
    BuiltInPlan.ZONE2_BASE -> R.string.plan_zone2_base
    BuiltInPlan.RACE_PREP -> R.string.plan_race_prep
    BuiltInPlan.GRAN_FONDO -> R.string.plan_gran_fondo
}

@StringRes
fun BuiltInPlan.descriptionRes(): Int = when (this) {
    BuiltInPlan.ZONE2_BASE -> R.string.plan_zone2_base_description
    BuiltInPlan.RACE_PREP -> R.string.plan_race_prep_description
    BuiltInPlan.GRAN_FONDO -> R.string.plan_gran_fondo_description
}

/** Localized name of a ready-made plan, the rider's name of their own (or "My plan"). */
@Composable
fun TrainingPlan.displayName(): String = when (val ref = ref) {
    is PlanRef.BuiltIn -> stringResource(ref.plan.nameRes())
    is PlanRef.Custom -> name.ifBlank { stringResource(R.string.plan_custom_default_name) }
}

/** "Zona 2 · Daya tahan". */
@StringRes
fun Intensity.titleRes(): Int = when (this) {
    Intensity.RECOVERY -> R.string.intensity_recovery
    Intensity.ENDURANCE -> R.string.intensity_endurance
    Intensity.TEMPO -> R.string.intensity_tempo
    Intensity.THRESHOLD -> R.string.intensity_threshold
    Intensity.VO2MAX -> R.string.intensity_vo2max
}

/** How it feels and how to ride it, for riders without sensors too. */
@StringRes
fun Intensity.howToRes(): Int = when (this) {
    Intensity.RECOVERY -> R.string.intensity_recovery_how
    Intensity.ENDURANCE -> R.string.intensity_endurance_how
    Intensity.TEMPO -> R.string.intensity_tempo_how
    Intensity.THRESHOLD -> R.string.intensity_threshold_how
    Intensity.VO2MAX -> R.string.intensity_vo2max_how
}

/** "Zona 2 · Daya tahan · 60 mnt", or "Istirahat". */
@Composable
fun PlanSession?.summary(): String = this?.let {
    stringResource(R.string.plan_session_summary, stringResource(it.intensity.titleRes()), it.durationMin)
} ?: stringResource(R.string.plan_rest_day)

/** Navigation argument of a plan: `builtin:ZONE2_BASE` or `custom:3`. */
object PlanRefArg {
    private const val BUILT_IN = "builtin:"
    private const val CUSTOM = "custom:"

    fun encode(ref: PlanRef): String = when (ref) {
        is PlanRef.BuiltIn -> BUILT_IN + ref.plan.name
        is PlanRef.Custom -> CUSTOM + ref.id
    }

    fun decode(value: String?): PlanRef? = when {
        value == null -> null
        value.startsWith(BUILT_IN) ->
            BuiltInPlan.entries.firstOrNull { it.name == value.removePrefix(BUILT_IN) }?.let(PlanRef::BuiltIn)
        value.startsWith(CUSTOM) -> value.removePrefix(CUSTOM).toLongOrNull()?.let(PlanRef::Custom)
        else -> null
    }
}
