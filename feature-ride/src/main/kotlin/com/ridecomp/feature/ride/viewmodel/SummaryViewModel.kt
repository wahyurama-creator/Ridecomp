package com.ridecomp.feature.ride.viewmodel

import androidx.compose.runtime.Immutable
import androidx.lifecycle.viewModelScope
import com.ridecomp.core.engine.recording.LiveRide
import com.ridecomp.core.engine.recording.RideAction
import com.ridecomp.core.model.UnitSystem
import com.ridecomp.core.usecase.ObserveActivityUseCase
import com.ridecomp.core.usecase.ObserveLiveRideUseCase
import com.ridecomp.core.usecase.ObserveRideSettingsUseCase
import com.ridecomp.core.usecase.PerformRideActionUseCase
import com.ridecomp.core.usecase.RenameActivityUseCase
import com.ridecomp.ui.udf.UdfViewModel
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Immutable
data class SummaryUiState(
    val live: LiveRide = LiveRide(),
    val activityId: Long? = null,
    val name: String = "",
    val unitSystem: UnitSystem = UnitSystem.METRIC,
    val isWorking: Boolean = false,
)

sealed interface SummaryUiEvent {
    data class NameChanged(val name: String) : SummaryUiEvent

    data object Save : SummaryUiEvent

    data object Discard : SummaryUiEvent
}

sealed interface SummaryEffect {
    /** Saved: open the activity detail (export FIT there). */
    data class Saved(val activityId: Long) : SummaryEffect

    data object Discarded : SummaryEffect
}

/** Summary after FINISH (T1.12, 02-DESIGN §3.6): name the ride, then save or discard. */
class SummaryViewModel(
    private val observeLive: ObserveLiveRideUseCase,
    private val observeActivity: ObserveActivityUseCase,
    observeSettings: ObserveRideSettingsUseCase,
    private val rename: RenameActivityUseCase,
    private val performAction: PerformRideActionUseCase,
) : UdfViewModel<SummaryUiState, SummaryUiEvent, SummaryEffect>(SummaryUiState()) {
    init {
        viewModelScope.launch {
            observeLive().collect { live -> updateState { copy(live = live) } }
        }
        viewModelScope.launch {
            val id = observeLive.activeActivityId().filterNotNull().first()
            val name = observeActivity(id).filterNotNull().first().name
            updateState { copy(activityId = id, name = name) }
        }
        viewModelScope.launch {
            observeSettings().collect { settings -> updateState { copy(unitSystem = settings.unitSystem) } }
        }
    }

    override fun onEvent(event: SummaryUiEvent) {
        when (event) {
            is SummaryUiEvent.NameChanged -> updateState { copy(name = event.name) }
            SummaryUiEvent.Save -> save()
            SummaryUiEvent.Discard -> {
                updateState { copy(isWorking = true) }
                performAction(RideAction.DISCARD)
                sendEffect(SummaryEffect.Discarded)
            }
        }
    }

    private fun save() {
        val id = currentState.activityId ?: return
        updateState { copy(isWorking = true) }
        viewModelScope.launch {
            // The service saves the ride with the stored name, so rename first.
            rename(id, currentState.name)
            performAction(RideAction.SAVE)
            sendEffect(SummaryEffect.Saved(id))
        }
    }
}
