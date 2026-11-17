package com.ridecomp.core.usecase

import com.ridecomp.core.model.ActivePlanState
import com.ridecomp.core.model.BuiltInPlans
import com.ridecomp.core.model.CustomPlan
import com.ridecomp.core.model.PlanDay
import com.ridecomp.core.model.PlanRef
import com.ridecomp.core.model.PlanSession
import com.ridecomp.core.model.TrainingPlan
import com.ridecomp.core.model.TrainingPlanLimits
import com.ridecomp.core.repository.TrainingPlanRepository
import com.ridecomp.core.util.Outcome
import com.ridecomp.core.util.asFailure
import com.ridecomp.core.util.asSuccess
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** Ready-made plans first, then the rider's own (T7.4). */
public class ObservePlansUseCase(private val repository: TrainingPlanRepository) {
    public operator fun invoke(): Flow<List<TrainingPlan>> =
        repository.observeCustomPlans().map { custom -> BuiltInPlans.ALL + custom.map { it.toPlan() } }
}

public class GetCustomPlanUseCase(private val repository: TrainingPlanRepository) {
    public suspend operator fun invoke(id: Long): CustomPlan? =
        repository.observeCustomPlans().first().firstOrNull { it.id == id }
}

/** The plan being followed: today's day in it, or null [ActivePlan.today] once the plan is over. */
public data class ActivePlan(val plan: TrainingPlan, val startEpochDay: Long, val today: PlanDay?)

public class ObserveActivePlanUseCase(
    private val repository: TrainingPlanRepository,
    private val calendar: TrainingCalendar,
) {
    public operator fun invoke(): Flow<ActivePlan?> =
        combine(repository.observeActivePlan(), repository.observeCustomPlans()) { state, custom ->
            state?.let { active -> resolve(active.ref, custom)?.let { plan -> activePlan(plan, active) } }
        }

    private fun activePlan(plan: TrainingPlan, state: ActivePlanState): ActivePlan {
        val dayNumber = calendar.today().toEpochDay() - state.startEpochDay
        val weekIndex = Math.floorDiv(dayNumber, TrainingPlanLimits.DAYS_PER_WEEK.toLong()).toInt()
        val dayIndex = Math.floorMod(dayNumber, TrainingPlanLimits.DAYS_PER_WEEK.toLong()).toInt()
        val week = plan.weeks.getOrNull(weekIndex)
        val today = week?.let { PlanDay(plan, weekIndex, dayIndex, it.days[dayIndex]) }
        return ActivePlan(plan, state.startEpochDay, today)
    }
}

internal fun resolve(ref: PlanRef, custom: List<CustomPlan>): TrainingPlan? = when (ref) {
    is PlanRef.BuiltIn -> BuiltInPlans.plan(ref.plan)
    is PlanRef.Custom -> custom.firstOrNull { it.id == ref.id }?.toPlan()
}

/** Starts following a plan from today (day 1 of week 1); replaces any other plan. */
public class StartPlanUseCase(private val repository: TrainingPlanRepository, private val calendar: TrainingCalendar) {
    public suspend operator fun invoke(ref: PlanRef) {
        repository.setActivePlan(ActivePlanState(ref, calendar.today().toEpochDay()))
    }
}

public class StopPlanUseCase(private val repository: TrainingPlanRepository) {
    public suspend operator fun invoke() {
        repository.setActivePlan(null)
    }
}

/** Plan builder fields that can be invalid. */
public enum class PlanField { WEEKS, SESSIONS }

public data class InvalidPlan(val fields: Set<PlanField>)

/** Saves a plan from the builder: 1..16 weeks, at least one ride of 10..480 min a week. */
public class SaveCustomPlanUseCase(private val repository: TrainingPlanRepository) {
    public suspend operator fun invoke(plan: CustomPlan): Outcome<Long, InvalidPlan> {
        val sessions = plan.week.days.filterNotNull()
        val minutes = TrainingPlanLimits.MIN_SESSION_MIN..TrainingPlanLimits.MAX_SESSION_MIN
        val invalid = buildSet {
            if (plan.weekCount !in 1..TrainingPlanLimits.MAX_WEEKS) add(PlanField.WEEKS)
            if (sessions.isEmpty() || sessions.any { it.durationMin !in minutes }) add(PlanField.SESSIONS)
        }
        if (invalid.isNotEmpty()) return InvalidPlan(invalid).asFailure()
        return repository.saveCustomPlan(plan.copy(name = plan.name.trim().take(TrainingPlanLimits.NAME_MAX_LENGTH)))
            .asSuccess()
    }
}

/** Deletes a built plan; following it stops too. */
public class DeleteCustomPlanUseCase(private val repository: TrainingPlanRepository) {
    public suspend operator fun invoke(id: Long) {
        if (repository.observeActivePlan().first()?.ref == PlanRef.Custom(id)) repository.setActivePlan(null)
        repository.deleteCustomPlan(id)
    }
}

/**
 * Today's planned session for the ride being started (T7.4b): stored with the ride so a recovered ride
 * is still measured against it; a ride without a session clears it.
 */
public class RideSessionUseCase(
    private val repository: TrainingPlanRepository,
    private val observeActivePlan: ObserveActivePlanUseCase,
) {
    public suspend fun forNewRide(withSession: Boolean): PlanSession? {
        val session = if (withSession) observeActivePlan().first()?.today?.session else null
        repository.setRideTarget(session)
        return session
    }

    public suspend fun forRecoveredRide(): PlanSession? = repository.getRideTarget()
}
