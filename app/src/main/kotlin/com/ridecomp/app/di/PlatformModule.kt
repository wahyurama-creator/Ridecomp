package com.ridecomp.app.di

import com.ridecomp.app.BuildConfig
import com.ridecomp.core.engine.backup.BackupManifestCodec
import com.ridecomp.core.engine.recording.RecordingSession
import com.ridecomp.core.engine.safety.IncidentManager
import com.ridecomp.core.engine.sensors.BleScanner
import com.ridecomp.core.engine.sensors.SensorConnector
import com.ridecomp.core.engine.sensors.SensorRepository
import com.ridecomp.core.model.StravaSetup
import com.ridecomp.core.repository.ActivityRepository
import com.ridecomp.core.repository.OfflineMapRepository
import com.ridecomp.core.repository.ProfileRepository
import com.ridecomp.core.repository.RouteRepository
import com.ridecomp.core.repository.SettingsRepository
import com.ridecomp.core.source.AccelerometerSource
import com.ridecomp.core.source.AppLanguageStore
import com.ridecomp.core.source.AppRestarter
import com.ridecomp.core.source.BatteryLevelSource
import com.ridecomp.core.source.Clock
import com.ridecomp.core.source.DispatcherProvider
import com.ridecomp.core.source.GpxDocumentSource
import com.ridecomp.core.source.IdGenerator
import com.ridecomp.core.source.LocationSource
import com.ridecomp.core.source.Logger
import com.ridecomp.core.source.PickedContactReader
import com.ridecomp.core.source.PressureSource
import com.ridecomp.core.source.RideController
import com.ridecomp.core.source.RideNameProvider
import com.ridecomp.core.source.SafetyMessages
import com.ridecomp.core.source.SmsGateway
import com.ridecomp.core.source.StravaAccountStore
import com.ridecomp.core.source.StravaApi
import com.ridecomp.core.usecase.BackupFiles
import com.ridecomp.core.usecase.ExportFitUseCase
import com.ridecomp.core.usecase.GetLiveSegmentsUseCase
import com.ridecomp.core.usecase.GetRideProfileUseCase
import com.ridecomp.core.usecase.GetRouteGuidanceUseCase
import com.ridecomp.platform.backup.AndroidAppRestarter
import com.ridecomp.platform.backup.AndroidBackupFiles
import com.ridecomp.platform.backup.JsonBackupManifestCodec
import com.ridecomp.platform.ble.AndroidBleScanner
import com.ridecomp.platform.ble.AndroidSensorConnector
import com.ridecomp.platform.crash.CrashLogConfig
import com.ridecomp.platform.crash.CrashLogStore
import com.ridecomp.platform.device.AlertPlayer
import com.ridecomp.platform.device.AndroidBatteryLevelSource
import com.ridecomp.platform.device.AndroidRideNameProvider
import com.ridecomp.platform.device.UuidIdGenerator
import com.ridecomp.platform.locale.AndroidAppLanguageStore
import com.ridecomp.platform.location.AndroidLocationSource
import com.ridecomp.platform.location.AndroidPressureSource
import com.ridecomp.platform.map.AndroidOfflineMapStore
import com.ridecomp.platform.route.AndroidGpxDocumentSource
import com.ridecomp.platform.safety.AndroidAccelerometerSource
import com.ridecomp.platform.safety.AndroidPickedContactReader
import com.ridecomp.platform.safety.AndroidSafetyMessages
import com.ridecomp.platform.safety.AndroidSmsGateway
import com.ridecomp.platform.service.RecordingDependencies
import com.ridecomp.platform.service.RideStateHolder
import com.ridecomp.platform.service.ServiceRideController
import com.ridecomp.platform.strava.AndroidStravaAccountStore
import com.ridecomp.platform.strava.HttpStravaApi
import com.ridecomp.platform.system.AndroidClock
import com.ridecomp.platform.system.AndroidLogger
import com.ridecomp.platform.system.StandardDispatcherProvider
import java.io.File
import org.koin.android.ext.koin.androidContext
import org.koin.core.Koin
import org.koin.dsl.module

