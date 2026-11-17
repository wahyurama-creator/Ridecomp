package com.ridecomp.core.usecase

import com.ridecomp.core.config.GpsConfig
import com.ridecomp.core.engine.recording.BatteryStatus
import com.ridecomp.core.engine.recording.LiveRide
import com.ridecomp.core.engine.recording.RideAction
import com.ridecomp.core.engine.safety.IncidentAction
import com.ridecomp.core.model.RideStatus
import com.ridecomp.core.repository.ActivityRepository
import com.ridecomp.core.source.BatteryLevelSource
import com.ridecomp.core.source.LocationSource
import com.ridecomp.core.source.RideController
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

/** Starts a new ride (foreground service). */
public class StartRideUseCase(private val controller: RideController) {
    /** Starts a ride with the chosen activity profile (12-FEATURE-SPECS §4). */
    public operator fun invoke(profileId: Long?, routeId: Long? = null, withSession: Boolean = false) {
        controller.start(profileId, routeId, withSession)
    }
}

/** Any in-ride action: pause, resume, lap, finish, save, discard, reminder buttons. */
public class PerformRideActionUseCase(private val controller: RideController) {
    public operator fun invoke(action: RideAction) {
        controller.perform(action)
    }
}

/** Live ride state for the ride screen; IDLE when no ride runs. */
public class ObserveLiveRideUseCase(private val controller: RideController) {
    public operator fun invoke(): StateFlow<LiveRide> = controller.liveRide

    public fun activeActivityId(): StateFlow<Long?> = controller.activeActivityId
}

/** What the user chose for a ride interrupted by a crash/kill (02-DESIGN §3.2). */
public enum class RecoveryChoice { RESUME, SAVE, DISCARD }

/** Resumes, saves or discards a recovered ride (03-ARCHITECTURE §4.5). */
public class HandleRecoveredRideUseCase(
    private val controller: RideController,
    private val repository: ActivityRepository,
) {
    public suspend operator fun invoke(activityId: Long, choice: RecoveryChoice) {
        when (choice) {
            RecoveryChoice.RESUME -> controller.resumeRecovered(activityId)
            RecoveryChoice.SAVE -> {
                controller.resumeRecovered(activityId)
                controller.perform(RideAction.FINISH)
                controller.perform(RideAction.SAVE)
            }
            RecoveryChoice.DISCARD -> repository.delete(activityId)
        }
    }
}

/** GPS readiness shown in the pre-ride check (02-DESIGN §3.3). */
public sealed interface GpsStatus {
    public data object NoPermission : GpsStatus

    public data object Disabled : GpsStatus

    /** No fix good enough yet; [accuracyM] is the latest fix's accuracy, if any (13-UI-SCREENS #29). */
    public data class Searching(val accuracyM: Double? = null) : GpsStatus

    /** The selected profile records without GPS (Indoor). */
    public data object NotUsed : GpsStatus

    public data class Ready(val accuracyM: Double?) : GpsStatus
}

/**
 * Pre-ride GPS warm-up: GPS runs only while this flow is collected (resource table 09 §1), so collect it
 * lifecycle-aware and stop when leaving the screen.
 */
public class ObserveGpsStatusUseCase(private val locationSource: LocationSource) {
    public operator fun invoke(): Flow<GpsStatus> = when {
        !locationSource.isPermissionGranted() -> flowOf(GpsStatus.NoPermission)
        !locationSource.isGpsEnabled() -> flowOf(GpsStatus.Disabled)
        else -> locationSource.fixes(GpsConfig.INTERVAL_MS)
            .map<_, GpsStatus> { fix ->
                val accuracy = fix.accuracyM
                if (accuracy == null ||
                    accuracy <= GpsConfig.MAX_ACCURACY_M
                ) {
                    GpsStatus.Ready(accuracy)
                } else {
                    GpsStatus.Searching(accuracy)
                }
            }
            .onStart { emit(GpsStatus.Searching()) }
            .catch { emit(GpsStatus.NoPermission) }
    }
}

/** Whether recording could use GPS, for the home tiles: permission and switch only, GPS stays off (BAT-01). */
public enum class GpsAvailability { READY, NO_PERMISSION, DISABLED }

public class CheckGpsAvailabilityUseCase(private val locationSource: LocationSource) {
    public operator fun invoke(): GpsAvailability = when {
        !locationSource.isPermissionGranted() -> GpsAvailability.NO_PERMISSION
        !locationSource.isGpsEnabled() -> GpsAvailability.DISABLED
        else -> GpsAvailability.READY
    }
}

/** Phone battery for the pre-ride check. */
public class ReadBatteryUseCase(private val source: BatteryLevelSource) {
    public operator fun invoke(): BatteryStatus? = source.read()
}

/** True while a ride exists in the service (recording, paused or waiting for save/discard). */
public fun LiveRide.isRideInProgress(): Boolean = status != RideStatus.IDLE

/** "I am OK", "Send now", "I am safe" and manual SOS (12-FEATURE-SPECS §5.3). */
public class PerformIncidentActionUseCase(private val controller: RideController) {
    public operator fun invoke(action: IncidentAction) {
        controller.performIncident(action)
    }
}
