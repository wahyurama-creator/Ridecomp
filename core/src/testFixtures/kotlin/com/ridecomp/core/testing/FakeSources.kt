package com.ridecomp.core.testing

import com.ridecomp.core.engine.recording.BatteryStatus
import com.ridecomp.core.engine.recording.LiveRide
import com.ridecomp.core.engine.recording.RideAction
import com.ridecomp.core.model.LocationFix
import com.ridecomp.core.source.BatteryLevelSource
import com.ridecomp.core.source.LocationSource
import com.ridecomp.core.source.RideController
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart

/** Records commands and lets tests drive the live state. */
public class FakeRideController : RideController {
    public val live: MutableStateFlow<LiveRide> = MutableStateFlow(LiveRide())
    public val activityId: MutableStateFlow<Long?> = MutableStateFlow(null)
    public val commands: MutableList<String> = mutableListOf()

    override val liveRide: StateFlow<LiveRide> get() = live
    override val activeActivityId: StateFlow<Long?> get() = activityId

    override fun start() {
        commands += START
    }

    override fun resumeRecovered(activityId: Long) {
        commands += "$RESUME_PREFIX$activityId"
    }

    override fun perform(action: RideAction) {
        commands += action.name
    }

    public companion object {
        public const val START: String = "START"
        public const val RESUME_PREFIX: String = "RESUME:"
    }
}

/** GPS fake: tests emit fixes; [activeCollectors] proves updates stop when nobody collects (BAT-01). */
public class FakeLocationSource(
    public var isPermissionGrantedValue: Boolean = true,
    public var isGpsEnabledValue: Boolean = true,
) : LocationSource {
    public val fixesFlow: MutableSharedFlow<LocationFix> = MutableSharedFlow(extraBufferCapacity = 64)
    public var activeCollectors: Int = 0
        private set

    override fun fixes(intervalMs: Long): Flow<LocationFix> = fixesFlow
        .onStart { activeCollectors++ }
        .onCompletion { activeCollectors-- }

    override fun isPermissionGranted(): Boolean = isPermissionGrantedValue

    override fun isGpsEnabled(): Boolean = isGpsEnabledValue
}

public class FakeBatteryLevelSource(public var status: BatteryStatus? = BatteryStatus(80, isCharging = false)) :
    BatteryLevelSource {
    override fun read(): BatteryStatus? = status
}
