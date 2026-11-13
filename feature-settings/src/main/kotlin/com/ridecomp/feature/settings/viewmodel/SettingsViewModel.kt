package com.ridecomp.feature.settings.viewmodel

import androidx.compose.runtime.Immutable
import androidx.lifecycle.viewModelScope
import com.ridecomp.core.model.RideSettings
import com.ridecomp.core.model.RiderProfile
import com.ridecomp.core.usecase.ObserveActivityProfilesUseCase
import com.ridecomp.core.usecase.ObserveProfileUseCase
import com.ridecomp.core.usecase.ObserveRideSettingsUseCase
import com.ridecomp.core.usecase.ObserveSafetyUseCase
import com.ridecomp.core.usecase.SafetyState
import com.ridecomp.ui.udf.NoEffect
import com.ridecomp.ui.udf.UdfViewModel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/** Values shown on the right of each settings row (13-UI-SCREENS #13). */
@Immutable
data class SettingsUiState(
    val profile: RiderProfile? = null,
    val activityProfileCount: Int = 0,
    val rideSettings: RideSettings = RideSettings(),
    val safety: SafetyState? = null,
)

/** Settings overview: read-only summaries; every change happens on the section's own screen. */
class SettingsViewModel(
    observeProfile: ObserveProfileUseCase,
    observeActivityProfiles: ObserveActivityProfilesUseCase,
    observeRideSettings: ObserveRideSettingsUseCase,
    observeSafety: ObserveSafetyUseCase,
) : UdfViewModel<SettingsUiState, Unit, NoEffect>(SettingsUiState()) {
    init {
        viewModelScope.launch {
            combine(observeProfile(), observeActivityProfiles(), observeRideSettings(), observeSafety()) {
                    profile,
                    profiles,
                    settings,
                    safety,
                ->
                SettingsUiState(profile, profiles.size, settings, safety)
            }.collect { state -> updateState { state } }
        }
    }

    override fun onEvent(event: Unit) = Unit
}
