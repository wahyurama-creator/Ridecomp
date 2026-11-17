package com.ridecomp.core.model

/**
 * The ready-made plans (T7.4, D-94): common amateur structures, progressive load with an easier fourth
 * week. Each week starts on the day the rider starts the plan; null = rest.
 */
public object BuiltInPlans {
    private fun s(intensity: Intensity, minutes: Int) = PlanSession(intensity, minutes)

    private fun week(vararg days: PlanSession?) = PlanWeek(days.toList())

    private val Z1 = Intensity.RECOVERY
    private val Z2 = Intensity.ENDURANCE
    private val Z3 = Intensity.TEMPO
    private val Z4 = Intensity.THRESHOLD
    private val Z5 = Intensity.VO2MAX

    /** Aerobic base: mostly zone 2, time grows each week, week 4 easier. */
    private val ZONE2_BASE = listOf(
        week(s(Z2, 45), null, s(Z2, 45), null, s(Z1, 30), s(Z2, 60), null),
        week(s(Z2, 60), null, s(Z2, 60), null, s(Z1, 30), s(Z2, 75), null),
        week(s(Z2, 60), null, s(Z2, 75), null, s(Z1, 30), s(Z2, 90), null),
        week(s(Z2, 45), null, s(Z1, 30), null, null, s(Z2, 60), null),
    )

    /** Race preparation: base, then threshold and VO2max work, taper in the last week. */
    private val RACE_PREP = listOf(
        week(s(Z2, 60), null, s(Z3, 60), null, s(Z1, 30), s(Z2, 90), null),
        week(s(Z2, 60), null, s(Z4, 60), null, s(Z1, 30), s(Z2, 105), null),
        week(s(Z2, 60), null, s(Z4, 60), s(Z1, 30), s(Z3, 60), s(Z2, 120), null),
        week(s(Z2, 45), null, s(Z3, 45), null, null, s(Z2, 75), null),
        week(s(Z2, 60), null, s(Z5, 60), s(Z1, 30), s(Z4, 60), s(Z2, 120), null),
        week(s(Z2, 60), null, s(Z5, 60), s(Z1, 30), s(Z4, 75), s(Z2, 135), null),
        week(s(Z2, 45), null, s(Z4, 45), null, s(Z1, 30), s(Z2, 90), null),
        week(s(Z2, 45), null, s(Z5, 45), null, s(Z1, 30), s(Z3, 30), null),
    )

    /** Gran Fondo 100 km: the long ride grows to ~4 hours, tempo in the week. */
    private val GRAN_FONDO = listOf(
        week(s(Z2, 60), null, s(Z2, 60), null, s(Z1, 30), s(Z2, 120), null),
        week(s(Z2, 60), null, s(Z3, 60), null, s(Z1, 30), s(Z2, 150), null),
        week(s(Z2, 75), null, s(Z3, 60), null, s(Z1, 30), s(Z2, 180), null),
        week(s(Z2, 45), null, s(Z2, 60), null, null, s(Z2, 120), null),
        week(s(Z2, 75), null, s(Z3, 75), null, s(Z1, 30), s(Z2, 210), null),
        week(s(Z2, 75), null, s(Z4, 60), null, s(Z1, 30), s(Z2, 240), null),
        week(s(Z2, 60), null, s(Z3, 60), null, s(Z1, 30), s(Z2, 180), null),
        week(s(Z2, 45), null, s(Z3, 30), null, s(Z1, 30), null, null),
    )

    public fun plan(builtIn: BuiltInPlan): TrainingPlan = TrainingPlan(
        PlanRef.BuiltIn(builtIn),
        when (builtIn) {
            BuiltInPlan.ZONE2_BASE -> ZONE2_BASE
            BuiltInPlan.RACE_PREP -> RACE_PREP
            BuiltInPlan.GRAN_FONDO -> GRAN_FONDO
        },
    )

    public val ALL: List<TrainingPlan> = BuiltInPlan.entries.map(::plan)
}
