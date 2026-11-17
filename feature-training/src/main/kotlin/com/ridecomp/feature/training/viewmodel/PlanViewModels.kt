package com.ridecomp.feature.training.viewmodel

import androidx.compose.runtime.Immutable
import androidx.lifecycle.viewModelScope
import com.ridecomp.core.model.CustomPlan
import com.ridecomp.core.model.Intensity
import com.ridecomp.core.model.PlanRef
import com.ridecomp.core.model.PlanSession
import com.ridecomp.core.model.PlanWeek
import com.ridecomp.core.model.TrainingPlan
import com.ridecomp.core.model.TrainingPlanLimits
import com.ridecomp.core.usecase.ActivePlan
import com.ridecomp.core.usecase.DeleteCustomPlanUseCase
import com.ridecomp.core.usecase.GetCustomPlanUseCase
import com.ridecomp.core.usecase.ObserveActivePlanUseCase
import com.ridecomp.core.usecase.ObservePlansUseCase
import com.ridecomp.core.usecase.PlanField
import com.ridecomp.core.usecase.SaveCustomPlanUseCase
import com.ridecomp.core.usecase.StartPlanUseCase
import com.ridecomp.core.usecase.StopPlanUseCase
import com.ridecomp.core.util.fold
import com.ridecomp.ui.udf.NoEffect
import com.ridecomp.ui.udf.UdfViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

@Immutable
data class PlansUiState(
    val plans: ImmutableList<TrainingPlan> = persistentListOf(),
    val active: ActivePlan? = null,
    val isLoaded: Boolean = false,
)

/** Plan list (T7.4): ready-made plans, the rider's own, and which one is followed. */
class PlansViewModel(observePlans: ObservePlansUseCase, observeActive: ObserveActivePlanUseCase) :
    UdfViewModel<PlansUiState, Unit, NoEffect>(PlansUiState()) {
    init {
        viewModelScope.launch {
            combine(observePlans(), observeActive()) { plans, active ->
                PlansUiState(plans.toImmutableList(), active, isLoaded = true)
            }.collect { state -> updateState { state } }
        }
    }

    override fun onEvent(event: Unit) = Unit
}

@Immutable
data class PlanDetailUiState(
    val plan: TrainingPlan? = null,
    /** This plan is the one followed; [active] tells today's day in it. */
    val active: ActivePlan? = null,
    val isDeleteAsked: Boolean = false,
    val isMissing: Boolean = false,
) {
    val isFollowed: Boolean get() = plan != null && active?.plan?.ref == plan.ref
}

sealed interface PlanDetailUiEvent {
    data object Start : PlanDetailUiEvent

    data object Stop : PlanDetailUiEvent

    data object AskDelete : PlanDetailUiEvent

    data object ConfirmDelete : PlanDetailUiEvent

    data object DismissDelete : PlanDetailUiEvent
}

sealed interface PlanDetailEffect {
    data object Deleted : PlanDetailEffect
}

/** Use cases of the plan detail, grouped to keep the constructor short (KT-05). */
data class PlanDetailUseCases(
    val observePlans: ObservePlansUseCase,
    val observeActive: ObserveActivePlanUseCase,
    val start: StartPlanUseCase,
    val stop: StopPlanUseCase,
    val delete: DeleteCustomPlanUseCase,
)

/** One plan week by week; start or stop following it, delete the rider's own (T7.4). */
class PlanDetailViewModel(private val ref: PlanRef, private val useCases: PlanDetailUseCases) :
    UdfViewModel<PlanDetailUiState, PlanDetailUiEvent, PlanDetailEffect>(PlanDetailUiState()) {
    init {
        viewModelScope.launch {
            combine(useCases.observePlans(), useCases.observeActive()) { plans, active ->
                plans.firstOrNull { it.ref == ref } to active
            }.collect { (plan, active) ->
                updateState { copy(plan = plan, active = active, isMissing = plan == null) }
            }
        }
    }

    override fun onEvent(event: PlanDetailUiEvent) {
        when (event) {
            PlanDetailUiEvent.Start -> viewModelScope.launch { useCases.start(ref) }
            PlanDetailUiEvent.Stop -> viewModelScope.launch { useCases.stop() }
            PlanDetailUiEvent.AskDelete -> if (ref is PlanRef.Custom) updateState { copy(isDeleteAsked = true) }
            PlanDetailUiEvent.DismissDelete -> updateState { copy(isDeleteAsked = false) }
            PlanDetailUiEvent.ConfirmDelete -> (ref as? PlanRef.Custom)?.let { custom ->
                updateState { copy(isDeleteAsked = false) }
                viewModelScope.launch {
                    useCases.delete(custom.id)
                    sendEffect(PlanDetailEffect.Deleted)
                }
            }
        }
    }
}