/** Binds :core source interfaces to their :platform implementations (ARCH-03). */
val platformModule = module {
    single<Clock> { AndroidClock() }
    single<DispatcherProvider> { StandardDispatcherProvider() }
    single<Logger> { AndroidLogger(isDebugEnabled = BuildConfig.DEBUG) }
    single {
        val context = androidContext()
        CrashLogStore({ File(context.filesDir, CrashLogConfig.DIRECTORY_NAME) }, get())
    }
    single<LocationSource> { AndroidLocationSource(androidContext(), get()) }
    single<PressureSource> { AndroidPressureSource(androidContext(), get()) }
    single<BatteryLevelSource> { AndroidBatteryLevelSource(androidContext()) }
    single<IdGenerator> { UuidIdGenerator() }
    single<StravaAccountStore> { AndroidStravaAccountStore(androidContext(), get(), get()) }
    single<AppLanguageStore> { AndroidAppLanguageStore(androidContext()) }
    single { StravaSetup(BuildConfig.STRAVA_CLIENT_ID, BuildConfig.STRAVA_TOKEN_SERVER_URL) }
    single<StravaApi> { HttpStravaApi(get(), get(), get(), get()) }
    single<RideNameProvider> { AndroidRideNameProvider(androidContext()) }
    single { AlertPlayer(androidContext()) }
    single { RideStateHolder() }
    single<BleScanner> { AndroidBleScanner(androidContext()) }
    single<OfflineMapRepository> { AndroidOfflineMapStore(androidContext(), get(), get()) }
    single<SensorConnector> { AndroidSensorConnector(androidContext(), get(), get()) }
    single<SmsGateway> { AndroidSmsGateway(androidContext(), get(), get()) }
    single<SafetyMessages> { AndroidSafetyMessages(androidContext()) }
    single<PickedContactReader> { AndroidPickedContactReader(androidContext(), get()) }
    single<AccelerometerSource> { AndroidAccelerometerSource(androidContext(), get()) }
    single { IncidentManager(get(), get(), get(), get(), get(), get()) }
    single<BackupFiles> { AndroidBackupFiles(androidContext()) }
    single<BackupManifestCodec> { JsonBackupManifestCodec() }
    single<AppRestarter> { AndroidAppRestarter(androidContext()) }
    single<GpxDocumentSource> { AndroidGpxDocumentSource(androidContext()) }
    single<RideController> { ServiceRideController(androidContext(), get()) }
    factory { RecordingSession(get(), get(), get(), get()) }
}

/** Dependencies of the RecordingService, resolved from the Koin graph (decision D-20). */
class KoinRecordingDependencies(private val koin: Koin) : RecordingDependencies {
    override val activityRepository get() = koin.get<ActivityRepository>()
    override val settingsRepository get() = koin.get<SettingsRepository>()
    override val profileRepository get() = koin.get<ProfileRepository>()
    override val locationSource get() = koin.get<LocationSource>()
    override val pressureSource get() = koin.get<PressureSource>()
    override val batteryLevelSource get() = koin.get<BatteryLevelSource>()
    override val clock get() = koin.get<Clock>()
    override val dispatchers get() = koin.get<DispatcherProvider>()
    override val logger get() = koin.get<Logger>()
    override val stateHolder get() = koin.get<RideStateHolder>()
    override val alertPlayer get() = koin.get<AlertPlayer>()
    override val exportFit get() = koin.get<ExportFitUseCase>()
    override val getRideProfile get() = koin.get<GetRideProfileUseCase>()
    override val incidentManager get() = koin.get<IncidentManager>()
    override val accelerometerSource get() = koin.get<AccelerometerSource>()
    override val routeRepository get() = koin.get<RouteRepository>()
    override val getRouteGuidance get() = koin.get<GetRouteGuidanceUseCase>()
    override val getLiveSegments get() = koin.get<GetLiveSegmentsUseCase>()
    override val sensorRepository get() = koin.get<SensorRepository>()
    override val sensorConnector get() = koin.get<SensorConnector>()

    override fun newSession(): RecordingSession = koin.get()
}
