package com.ridecomp.platform.service

import com.ridecomp.core.engine.recording.RecordingSession
import com.ridecomp.core.engine.sensors.SensorConnector
import com.ridecomp.core.engine.sensors.SensorRepository
import com.ridecomp.core.repository.ActivityRepository
import com.ridecomp.core.repository.ProfileRepository
import com.ridecomp.core.repository.SettingsRepository
import com.ridecomp.core.source.BatteryLevelSource
import com.ridecomp.core.source.Clock
import com.ridecomp.core.source.DispatcherProvider
import com.ridecomp.core.source.LocationSource
import com.ridecomp.core.source.Logger
import com.ridecomp.core.source.PressureSource
import com.ridecomp.core.usecase.ExportFitUseCase
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

    /** A new session per service instance. */
    fun newSession(): RecordingSession
}

/** Implemented by the Application class. */
interface RecordingDependenciesProvider {
    val recordingDependencies: RecordingDependencies
}