/** Plan builder (T7.4): a name, how many weeks, and one week of rides repeated. */
@Immutable
data class PlanBuilderUiState(
    val id: Long = 0,
    val name: String = "",
    val weekCount: Int = DEFAULT_WEEKS,
    /** Seven days; null = rest. Starts with a simple zone 2 week. */
    val days: ImmutableList<PlanSession?> = DEFAULT_WEEK.toImmutableList(),
    /** Day being edited in the dialog. */
    val editingDay: Int? = null,
    val invalidFields: Set<PlanField> = emptySet(),
    val isLoaded: Boolean = false,
) {
    companion object {
        const val DEFAULT_WEEKS = 4
        private const val DEFAULT_MIN = 60
        private val RIDE = PlanSession(Intensity.ENDURANCE, DEFAULT_MIN)
        val DEFAULT_WEEK: List<PlanSession?> = listOf(RIDE, null, RIDE, null, null, RIDE, null)
    }
}

sealed interface PlanBuilderUiEvent {
    data class NameChanged(val name: String) : PlanBuilderUiEvent

    data class WeeksPicked(val weeks: Int) : PlanBuilderUiEvent

    data class EditDay(val day: Int) : PlanBuilderUiEvent

    /** Null = rest day. */
    data class DaySet(val session: PlanSession?) : PlanBuilderUiEvent

    data object DismissDay : PlanBuilderUiEvent

    data object Save : PlanBuilderUiEvent
}

sealed interface PlanBuilderEffect {
    data class Saved(val ref: PlanRef) : PlanBuilderEffect
}

class PlanBuilderViewModel(
    private val planId: Long?,
    private val getPlan: GetCustomPlanUseCase,
    private val savePlan: SaveCustomPlanUseCase,
) : UdfViewModel<PlanBuilderUiState, PlanBuilderUiEvent, PlanBuilderEffect>(PlanBuilderUiState()) {
    init {
        viewModelScope.launch {
            val plan = planId?.let { getPlan(it) }
            updateState {
                if (plan == null) {
                    copy(isLoaded = true)
                } else {
                    copy(
                        id = plan.id,
                        name = plan.name,
                        weekCount = plan.weekCount,
                        days = plan.week.days.toImmutableList(),
                        isLoaded = true,
                    )
                }
            }
        }
    }

    override fun onEvent(event: PlanBuilderUiEvent) {
        when (event) {
            is PlanBuilderUiEvent.NameChanged ->
                updateState { copy(name = event.name.take(TrainingPlanLimits.NAME_MAX_LENGTH)) }
            is PlanBuilderUiEvent.WeeksPicked -> updateState { copy(weekCount = event.weeks) }
            is PlanBuilderUiEvent.EditDay -> updateState { copy(editingDay = event.day) }
            PlanBuilderUiEvent.DismissDay -> updateState { copy(editingDay = null) }
            is PlanBuilderUiEvent.DaySet -> updateState {
                val day = editingDay ?: return@updateState this
                copy(
                    days = days.toMutableList().also { it[day] = event.session }.toImmutableList(),
                    editingDay = null,
                    invalidFields = invalidFields - PlanField.SESSIONS,
                )
            }
            PlanBuilderUiEvent.Save -> save()
        }
    }

    private fun save() {
        val state = currentState
        viewModelScope.launch {
            savePlan(CustomPlan(state.id, state.name, state.weekCount, PlanWeek(state.days))).fold(
                onSuccess = { id -> sendEffect(PlanBuilderEffect.Saved(PlanRef.Custom(id))) },
                onFailure = { error -> updateState { copy(invalidFields = error.fields) } },
            )
        }
    }
}
