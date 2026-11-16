package com.ridecomp.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ridecomp.core.usecase.AnalysisRequests
import com.ridecomp.core.usecase.AnalyzeActivitiesUseCase
import com.ridecomp.core.usecase.ClearShareCardsUseCase
import com.ridecomp.core.usecase.ObserveHistoryCountUseCase
import com.ridecomp.core.usecase.ObserveOnboardingDoneUseCase
import com.ridecomp.core.usecase.ProcessStravaUploadsUseCase
import com.ridecomp.core.usecase.QueueNewRidesForStravaUseCase
import com.ridecomp.core.usecase.StravaUploadRequests
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The Strava upload queue as the app runs it (T5.3), grouped to keep the constructor short (KT-05). */
data class StravaQueue(
    val requests: StravaUploadRequests,
    val queueNewRides: QueueNewRidesForStravaUseCase,
    val process: ProcessStravaUploadsUseCase,
)

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
    clearShareCards: ClearShareCardsUseCase,
    strava: StravaQueue,
) : ViewModel() {
    val isOnboardingDone: StateFlow<Boolean?> = observeOnboardingDone()
        .map<Boolean, Boolean?> { it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    init {
        // Share card images of earlier sessions (12-FEATURE-SPECS §11).
        viewModelScope.launch { clearShareCards() }
        viewModelScope.launch {
            merge(observeRideCount().distinctUntilChanged(), analysisRequests.flow).collectLatest { analyze() }
        }
        // Strava uploads (T5.3): at start, after each saved ride (auto-upload) and when a ride is queued.
        // One run at a time, never cancelled mid-upload.
        viewModelScope.launch {
            merge(observeRideCount().distinctUntilChanged().map { }, strava.requests.flow).collect {
                strava.queueNewRides()
                strava.process()
            }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
