package com.ridecomp.data.repository

import com.ridecomp.core.model.ActivePlanState
import com.ridecomp.core.model.BuiltInPlan
import com.ridecomp.core.model.CustomPlan
import com.ridecomp.core.model.Intensity
import com.ridecomp.core.model.PlanRef
import com.ridecomp.core.model.PlanSession
import com.ridecomp.core.repository.TrainingPlanRepository
import com.ridecomp.data.db.dao.SettingsDao
import com.ridecomp.data.db.entity.SettingEntity
import com.ridecomp.data.json.PlansJson
import com.ridecomp.data.mapper.SettingsKeys
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Plans in the `settings` table (T7.4): the rider's plans as one JSON value, the active plan as
 * `builtin:<NAME>` or `custom:<id>` with its start day. Part of backup & restore like other settings.
 */
class TrainingPlanRepositoryImpl(private val settings: SettingsDao) : TrainingPlanRepository {
    /** One read-modify-write of the JSON at a time. */
    private val mutex = Mutex()

    override fun observeCustomPlans(): Flow<List<CustomPlan>> =
        settings.observe(SettingsKeys.TRAINING_CUSTOM_PLANS).map(PlansJson::decode).distinctUntilChanged()

    override suspend fun saveCustomPlan(plan: CustomPlan): Long = mutex.withLock {
        val plans = PlansJson.decode(settings.get(SettingsKeys.TRAINING_CUSTOM_PLANS))
        val id = if (plan.id == 0L) (plans.maxOfOrNull { it.id } ?: 0L) + 1 else plan.id
        write(plans.filterNot { it.id == id } + plan.copy(id = id))
        id
    }

    override suspend fun deleteCustomPlan(id: Long) {
        mutex.withLock {
            write(
                PlansJson.decode(settings.get(SettingsKeys.TRAINING_CUSTOM_PLANS)).filterNot {
                    it.id ==
                        id
                },
            )
        }
    }

    override fun observeActivePlan(): Flow<ActivePlanState?> = combine(
        settings.observe(SettingsKeys.TRAINING_ACTIVE_PLAN),
        settings.observe(SettingsKeys.TRAINING_PLAN_START),
    ) { ref, start ->
        val parsed = ref?.toRef()
        val day = start?.toLongOrNull()
        if (parsed != null && day != null) ActivePlanState(parsed, day) else null
    }.distinctUntilChanged()

    override suspend fun setActivePlan(state: ActivePlanState?) {
        if (state == null) {
            settings.delete(SettingsKeys.TRAINING_ACTIVE_PLAN)
            settings.delete(SettingsKeys.TRAINING_PLAN_START)
            return
        }
        settings.upsertAll(
            listOf(
                SettingEntity(SettingsKeys.TRAINING_ACTIVE_PLAN, state.ref.toValue()),
                SettingEntity(SettingsKeys.TRAINING_PLAN_START, state.startEpochDay.toString()),
            ),
        )
    }

    override suspend fun getRideTarget(): PlanSession? {
        val value = settings.get(SettingsKeys.TRAINING_RIDE_TARGET) ?: return null
        val intensity = Intensity.entries.firstOrNull { it.name == value.substringBefore(TARGET_SEPARATOR) }
        val minutes = value.substringAfter(TARGET_SEPARATOR).toIntOrNull()
        return if (intensity != null && minutes != null) PlanSession(intensity, minutes) else null
    }

    override suspend fun setRideTarget(session: PlanSession?) {
        if (session == null) {
            settings.delete(SettingsKeys.TRAINING_RIDE_TARGET)
        } else {
            settings.upsert(
                SettingEntity(
                    SettingsKeys.TRAINING_RIDE_TARGET,
                    session.intensity.name + TARGET_SEPARATOR + session.durationMin,
                ),
            )
        }
    }

    private suspend fun write(plans: List<CustomPlan>) {
        settings.upsert(SettingEntity(SettingsKeys.TRAINING_CUSTOM_PLANS, PlansJson.encode(plans.sortedBy { it.id })))
    }

    private fun PlanRef.toValue(): String = when (this) {
        is PlanRef.BuiltIn -> BUILT_IN_PREFIX + plan.name
        is PlanRef.Custom -> CUSTOM_PREFIX + id
    }

    private fun String.toRef(): PlanRef? = when {
        startsWith(BUILT_IN_PREFIX) ->
            BuiltInPlan.entries.firstOrNull { it.name == removePrefix(BUILT_IN_PREFIX) }?.let(PlanRef::BuiltIn)
        startsWith(CUSTOM_PREFIX) -> removePrefix(CUSTOM_PREFIX).toLongOrNull()?.let(PlanRef::Custom)
        else -> null
    }

    private companion object {
        const val BUILT_IN_PREFIX = "builtin:"
        const val CUSTOM_PREFIX = "custom:"
        const val TARGET_SEPARATOR = ":"
    }
}
