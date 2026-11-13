package com.ridecomp.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ridecomp.core.usecase.AnalysisRequests
import com.ridecomp.core.usecase.AnalyzeActivitiesUseCase
import com.ridecomp.core.usecase.ObserveHistoryCountUseCase
import com.ridecomp.core.usecase.ObserveOnboardingDoneUseCase
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Where the app starts: null while loading, then onboarding (first launch) or home. While the app is open it
 * also analyzes saved rides (records and segments, D-67): at start for rides not analyzed yet, then whenever a
 * ride is saved or a new segment asks for old rides to be matched. Nothing runs once the app is closed
 * (BAT-05); an interrupted analysis resumes at the next start.
 */
class AppViewModel(
    observeOnboardingDone: ObserveOnboardingDoneUseCase,
    observeRideCount: ObserveHistoryCountUseCase,
    analysisRequests: AnalysisRequests,
    analyze: AnalyzeActivitiesUseCase,
) : ViewModel() {
    val isOnboardingDone: StateFlow<Boolean?> = observeOnboardingDone()
        .map<Boolean, Boolean?> { it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    init {
        viewModelScope.launch {
            merge(observeRideCount().distinctUntilChanged(), analysisRequests.flow).collectLatest { analyze() }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
