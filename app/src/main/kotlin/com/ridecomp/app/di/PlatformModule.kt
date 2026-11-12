package com.ridecomp.app.di

import com.ridecomp.app.BuildConfig
import com.ridecomp.core.engine.recording.RecordingSession
import com.ridecomp.core.repository.ActivityRepository
import com.ridecomp.core.repository.SettingsRepository
import com.ridecomp.core.source.BatteryLevelSource
import com.ridecomp.core.source.Clock
import com.ridecomp.core.source.DispatcherProvider
import com.ridecomp.core.source.IdGenerator
import com.ridecomp.core.source.LocationSource
import com.ridecomp.core.source.Logger
import com.ridecomp.core.source.PressureSource
import com.ridecomp.core.source.RideController
import com.ridecomp.core.source.RideNameProvider
import com.ridecomp.platform.crash.CrashLogConfig
import com.ridecomp.platform.crash.CrashLogStore
import com.ridecomp.platform.device.AlertPlayer
import com.ridecomp.platform.device.AndroidBatteryLevelSource
import com.ridecomp.platform.device.AndroidRideNameProvider
import com.ridecomp.platform.device.UuidIdGenerator
import com.ridecomp.platform.location.AndroidLocationSource
import com.ridecomp.platform.location.AndroidPressureSource
import com.ridecomp.platform.service.RecordingDependencies
import com.ridecomp.platform.service.RideStateHolder
import com.ridecomp.platform.service.ServiceRideController
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
    single { CrashLogStore(File(androidContext().filesDir, CrashLogConfig.DIRECTORY_NAME), get()) }
    single<LocationSource> { AndroidLocationSource(androidContext(), get()) }
    single<PressureSource> { AndroidPressureSource(androidContext(), get()) }
    single<BatteryLevelSource> { AndroidBatteryLevelSource(androidContext()) }
    single<IdGenerator> { UuidIdGenerator() }
    single<RideNameProvider> { AndroidRideNameProvider(androidContext()) }
    single { AlertPlayer(androidContext()) }
    single { RideStateHolder() }
    single<RideController> { ServiceRideController(androidContext(), get()) }
    factory { RecordingSession(get(), get(), get(), get()) }
}

/** Dependencies of the RecordingService, resolved from the Koin graph (decision D-20). */
class KoinRecordingDependencies(private val koin: Koin) : RecordingDependencies {
    override val activityRepository get() = koin.get<ActivityRepository>()
    override val settingsRepository get() = koin.get<SettingsRepository>()
    override val locationSource get() = koin.get<LocationSource>()
    override val pressureSource get() = koin.get<PressureSource>()
    override val batteryLevelSource get() = koin.get<BatteryLevelSource>()
    override val clock get() = koin.get<Clock>()
    override val dispatchers get() = koin.get<DispatcherProvider>()
    override val logger get() = koin.get<Logger>()
    override val stateHolder get() = koin.get<RideStateHolder>()
    override val alertPlayer get() = koin.get<AlertPlayer>()

    override fun newSession(): RecordingSession = koin.get()
}
