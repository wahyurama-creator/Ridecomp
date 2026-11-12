package com.ridecomp.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ridecomp.core.usecase.ObserveOnboardingDoneUseCase
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** Where the app starts: null while loading, then onboarding (first launch) or home. */
class AppViewModel(observeOnboardingDone: ObserveOnboardingDoneUseCase) : ViewModel() {
    val isOnboardingDone: StateFlow<Boolean?> = observeOnboardingDone()
        .map<Boolean, Boolean?> { it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
