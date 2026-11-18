package com.ridecomp.feature.settings.viewmodel

import androidx.lifecycle.viewModelScope
import com.ridecomp.core.model.RiderLevel
import com.ridecomp.core.usecase.CompleteOnboardingUseCase
import com.ridecomp.core.usecase.GpsStatus
import com.ridecomp.core.usecase.ObserveGpsStatusUseCase
import com.ridecomp.core.usecase.SetRiderLevelUseCase
import com.ridecomp.ui.udf.UdfViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Onboarding steps in order (02-DESIGN §3.1, 13-UI-SCREENS #1, #2, #22): WELCOME introduces the app
 * (T10.12); LANGUAGE first of the setup so every later text is in the rider's language (D-88); LEVEL
 * (Phase 4G) asks how experienced the rider is; BIKE saves the first bike, picked later before each ride
 * (D-89). [isSkippable] steps can be left for later (D-105): defaults are used until the rider fills them.
 */
enum class OnboardingStep(val isSkippable: Boolean = false) {
    WELCOME,
    LANGUAGE,
    PERMISSIONS,
    LEVEL(isSkippable = true),
    PROFILE(isSkippable = true),
    BIKE(isSkippable = true),
    READY,
    ;

    /** Setup steps after the welcome, numbered "Langkah n dari 6". */
    companion object {
        val SETUP: List<OnboardingStep> = entries - WELCOME
    }
}

sealed interface OnboardingUiEvent {
    data object Next : OnboardingUiEvent

    data object Back : OnboardingUiEvent

    /** The rider's experience (Phase 4G): stored, then the next step. */
    data class ChooseLevel(val level: RiderLevel) : OnboardingUiEvent

    /** "Lewati" on a skippable step: nothing is saved, defaults stay (D-105). */
    data object Skip : OnboardingUiEvent

    /** "Mulai pakai RideComp" or "Lewati" on the last step. */
    data object Finished : OnboardingUiEvent
}

sealed interface OnboardingEffect {
    data object Done : OnboardingEffect
}

/**
 * Onboarding. The GPS test of the last step runs only while that step is shown and the screen observes
 * [gpsStatus] (`WhileSubscribed`), like the pre-ride check (BAT-01, CO-07).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModel(
    private val completeOnboarding: CompleteOnboardingUseCase,
    observeGps: ObserveGpsStatusUseCase,
    private val setRiderLevel: SetRiderLevelUseCase,
) : UdfViewModel<OnboardingStep, OnboardingUiEvent, OnboardingEffect>(OnboardingStep.WELCOME) {
    val gpsStatus: StateFlow<GpsStatus?> = uiState
        .map { it == OnboardingStep.READY }
        .distinctUntilChanged()
        .flatMapLatest { isTesting -> if (isTesting) observeGps() else flowOf(null) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    override fun onEvent(event: OnboardingUiEvent) {
        when (event) {
            OnboardingUiEvent.Next -> updateState { OnboardingStep.entries.getOrElse(ordinal + 1) { this } }
            OnboardingUiEvent.Back -> updateState { OnboardingStep.entries.getOrElse(ordinal - 1) { this } }
            OnboardingUiEvent.Skip -> updateState {
                if (isSkippable) OnboardingStep.entries.getOrElse(ordinal + 1) { this } else this
            }
            is OnboardingUiEvent.ChooseLevel -> viewModelScope.launch {
                setRiderLevel(event.level)
                // A double tap must not skip the profile step.
                updateState { if (this == OnboardingStep.LEVEL) OnboardingStep.PROFILE else this }
            }
            OnboardingUiEvent.Finished -> viewModelScope.launch {
                completeOnboarding()
                sendEffect(OnboardingEffect.Done)
            }
        }
    }

    private companion object {
        /** Keeps GPS across quick configuration changes only (CO-07). */
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
