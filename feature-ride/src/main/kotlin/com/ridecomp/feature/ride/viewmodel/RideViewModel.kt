package com.ridecomp.feature.ride.viewmodel

import androidx.lifecycle.viewModelScope
import com.ridecomp.core.engine.recording.LiveRide
import com.ridecomp.core.engine.recording.RideAction
import com.ridecomp.core.model.RideSettings
import com.ridecomp.core.model.RideStatus
import com.ridecomp.core.usecase.ObserveDataPagesUseCase
import com.ridecomp.core.usecase.ObserveLiveRideUseCase
import com.ridecomp.core.usecase.ObserveRideSettingsUseCase
import com.ridecomp.core.usecase.PerformRideActionUseCase
import com.ridecomp.feature.ride.model.DataPage
import com.ridecomp.feature.ride.model.DefaultDataPages
import com.ridecomp.feature.ride.model.RideMetricKeys
import com.ridecomp.feature.ride.model.toDataPages
import com.ridecomp.ui.udf.UdfViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface RideEffect {
    /** Ride finished: show the summary (02-DESIGN §3.6). */
    data object OpenSummary : RideEffect

    /** No ride any more (saved/discarded elsewhere). */
    data object Close : RideEffect
}

/**
 * Ride screen (T1.8). Live data comes straight from the service's StateFlow; each field derives its own
 * text so only changed fields recompose (UI-04). User input only goes out as [RideAction]s (ARCH-06).
 */
class RideViewModel(
    observeLive: ObserveLiveRideUseCase,
    observeSettings: ObserveRideSettingsUseCase,
    observeDataPages: ObserveDataPagesUseCase,
    private val performAction: PerformRideActionUseCase,
) : UdfViewModel<Unit, RideAction, RideEffect>(Unit) {
    val liveRide: StateFlow<LiveRide> = observeLive()
    val settings: StateFlow<RideSettings> = observeSettings()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), RideSettings())

    /** The rider's pages (T2.8), or the defaults. */
    val pages: StateFlow<List<DataPage>> = observeDataPages(RideMetricKeys.ALL)
        .map { layout -> layout?.toDataPages() ?: DefaultDataPages.ALL }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), DefaultDataPages.ALL)

    init {
        viewModelScope.launch {
            var hasSeenRide = false
            liveRide.collect { live ->
                when (live.status) {
                    RideStatus.FINISHED -> sendEffect(RideEffect.OpenSummary)
                    RideStatus.IDLE -> if (hasSeenRide) sendEffect(RideEffect.Close)
                    RideStatus.RECORDING, RideStatus.PAUSED, RideStatus.AUTO_PAUSED -> hasSeenRide = true
                }
            }
        }
    }

    override fun onEvent(event: RideAction) {
        performAction(event)
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
