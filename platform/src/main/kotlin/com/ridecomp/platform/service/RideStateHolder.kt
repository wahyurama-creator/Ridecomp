package com.ridecomp.platform.service

import com.ridecomp.core.engine.recording.LiveRide
import com.ridecomp.core.engine.safety.IncidentState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Process-wide live ride state, written only by [RecordingService] and read by the UI through
 * [com.ridecomp.core.source.RideController]. Closing the UI never affects the recording
 * (03-ARCHITECTURE §4.1). Single instance from the DI graph (ARCH-09).
 */
class RideStateHolder {
    private val _liveRide = MutableStateFlow(LiveRide())
    val liveRide: StateFlow<LiveRide> = _liveRide.asStateFlow()

    private val _activeActivityId = MutableStateFlow<Long?>(null)
    val activeActivityId: StateFlow<Long?> = _activeActivityId.asStateFlow()

    internal fun publish(live: LiveRide, activityId: Long?) {
        _liveRide.value = live
        _activeActivityId.value = activityId
    }

    /** Incident state changes between ticks (countdown start, sent) are shown at once. */
    internal fun updateIncident(state: IncidentState) {
        _liveRide.update { it.copy(incident = state) }
    }

    internal fun reset() {
        publish(LiveRide(), null)
    }
}
