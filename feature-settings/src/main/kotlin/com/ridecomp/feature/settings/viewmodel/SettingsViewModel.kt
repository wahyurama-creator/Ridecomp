package com.ridecomp.feature.settings.viewmodel

import androidx.compose.runtime.Immutable
import androidx.lifecycle.viewModelScope
import com.ridecomp.core.model.AppLanguage
import com.ridecomp.core.model.AppTheme
import com.ridecomp.core.model.RideSettings
import com.ridecomp.core.model.RiderProfile
import com.ridecomp.core.model.StravaStatus
import com.ridecomp.core.usecase.GetAppLanguageUseCase
import com.ridecomp.core.usecase.ObserveActivityProfilesUseCase
import com.ridecomp.core.usecase.ObserveAppThemeUseCase
import com.ridecomp.core.usecase.ObserveBikeChoiceUseCase
import com.ridecomp.core.usecase.ObserveOfflineMapsUseCase
import com.ridecomp.core.usecase.ObserveProfileUseCase
import com.ridecomp.core.usecase.ObserveRideSettingsUseCase
import com.ridecomp.core.usecase.ObserveSafetyUseCase
import com.ridecomp.core.usecase.ObserveStravaStatusUseCase
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
    val bikeCount: Int = 0,
    val rideSettings: RideSettings = RideSettings(),
    val safety: SafetyState? = null,
    val offlineMapCount: Int = 0,
    val offlineMapBytes: Long = 0,
    val isStravaConnected: Boolean = false,
    val language: AppLanguage = AppLanguage.SYSTEM,
    val theme: AppTheme = AppTheme.DEFAULT,
)

/** Sources of the settings summaries, grouped to keep the constructor short (KT-05). */
data class SettingsUseCases(
    val observeProfile: ObserveProfileUseCase,
    val observeActivityProfiles: ObserveActivityProfilesUseCase,
    val observeRideSettings: ObserveRideSettingsUseCase,
    val observeSafety: ObserveSafetyUseCase,
    val observeOfflineMaps: ObserveOfflineMapsUseCase,
    val observeStrava: ObserveStravaStatusUseCase,
    val getLanguage: GetAppLanguageUseCase,
    val observeBikes: ObserveBikeChoiceUseCase,
    val observeTheme: ObserveAppThemeUseCase,
)

/** Settings overview: read-only summaries; every change happens on the section's own screen. */
class SettingsViewModel(private val useCases: SettingsUseCases) :
    UdfViewModel<SettingsUiState, Unit, NoEffect>(SettingsUiState()) {
    init {
        viewModelScope.launch {
            combine(
                useCases.observeProfile(),
                useCases.observeActivityProfiles(),
                useCases.observeRideSettings(),
                useCases.observeSafety(),
            ) {
                    profile,
                    profiles,
                    settings,
                    safety,
                ->
                SettingsUiState(profile, profiles.size, rideSettings = settings, safety = safety)
            }.collect { state ->
                updateState {
                    state.copy(
                        offlineMapCount = offlineMapCount,
                        offlineMapBytes = offlineMapBytes,
                        isStravaConnected = isStravaConnected,
                        bikeCount = bikeCount,
                        theme = theme,
                        language = useCases.getLanguage(),
                    )
                }
            }
        }
        viewModelScope.launch {
            useCases.observeOfflineMaps().collect { maps ->
                updateState { copy(offlineMapCount = maps.size, offlineMapBytes = maps.sumOf { it.sizeBytes }) }
            }
        }
        viewModelScope.launch {
            useCases.observeTheme().collect { theme -> updateState { copy(theme = theme) } }
        }
        viewModelScope.launch {
            useCases.observeBikes().collect { choice -> updateState { copy(bikeCount = choice.bikes.size) } }
        }
        viewModelScope.launch {
            useCases.observeStrava().collect { status ->
                updateState { copy(isStravaConnected = status is StravaStatus.Connected) }
            }
        }
    }

    /** The screen is shown again (e.g. back from Language): the language is not a flow, read it again. */
    override fun onEvent(event: Unit) {
        updateState { copy(language = useCases.getLanguage()) }
    }
}
