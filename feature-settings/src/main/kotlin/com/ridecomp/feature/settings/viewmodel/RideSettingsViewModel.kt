package com.ridecomp.feature.settings.viewmodel

import androidx.lifecycle.viewModelScope
import com.ridecomp.core.model.RideSettings
import com.ridecomp.core.model.ScreenMode
import com.ridecomp.core.model.UnitSystem
import com.ridecomp.core.usecase.ObserveRideSettingsUseCase
import com.ridecomp.core.usecase.SaveRideSettingsUseCase
import com.ridecomp.ui.udf.NoEffect
import com.ridecomp.ui.udf.UdfViewModel
import kotlinx.coroutines.launch

/** Every change is saved immediately; the screen always shows the stored value. */
sealed interface RideSettingsUiEvent {
    data class AutoPauseEnabled(val isEnabled: Boolean) : RideSettingsUiEvent

    data class AutoPauseDelay(val seconds: Int) : RideSettingsUiEvent

    data class AutoLap(val meters: Double?) : RideSettingsUiEvent

    data class DrinkInterval(val minutes: Int?) : RideSettingsUiEvent

    data class EatInterval(val minutes: Int?) : RideSettingsUiEvent

    data class Screen(val mode: ScreenMode) : RideSettingsUiEvent

    data class BatterySaver(val isEnabled: Boolean) : RideSettingsUiEvent

    data class VolumeButtons(val isEnabled: Boolean) : RideSettingsUiEvent

    data class Units(val system: UnitSystem) : RideSettingsUiEvent
}

class RideSettingsViewModel(
    private val observeSettings: ObserveRideSettingsUseCase,
    private val saveSettings: SaveRideSettingsUseCase,
) : UdfViewModel<RideSettings, RideSettingsUiEvent, NoEffect>(RideSettings()) {
    init {
        viewModelScope.launch { observeSettings().collect { stored -> updateState { stored } } }
    }

    override fun onEvent(event: RideSettingsUiEvent) {
        val updated = when (event) {
            is RideSettingsUiEvent.AutoPauseEnabled -> currentState.copy(isAutoPauseEnabled = event.isEnabled)
            is RideSettingsUiEvent.AutoPauseDelay -> currentState.copy(autoPauseDelayS = event.seconds)
            is RideSettingsUiEvent.AutoLap -> currentState.copy(autoLapM = event.meters)
            is RideSettingsUiEvent.DrinkInterval -> currentState.copy(drinkIntervalMin = event.minutes)
            is RideSettingsUiEvent.EatInterval -> currentState.copy(eatIntervalMin = event.minutes)
            is RideSettingsUiEvent.Screen -> currentState.copy(screenMode = event.mode)
            is RideSettingsUiEvent.BatterySaver -> currentState.copy(isBatterySaverEnabled = event.isEnabled)
            is RideSettingsUiEvent.VolumeButtons -> currentState.copy(isVolumeButtonsEnabled = event.isEnabled)
            is RideSettingsUiEvent.Units -> currentState.copy(unitSystem = event.system)
        }
        updateState { updated }
        viewModelScope.launch { saveSettings(updated) }
    }
}
