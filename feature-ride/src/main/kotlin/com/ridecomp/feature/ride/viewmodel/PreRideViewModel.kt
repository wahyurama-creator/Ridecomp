package com.ridecomp.feature.ride.viewmodel

import androidx.lifecycle.viewModelScope
import com.ridecomp.core.engine.recording.BatteryStatus
import com.ridecomp.core.usecase.GpsStatus
import com.ridecomp.core.usecase.ObserveGpsStatusUseCase
import com.ridecomp.core.usecase.ReadBatteryUseCase
import com.ridecomp.core.usecase.StartRideUseCase
import com.ridecomp.ui.udf.UdfViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/** Battery shown in the pre-ride check; GPS status is a separate lifecycle-bound flow. */
data class PreRideUiState(val battery: BatteryStatus?)

sealed interface PreRideUiEvent {
    data object Start : PreRideUiEvent
}

sealed interface PreRideEffect {
    data object OpenRide : PreRideEffect
}

/**
 * Pre-ride check (02-DESIGN §3.3). GPS warms up only while the screen is visible: [gpsStatus] is shared
 * with `WhileSubscribed`, so leaving the screen stops location updates (BAT-01, CO-07).
 */
class PreRideViewModel(
    observeGps: ObserveGpsStatusUseCase,
    readBattery: ReadBatteryUseCase,
    private val startRide: StartRideUseCase,
) : UdfViewModel<PreRideUiState, PreRideUiEvent, PreRideEffect>(PreRideUiState(readBattery())) {
    val gpsStatus: StateFlow<GpsStatus> = observeGps()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), GpsStatus.Searching)

    override fun onEvent(event: PreRideUiEvent) {
        when (event) {
            PreRideUiEvent.Start -> {
                startRide()
                sendEffect(PreRideEffect.OpenRide)
            }
        }
    }

    private companion object {
        /** Keeps GPS across quick configuration changes only (CO-07). */
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
