package com.ridecomp.platform.service

import com.ridecomp.core.config.Units
import com.ridecomp.core.engine.recording.RecordingSession
import com.ridecomp.core.engine.safety.IncidentManager
import com.ridecomp.core.engine.sensors.SensorConnector
import com.ridecomp.core.engine.sensors.SensorRepository
import com.ridecomp.core.engine.sensors.TrainerControl
import com.ridecomp.core.repository.ActivityRepository
import com.ridecomp.core.repository.ProfileRepository
import com.ridecomp.core.repository.RouteRepository
import com.ridecomp.core.repository.SettingsRepository
import com.ridecomp.core.source.AccelerometerSource
import com.ridecomp.core.source.BatteryLevelSource
import com.ridecomp.core.source.Clock
import com.ridecomp.core.source.DispatcherProvider
import com.ridecomp.core.source.LocationSource
import com.ridecomp.core.source.Logger
import com.ridecomp.core.source.PressureSource
import com.ridecomp.core.usecase.ExportFitUseCase
import com.ridecomp.core.usecase.GetLiveSegmentsUseCase
import com.ridecomp.core.usecase.GetRideProfileUseCase
import com.ridecomp.core.usecase.GetRouteGuidanceUseCase
import com.ridecomp.core.usecase.GetSelectedBikeUseCase
import com.ridecomp.core.usecase.RideSessionUseCase
import com.ridecomp.platform.device.AlertPlayer

/**
 * What [RecordingService] needs. Android creates the service, so it cannot use constructor injection;
 * the Application (composition root in :app) provides these, keeping :platform free of the DI framework
 * (decision D-20).
 */
interface RecordingDependencies {
    val activityRepository: ActivityRepository
    val settingsRepository: SettingsRepository
    val profileRepository: ProfileRepository
    val locationSource: LocationSource
    val pressureSource: PressureSource
    val batteryLevelSource: BatteryLevelSource
    val clock: Clock
    val dispatchers: DispatcherProvider
    val logger: Logger
    val stateHolder: RideStateHolder
    val alertPlayer: AlertPlayer
    val sensorRepository: SensorRepository
    val sensorConnector: SensorConnector

    /** Writes the temporary emergency FIT at ≤ 5 % battery (12-FEATURE-SPECS §3). */
    val exportFit: ExportFitUseCase

    /** Activity profile of a ride (default when the id is unknown or null). */
    val getRideProfile: GetRideProfileUseCase

    /** Bike picked before the ride: its weight and wheel size (D-89). */
    val getSelectedBike: GetSelectedBikeUseCase

    /** Today's planned session the ride is measured against (T7.4b). */
    val rideSession: RideSessionUseCase

    /** Smart trainer ERG target (T9.1). */
    val trainerControl: TrainerControl

    /** Refreshes the home screen widget (T9.2); a no-op without widgets. */
    val rideWidget: RideWidget

    /** Incident countdown and emergency SMS (single instance, T2.16). */
    val incidentManager: IncidentManager
    val accelerometerSource: AccelerometerSource

    /** Points of the followed route (T3.6). */
    val routeRepository: RouteRepository

    /** Climbs and turns of the followed route (T3.11, T3.12). */
    val getRouteGuidance: GetRouteGuidanceUseCase

    /** Personal segments with their records, for live segments (T4.3). */
    val getLiveSegments: GetLiveSegmentsUseCase

    /** A new session per service instance. */
    fun newSession(): RecordingSession
}

/** Implemented by the Application class. */
interface RecordingDependenciesProvider {
    val recordingDependencies: RecordingDependencies
}

/** The home screen widget, implemented in :app (T9.2). */
fun interface RideWidget {
    fun refresh()
}

/** Widget updates while riding at most once a minute (T9.2, BAT-03); [now] when the ride ends. */
class ThrottledRideWidget(private val widget: RideWidget, private val clock: Clock) {
    private var lastRefreshMs: Long? = null

    fun onRideUpdate() {
        val now = clock.nowMs()
        val last = lastRefreshMs
        if (last != null && now - last < REFRESH_MS) return
        lastRefreshMs = now
        widget.refresh()
    }

    fun now() {
        lastRefreshMs = null
        widget.refresh()
    }

    private companion object {
        const val REFRESH_MS = Units.MS_PER_MINUTE
    }
}
