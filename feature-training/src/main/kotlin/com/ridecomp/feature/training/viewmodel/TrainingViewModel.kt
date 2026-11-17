package com.ridecomp.feature.training.viewmodel

import androidx.compose.runtime.Immutable
import androidx.lifecycle.viewModelScope
import com.ridecomp.core.model.UnitSystem
import com.ridecomp.core.usecase.ActivePlan
import com.ridecomp.core.usecase.ExtendTrainingToTodayUseCase
import com.ridecomp.core.usecase.LoadWeekZonesUseCase
import com.ridecomp.core.usecase.ObserveActivePlanUseCase
import com.ridecomp.core.usecase.ObserveRideSettingsUseCase
import com.ridecomp.core.usecase.ObserveTrainingUseCase
import com.ridecomp.core.usecase.TrainingOverview
import com.ridecomp.ui.udf.NoEffect
import com.ridecomp.ui.udf.UdfViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@Immutable
data class TrainingUiState(
    val overview: TrainingOverview? = null,
    /** Seconds in heart-rate zones Z1..Z5 this week; empty without HR data or HR max. */
    val zonesS: ImmutableList<Long> = persistentListOf(),
    val unitSystem: UnitSystem = UnitSystem.METRIC,
    /** The plan being followed (T7.4); null = none. */
    val activePlan: ActivePlan? = null,
) {
    val hasLoad: Boolean get() = overview?.methods?.isNotEmpty() == true
}

/** Training tab (T4.4, 13-UI-SCREENS #20): fitness, fatigue, form, 8-week trend, this week, zones. */
class TrainingViewModel(
    observeTraining: ObserveTrainingUseCase,
    loadZones: LoadWeekZonesUseCase,
    observeSettings: ObserveRideSettingsUseCase,
    extendToToday: ExtendTrainingToTodayUseCase,
    observeActivePlan: ObserveActivePlanUseCase,
) : UdfViewModel<TrainingUiState, Unit, NoEffect>(TrainingUiState()) {
    init {
        viewModelScope.launch { observeActivePlan().collect { plan -> updateState { copy(activePlan = plan) } } }
        // Days without rides since the last one: fitness and fatigue decay up to today.
        viewModelScope.launch { extendToToday() }
        viewModelScope.launch {
            observeTraining().collectLatest { overview ->
                updateState { copy(overview = overview) }
                // This week's rides changed: their zone time too.
                val zones = loadZones()
                updateState { copy(zonesS = zones.toImmutableList()) }
            }
        }
        viewModelScope.launch {
            observeSettings().collect { settings -> updateState { copy(unitSystem = settings.unitSystem) } }
        }
    }

    override fun onEvent(event: Unit) = Unit
}
