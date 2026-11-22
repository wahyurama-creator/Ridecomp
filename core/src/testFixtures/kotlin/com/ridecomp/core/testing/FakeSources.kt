package com.ridecomp.core.testing

import com.ridecomp.core.engine.map.NetworkStatus
import com.ridecomp.core.engine.recording.BatteryStatus
import com.ridecomp.core.engine.recording.LiveRide
import com.ridecomp.core.engine.recording.RideAction
import com.ridecomp.core.engine.safety.IncidentAction
import com.ridecomp.core.model.LocationFix
import com.ridecomp.core.model.PressureReading
import com.ridecomp.core.source.BatteryLevelSource
import com.ridecomp.core.source.LocationSource
import com.ridecomp.core.source.MapCacheSource
import com.ridecomp.core.source.NetworkStatusSource
import com.ridecomp.core.source.PressureSource
import com.ridecomp.core.source.RideController
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart

/** Records commands and lets tests drive the live state. */
public class FakeRideController : RideController {
    public val live: MutableStateFlow<LiveRide> = MutableStateFlow(LiveRide())
    public val activityId: MutableStateFlow<Long?> = MutableStateFlow(null)
    public val commands: MutableList<String> = mutableListOf()

    override val liveRide: StateFlow<LiveRide> get() = live
    override val activeActivityId: StateFlow<Long?> get() = activityId

    public var startedProfileId: Long? = null
        private set

    public var startedRouteId: Long? = null
        private set

    public var startedWithSession: Boolean = false
        private set

    override fun start(profileId: Long?, routeId: Long?, withSession: Boolean) {
        startedProfileId = profileId
        startedRouteId = routeId
        startedWithSession = withSession
        commands += START
    }

    override fun resumeRecovered(activityId: Long) {
        commands += "$RESUME_PREFIX$activityId"
    }

    override fun perform(action: RideAction) {
        commands += action.name
    }

    override fun performIncident(action: IncidentAction) {
        commands += INCIDENT_PREFIX + action.name
    }

    public companion object {
        public const val START: String = "START"
        public const val RESUME_PREFIX: String = "RESUME:"
        public const val INCIDENT_PREFIX: String = "INCIDENT:"
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

/** Network for the online map; counts collectors to check the callback is released (09 §1). */
public class FakeNetworkStatusSource(initial: NetworkStatus = NetworkStatus(isConnected = true, isUnmetered = true)) :
    NetworkStatusSource {
    public val status: MutableStateFlow<NetworkStatus> = MutableStateFlow(initial)
    public var activeCollectors: Int = 0
        private set

    override fun observe(): Flow<NetworkStatus> = status
        .onStart { activeCollectors++ }
        .onCompletion { activeCollectors-- }
}

/** Map tile cache of [sizeBytes]; [clear] empties it (T11.11). */
public class FakeMapCacheSource(public var size: Long = 0L) : MapCacheSource {
    public var clears: Int = 0
        private set

    override suspend fun sizeBytes(): Long = size

    override suspend fun clear() {
        size = 0L
        clears++
    }
}

public class FakeBatteryLevelSource(public var status: BatteryStatus? = BatteryStatus(80, isCharging = false)) :
    BatteryLevelSource {
    override fun read(): BatteryStatus? = status
}

/** A barometer, or none ([hasBarometer] false, the default: most low-end phones, D-148). */
public class FakePressureSource(override val hasBarometer: Boolean = false) : PressureSource {
    override fun readings(): Flow<PressureReading> = emptyFlow()
}
