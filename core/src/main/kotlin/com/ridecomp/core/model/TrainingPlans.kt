package com.ridecomp.core.model

/**
 * How hard a planned ride is (T7.4, D-94): the target zone, from heart rate (Z1..Z5 of HR max), power
 * (the same first five zones of FTP) or, without sensors, by feel.
 */
public enum class Intensity(public val zone: Int) {
    RECOVERY(1),
    ENDURANCE(2),
    TEMPO(3),
    THRESHOLD(4),
    VO2MAX(5),
}

/** One planned ride: how hard and how long. */
public data class PlanSession(val intensity: Intensity, val durationMin: Int)

/** Seven days from the plan's start weekday; null = rest day. */
public data class PlanWeek(val days: List<PlanSession?>) {
    init {
        require(days.size == TrainingPlanLimits.DAYS_PER_WEEK) { "A week has 7 days" }
    }
}

/** The ready-made plans; names and descriptions are localized in the UI. */
public enum class BuiltInPlan { ZONE2_BASE, RACE_PREP, GRAN_FONDO }

/** Which plan: a ready-made one or one the rider built. */
public sealed interface PlanRef {
    public data class BuiltIn(val plan: BuiltInPlan) : PlanRef

    public data class Custom(val id: Long) : PlanRef
}

public data class TrainingPlan(val ref: PlanRef, val weeks: List<PlanWeek>, val name: String = "") {
    val sessionCount: Int get() = weeks.sumOf { week -> week.days.count { it != null } }
}

/** A plan the rider built: one week repeated [weekCount] times (simple builder, D-94). */
public data class CustomPlan(val id: Long = 0, val name: String, val weekCount: Int, val week: PlanWeek) {
    public fun toPlan(): TrainingPlan = TrainingPlan(PlanRef.Custom(id), List(weekCount) { week }, name)
}

/** The plan being followed and the day it started (day 1 of week 1). */
public data class ActivePlanState(val ref: PlanRef, val startEpochDay: Long)

/** Today in the active plan. */
public data class PlanDay(
    val plan: TrainingPlan,
    /** 0-based. */
    val weekIndex: Int,
    /** 0-based day of that week. */
    val dayIndex: Int,
    /** Null = rest day. */
    val session: PlanSession?,
) {
    val weekNumber: Int get() = weekIndex + 1
}

public object TrainingPlanLimits {
    public const val DAYS_PER_WEEK: Int = 7
    public const val MAX_WEEKS: Int = 16
    public const val MIN_SESSION_MIN: Int = 10
    public const val MAX_SESSION_MIN: Int = 480
    public const val NAME_MAX_LENGTH: Int = 30
}
