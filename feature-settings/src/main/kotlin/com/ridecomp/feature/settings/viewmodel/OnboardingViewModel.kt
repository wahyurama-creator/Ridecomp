package com.ridecomp.feature.settings.viewmodel

import androidx.lifecycle.viewModelScope
import com.ridecomp.core.usecase.CompleteOnboardingUseCase
import com.ridecomp.ui.udf.UdfViewModel
import kotlinx.coroutines.launch

/** Onboarding steps in order (02-DESIGN §3.1). Bluetooth permission comes with sensors in Phase 2. */
enum class OnboardingStep { LOCATION, NOTIFICATIONS, BATTERY, PROFILE }

sealed interface OnboardingUiEvent {
    data object Next : OnboardingUiEvent

    data object Back : OnboardingUiEvent

    /** Profile saved on the last step. */
    data object Finished : OnboardingUiEvent
}

sealed interface OnboardingEffect {
    data object Done : OnboardingEffect
}

class OnboardingViewModel(private val completeOnboarding: CompleteOnboardingUseCase) :
    UdfViewModel<OnboardingStep, OnboardingUiEvent, OnboardingEffect>(OnboardingStep.LOCATION) {
    override fun onEvent(event: OnboardingUiEvent) {
        when (event) {
            OnboardingUiEvent.Next -> updateState { OnboardingStep.entries.getOrElse(ordinal + 1) { this } }
            OnboardingUiEvent.Back -> updateState { OnboardingStep.entries.getOrElse(ordinal - 1) { this } }
            OnboardingUiEvent.Finished -> viewModelScope.launch {
                completeOnboarding()
                sendEffect(OnboardingEffect.Done)
            }
        }
    }
}
