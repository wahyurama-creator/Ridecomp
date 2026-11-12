package com.ridecomp.app.di

import com.ridecomp.app.AppViewModel
import com.ridecomp.core.engine.sensors.BleScanner
import com.ridecomp.core.engine.sensors.SensorRepository
import com.ridecomp.core.repository.ActivityRepository
import com.ridecomp.core.repository.ProfileRepository
import com.ridecomp.core.repository.SettingsRepository
import com.ridecomp.core.source.BatteryLevelSource
import com.ridecomp.core.source.Clock
import com.ridecomp.core.source.DispatcherProvider
import com.ridecomp.core.source.LocationSource
import com.ridecomp.core.source.Logger
import com.ridecomp.core.source.RideController
import com.ridecomp.core.testing.FakeActivityRepository
import com.ridecomp.core.testing.FakeBatteryLevelSource
import com.ridecomp.core.testing.FakeBleScanner
import com.ridecomp.core.testing.FakeClock
import com.ridecomp.core.testing.FakeExportFileStore
import com.ridecomp.core.testing.FakeExportedFileHandler
import com.ridecomp.core.testing.FakeLocationSource
import com.ridecomp.core.testing.FakeProfileRepository
import com.ridecomp.core.testing.FakeRideController
import com.ridecomp.core.testing.FakeSensorRepository
import com.ridecomp.core.testing.FakeSettingsRepository
import com.ridecomp.core.testing.RecordingLogger
import com.ridecomp.core.testing.TestDispatcherProvider
import com.ridecomp.core.usecase.ExportFileStore
import com.ridecomp.core.usecase.ExportedFileHandler
import com.ridecomp.feature.history.viewmodel.ActivityDetailViewModel
import com.ridecomp.feature.history.viewmodel.HistoryViewModel
import com.ridecomp.feature.ride.viewmodel.HomeViewModel
import com.ridecomp.feature.ride.viewmodel.PreRideViewModel
import com.ridecomp.feature.ride.viewmodel.RideViewModel
import com.ridecomp.feature.ride.viewmodel.SummaryViewModel
import com.ridecomp.feature.sensors.viewmodel.SensorsViewModel
import com.ridecomp.feature.settings.viewmodel.OnboardingViewModel
import com.ridecomp.feature.settings.viewmodel.ProfileViewModel
import com.ridecomp.feature.settings.viewmodel.RideSettingsViewModel
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.parameter.parametersOf
import org.koin.dsl.module
import org.koin.java.KoinJavaComponent.getKoin

/**
 * Guards the composition root: every ViewModel used by a screen and the core sources must resolve from
 * the real module list. Android-backed definitions are replaced by fakes, so this runs on the JVM and a
 * forgotten registration fails the build instead of crashing the app.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AppModulesTest {
    private val fakes = module {
        single<Clock> { FakeClock() }
        single<DispatcherProvider> { TestDispatcherProvider() }
        single<Logger> { RecordingLogger() }
        single<ActivityRepository> { FakeActivityRepository() }
        single<ProfileRepository> { FakeProfileRepository() }
        single<SettingsRepository> { FakeSettingsRepository() }
        single<LocationSource> { FakeLocationSource() }
        single<BatteryLevelSource> { FakeBatteryLevelSource() }
        single<RideController> { FakeRideController() }
        single<ExportFileStore> { FakeExportFileStore() }
        single<ExportedFileHandler> { FakeExportedFileHandler() }
        single<SensorRepository> { FakeSensorRepository() }
        single<BleScanner> { FakeBleScanner() }
    }

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        startKoin {
            allowOverride(true)
            modules(appModules + fakes)
        }
    }

    @AfterTest
    fun tearDown() {
        stopKoin()
        Dispatchers.resetMain()
    }

    @Test
    fun `every screen view model resolves from the app graph`() {
        val koin = getKoin()

        assertNotNull(koin.get<AppViewModel>())
        assertNotNull(koin.get<HomeViewModel>())
        assertNotNull(koin.get<PreRideViewModel>())
        assertNotNull(koin.get<RideViewModel>())
        assertNotNull(koin.get<SummaryViewModel>())
        assertNotNull(koin.get<ProfileViewModel>())
        assertNotNull(koin.get<RideSettingsViewModel>())
        assertNotNull(koin.get<OnboardingViewModel>())
        assertNotNull(koin.get<HistoryViewModel>())
        assertNotNull(koin.get<ActivityDetailViewModel> { parametersOf(1L) })
        assertNotNull(koin.get<SensorsViewModel>())
    }
}
