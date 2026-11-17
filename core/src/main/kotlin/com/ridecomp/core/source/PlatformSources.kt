package com.ridecomp.core.source

import com.ridecomp.core.engine.map.NetworkStatus
import com.ridecomp.core.engine.recording.BatteryStatus
import com.ridecomp.core.engine.recording.LiveRide
import com.ridecomp.core.engine.recording.RideAction
import com.ridecomp.core.engine.safety.AccelSample
import com.ridecomp.core.engine.safety.IncidentAction
import com.ridecomp.core.model.LocationFix
import com.ridecomp.core.model.PressureReading
import java.io.InputStream
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
/** The default network (D-90): listening only while collected (callback registered/unregistered, 09 §1). */
public interface NetworkStatusSource {
    public fun observe(): Flow<NetworkStatus>
}

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

    /** Starts the foreground service and a new ride; [withSession] measures it against today's plan (T7.4b). */
    public fun start(profileId: Long?, routeId: Long? = null, withSession: Boolean = false)

    /** Continues a recovered ride (03-ARCHITECTURE §4.5). */
    public fun resumeRecovered(activityId: Long)

    /** Any in-ride action (pause, lap, finish, save, discard, reminder buttons…). */
    public fun perform(action: RideAction)

    /** Incident countdown buttons and manual SOS (12-FEATURE-SPECS §5.3). */
    public fun performIncident(action: IncidentAction)
}

/**
 * The only way RideComp sends SMS (SEC-SMS-02). Called only by the incident flow and the test SMS.
 * Returns true when the network accepted the message.
 */
public interface SmsGateway {
    public fun hasPermission(): Boolean

    public suspend fun send(phone: String, text: String): Boolean
}

/** Localized SMS texts (12-FEATURE-SPECS §5.3, minimal content SEC-SMS-04). */
public interface SafetyMessages {
    public fun test(riderName: String): String

    public fun incident(riderName: String, atMs: Long, latDeg: Double?, lonDeg: Double?, accuracyM: Double?): String

    public fun safe(riderName: String): String
}

/** Contact chosen with the system picker (`ACTION_PICK`, no READ_CONTACTS, SEC-SMS-05). */
public data class PickedContact(val displayName: String, val phone: String) {
    override fun toString(): String = "PickedContact"
}

public interface PickedContactReader {
    /** Reads the contact the picker returned as [uri]; null when it has no phone number. */
    public suspend fun read(uri: String): PickedContact?
}

/** Accelerometer magnitude for incident detection (12-FEATURE-SPECS §5.2). Collected only while recording. */
public interface AccelerometerSource {
    public val hasAccelerometer: Boolean

    /** About 50 Hz while collected; the sensor listener is removed when collection stops (09 §1). */
    public fun samples(): Flow<AccelSample>
}

/** Restarts the app process (used once after a restore, decision D-39). */
public interface AppRestarter {
    public fun restart()
}

/** A GPX document chosen by the rider (SAF picker or "Open with"). */
public interface GpxDocumentSource {
    /** Opens the document; the caller closes the stream. */
    public fun open(uri: String): InputStream

    /** Original file name, used only as a label (SEC-IN-04); null when unknown. */
    public fun displayName(uri: String): String?
}
