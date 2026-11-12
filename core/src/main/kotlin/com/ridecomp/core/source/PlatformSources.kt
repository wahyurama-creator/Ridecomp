package com.ridecomp.core.source

import com.ridecomp.core.engine.recording.BatteryStatus
import com.ridecomp.core.engine.recording.LiveRide
import com.ridecomp.core.engine.recording.RideAction
import com.ridecomp.core.model.LocationFix
import com.ridecomp.core.model.PressureReading
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/** GPS fixes; collecting starts updates, cancelling stops them (CO-04, BAT-01). */
public interface LocationSource {
    /** Fixes at [intervalMs]; the flow ends updates in `awaitClose`. */
    public fun fixes(intervalMs: Long): Flow<LocationFix>

    public fun isPermissionGranted(): Boolean

    public fun isGpsEnabled(): Boolean
}

/** Barometer; [readings] is empty when the phone has none. */
public interface PressureSource {
    public val hasBarometer: Boolean

    public fun readings(): Flow<PressureReading>
}

/** Phone battery, read from the sticky broadcast on demand (no receiver kept registered). */
public interface BatteryLevelSource {
    public fun read(): BatteryStatus?
}

/** Unique ids (activity uuid / FIT external id). */
public interface IdGenerator {
    public fun newId(): String
}

/** Localized default ride name ("Morning ride"…), see [com.ridecomp.core.usecase.TimeOfDay]. */
public interface RideNameProvider {
    public fun defaultName(startedAtMs: Long): String
}

/**
 * Commands to the recording service and its live state. Implemented in :platform; the UI only uses
 * this interface (ARCH-02).
 */
public interface RideController {
    /** Live ride state (IDLE when no ride runs). */
    public val liveRide: StateFlow<LiveRide>

    /** Id of the ride being recorded, null when none. */
    public val activeActivityId: StateFlow<Long?>

    /** Starts the foreground service and a new ride. */
    public fun start()

    /** Continues a recovered ride (03-ARCHITECTURE §4.5). */
    public fun resumeRecovered(activityId: Long)

    /** Any in-ride action (pause, lap, finish, save, discard, reminder buttons…). */
    public fun perform(action: RideAction)
}
