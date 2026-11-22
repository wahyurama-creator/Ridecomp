package com.ridecomp.app.di

import com.ridecomp.app.AppViewModel
import com.ridecomp.core.engine.backup.BackupManifestCodec
import com.ridecomp.core.engine.safety.IncidentManager
import com.ridecomp.core.engine.sensors.BleScanner
import com.ridecomp.core.engine.sensors.SensorProbe
import com.ridecomp.core.engine.sensors.SensorRepository
import com.ridecomp.core.model.AppLanguage
import com.ridecomp.core.model.BuiltInPlan
import com.ridecomp.core.model.PlanRef
import com.ridecomp.core.repository.ActivityAnalysisRepository
import com.ridecomp.core.repository.ActivityProfileRepository
import com.ridecomp.core.repository.ActivityRepository
import com.ridecomp.core.repository.BestEffortRepository
import com.ridecomp.core.repository.BikeComponentRepository
import com.ridecomp.core.repository.BikeRepository
import com.ridecomp.core.repository.GuideRepository
import com.ridecomp.core.repository.IncidentRepository
import com.ridecomp.core.repository.MapPackStateRepository
import com.ridecomp.core.repository.OfflineMapRepository
import com.ridecomp.core.repository.ProfileRepository
import com.ridecomp.core.repository.RidePositionRepository
import com.ridecomp.core.repository.RouteRepository
import com.ridecomp.core.repository.SafetyRepository
import com.ridecomp.core.repository.SegmentRepository
import com.ridecomp.core.repository.SettingsRepository
import com.ridecomp.core.repository.StravaUploadRepository
import com.ridecomp.core.repository.TrainingPlanRepository
import com.ridecomp.core.repository.TrainingRepository
import com.ridecomp.core.source.AccelerometerSource
import com.ridecomp.core.source.AppLanguageStore
import com.ridecomp.core.source.AppRestarter
import com.ridecomp.core.source.BatteryLevelSource
import com.ridecomp.core.source.Clock
import com.ridecomp.core.source.DispatcherProvider
import com.ridecomp.core.source.GpxDocumentSource
import com.ridecomp.core.source.LocationSource
import com.ridecomp.core.source.Logger
import com.ridecomp.core.source.MapCacheSource
import com.ridecomp.core.source.MapPackDownloader
import com.ridecomp.core.source.MapRegionIndexSource
import com.ridecomp.core.source.NetworkStatusSource
import com.ridecomp.core.source.PickedContactReader
import com.ridecomp.core.source.PressureSource
import com.ridecomp.core.source.RideController
import com.ridecomp.core.source.SafetyMessages
import com.ridecomp.core.source.SmsGateway
import com.ridecomp.core.source.StravaAccountStore
import com.ridecomp.core.source.StravaApi
import com.ridecomp.core.testing.FakeAccelerometerSource
import com.ridecomp.core.testing.FakeActivityAnalysisRepository
import com.ridecomp.core.testing.FakeActivityProfileRepository
import com.ridecomp.core.testing.FakeActivityRepository
import com.ridecomp.core.testing.FakeAppInfoSource
import com.ridecomp.core.testing.FakeAppRestarter
import com.ridecomp.core.testing.FakeBackupDatabase
import com.ridecomp.core.testing.FakeBackupFiles
import com.ridecomp.core.testing.FakeBackupManifestCodec
import com.ridecomp.core.testing.FakeBackupStateRepository
import com.ridecomp.core.testing.FakeBatteryLevelSource
import com.ridecomp.core.testing.FakeBestEffortRepository
import com.ridecomp.core.testing.FakeBikeComponentRepository
import com.ridecomp.core.testing.FakeBikeRepository
import com.ridecomp.core.testing.FakeBleScanner
import com.ridecomp.core.testing.FakeClock
import com.ridecomp.core.testing.FakeExportFileStore
import com.ridecomp.core.testing.FakeExportedFileHandler
import com.ridecomp.core.testing.FakeGpxDocumentSource
import com.ridecomp.core.testing.FakeGuideRepository
import com.ridecomp.core.testing.FakeIncidentRepository
import com.ridecomp.core.testing.FakeLocationSource
import com.ridecomp.core.testing.FakeMapCacheSource
import com.ridecomp.core.testing.FakeMapPackDownloader
import com.ridecomp.core.testing.FakeMapPackStateRepository
import com.ridecomp.core.testing.FakeMapRegionIndexSource
import com.ridecomp.core.testing.FakeNetworkStatusSource
import com.ridecomp.core.testing.FakeOfflineMapRepository
import com.ridecomp.core.testing.FakePickedContactReader
import com.ridecomp.core.testing.FakePressureSource
import com.ridecomp.core.testing.FakeProfileRepository
import com.ridecomp.core.testing.FakeReleaseSource
import com.ridecomp.core.testing.FakeRideController
import com.ridecomp.core.testing.FakeRidePositionRepository
import com.ridecomp.core.testing.FakeRouteRepository
import com.ridecomp.core.testing.FakeSafetyMessages
import com.ridecomp.core.testing.FakeSafetyRepository
import com.ridecomp.core.testing.FakeSegmentRepository
import com.ridecomp.core.testing.FakeSensorProbe
import com.ridecomp.core.testing.FakeSensorRepository
import com.ridecomp.core.testing.FakeSettingsRepository
import com.ridecomp.core.testing.FakeShareCardFiles
import com.ridecomp.core.testing.FakeSmsGateway
import com.ridecomp.core.testing.FakeStravaAccountStore
import com.ridecomp.core.testing.FakeStravaApi
import com.ridecomp.core.testing.FakeStravaUploadRepository
import com.ridecomp.core.testing.FakeTrainingPlanRepository
import com.ridecomp.core.testing.FakeTrainingRepository
import com.ridecomp.core.testing.FakeUpdateRepository
import com.ridecomp.core.testing.RecordingLogger
import com.ridecomp.core.testing.TestDispatcherProvider
import com.ridecomp.core.usecase.AppInfoSource
import com.ridecomp.core.usecase.BackupDatabase
import com.ridecomp.core.usecase.BackupFiles
import com.ridecomp.core.usecase.BackupStateRepository
import com.ridecomp.core.usecase.ExportFileStore
import com.ridecomp.core.usecase.ExportedFileHandler
import com.ridecomp.core.usecase.ReleaseSource
import com.ridecomp.core.usecase.ShareCardFiles
import com.ridecomp.core.usecase.UpdateRepository
import com.ridecomp.feature.history.viewmodel.ActivityDetailViewModel
import com.ridecomp.feature.history.viewmodel.HistoryViewModel
import com.ridecomp.feature.ride.viewmodel.ActivityProfileEditorViewModel
import com.ridecomp.feature.ride.viewmodel.ActivityProfilesViewModel
import com.ridecomp.feature.ride.viewmodel.BikeEditorViewModel
import com.ridecomp.feature.ride.viewmodel.BikeTarget
import com.ridecomp.feature.ride.viewmodel.BikesViewModel
import com.ridecomp.feature.ride.viewmodel.ComponentEditorViewModel
import com.ridecomp.feature.ride.viewmodel.ComponentTarget
import com.ridecomp.feature.ride.viewmodel.DataPagesEditorViewModel
import com.ridecomp.feature.ride.viewmodel.HomeViewModel
import com.ridecomp.feature.ride.viewmodel.PreRideViewModel
import com.ridecomp.feature.ride.viewmodel.RideViewModel
import com.ridecomp.feature.ride.viewmodel.SummaryViewModel
import com.ridecomp.feature.routes.viewmodel.RouteDetailViewModel
import com.ridecomp.feature.routes.viewmodel.RoutesViewModel
import com.ridecomp.feature.sensors.viewmodel.SensorsViewModel
import com.ridecomp.feature.settings.viewmodel.AppUpdateViewModel
import com.ridecomp.feature.settings.viewmodel.BackupViewModel
import com.ridecomp.feature.settings.viewmodel.GuideViewModel
import com.ridecomp.feature.settings.viewmodel.LanguageViewModel
import com.ridecomp.feature.settings.viewmodel.LicenseTextReader
import com.ridecomp.feature.settings.viewmodel.OfflineMapsViewModel
import com.ridecomp.feature.settings.viewmodel.OnboardingViewModel
import com.ridecomp.feature.settings.viewmodel.OssLicensesViewModel
import com.ridecomp.feature.settings.viewmodel.ProfileViewModel
import com.ridecomp.feature.settings.viewmodel.RideSettingsViewModel
import com.ridecomp.feature.settings.viewmodel.SafetyViewModel
import com.ridecomp.feature.settings.viewmodel.SettingsViewModel
import com.ridecomp.feature.settings.viewmodel.StravaViewModel
import com.ridecomp.feature.training.viewmodel.NewRecordsViewModel
import com.ridecomp.feature.training.viewmodel.PlanBuilderViewModel
import com.ridecomp.feature.training.viewmodel.PlanDetailViewModel
import com.ridecomp.feature.training.viewmodel.PlansViewModel
import com.ridecomp.feature.training.viewmodel.RecordsViewModel
import com.ridecomp.feature.training.viewmodel.SegmentDetailViewModel
import com.ridecomp.feature.training.viewmodel.SegmentEditorViewModel
import com.ridecomp.feature.training.viewmodel.SegmentsViewModel
import com.ridecomp.feature.training.viewmodel.ShareCardViewModel
import com.ridecomp.feature.training.viewmodel.SharePhotoLoader
import com.ridecomp.feature.training.viewmodel.TrainingViewModel
import java.io.File
import java.nio.file.Files
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
    private val tempDir: File = Files.createTempDirectory("app-modules").toFile()

    private val fakes = module {
        single<Clock> { FakeClock() }
        single<DispatcherProvider> { TestDispatcherProvider() }
        single<Logger> { RecordingLogger() }
        single<ActivityRepository> { FakeActivityRepository() }
        single<ProfileRepository> { FakeProfileRepository() }
        single<SettingsRepository> { FakeSettingsRepository() }
        single<LocationSource> { FakeLocationSource() }
        single<NetworkStatusSource> { FakeNetworkStatusSource() }
        single<MapCacheSource> { FakeMapCacheSource() }
        single<BatteryLevelSource> { FakeBatteryLevelSource() }
        single<RideController> { FakeRideController() }
        single<ExportFileStore> { FakeExportFileStore() }
        single<ExportedFileHandler> { FakeExportedFileHandler() }
        single<SensorRepository> { FakeSensorRepository() }
        single<BleScanner> { FakeBleScanner() }
        single<SensorProbe> { FakeSensorProbe() }
        single<ActivityProfileRepository> { FakeActivityProfileRepository() }
        single<BikeRepository> { FakeBikeRepository() }
        single<BikeComponentRepository> { FakeBikeComponentRepository() }
        single<TrainingPlanRepository> { FakeTrainingPlanRepository() }
        single<SafetyRepository> { FakeSafetyRepository() }
        single<SmsGateway> { FakeSmsGateway() }
        single<SafetyMessages> { FakeSafetyMessages() }
        single<PickedContactReader> { FakePickedContactReader() }
        single<IncidentRepository> { FakeIncidentRepository() }
        single<AccelerometerSource> { FakeAccelerometerSource() }
        single<PressureSource> { FakePressureSource() }
        single<BackupDatabase> { FakeBackupDatabase(tempDir) }
        single<BackupFiles> { FakeBackupFiles(tempDir) }
        single<BackupStateRepository> { FakeBackupStateRepository() }
        single<BackupManifestCodec> { FakeBackupManifestCodec() }
        single<AppRestarter> { FakeAppRestarter() }
        single<RouteRepository> { FakeRouteRepository() }
        single<GpxDocumentSource> { FakeGpxDocumentSource() }
        single { FakeOfflineMapRepository() }
        single<OfflineMapRepository> { get<FakeOfflineMapRepository>() }
        single<MapPackDownloader> { FakeMapPackDownloader(get()) }
        single<MapRegionIndexSource> { FakeMapRegionIndexSource() }
        single<MapPackStateRepository> { FakeMapPackStateRepository() }
        single<RidePositionRepository> { FakeRidePositionRepository() }
        single<BestEffortRepository> { FakeBestEffortRepository(get()) }
        single<SegmentRepository> { FakeSegmentRepository(get()) }
        single<TrainingRepository> { FakeTrainingRepository(FakeActivityRepository()) }
        single<ShareCardFiles> { FakeShareCardFiles() }
        single<ReleaseSource> { FakeReleaseSource() }
        single<AppInfoSource> { FakeAppInfoSource() }
        single<UpdateRepository> { FakeUpdateRepository() }
        single<GuideRepository> { FakeGuideRepository() }
        factory<SharePhotoLoader> { SharePhotoLoader { _, _ -> null } }
        single<ActivityAnalysisRepository> { FakeActivityAnalysisRepository(FakeActivityRepository()) }
        factory<LicenseTextReader> { LicenseTextReader { "" } }
        single<StravaAccountStore> { FakeStravaAccountStore() }
        single<StravaApi> { FakeStravaApi() }
        single { FakeStravaApi.SETUP }
        single<AppLanguageStore> {
            object : AppLanguageStore {
                override fun current() = AppLanguage.SYSTEM

                override fun set(language: AppLanguage) = Unit
            }
        }
        single<StravaUploadRepository> { FakeStravaUploadRepository() }
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
    fun `training plan view models resolve from the app graph`() {
        val koin = getKoin()

        assertNotNull(koin.get<PlansViewModel>())
        assertNotNull(koin.get<PlanDetailViewModel> { parametersOf(PlanRef.BuiltIn(BuiltInPlan.ZONE2_BASE)) })
        assertNotNull(koin.get<PlanBuilderViewModel> { parametersOf(null) })
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
        assertNotNull(koin.get<SettingsViewModel>())
        assertNotNull(koin.get<OfflineMapsViewModel>())
        assertNotNull(koin.get<OssLicensesViewModel>())
        assertNotNull(koin.get<GuideViewModel>())
        assertNotNull(koin.get<RecordsViewModel>())
        assertNotNull(koin.get<NewRecordsViewModel> { parametersOf(1L) })
        assertNotNull(koin.get<SegmentsViewModel>())
        assertNotNull(koin.get<TrainingViewModel>())
        assertNotNull(koin.get<ShareCardViewModel> { parametersOf(1L) })
        assertNotNull(koin.get<SegmentDetailViewModel> { parametersOf(1L) })
        assertNotNull(koin.get<SegmentEditorViewModel> { parametersOf(1L) })
        assertNotNull(koin.get<RideSettingsViewModel>())
        assertNotNull(koin.get<OnboardingViewModel>())
        assertNotNull(koin.get<HistoryViewModel>())
        assertNotNull(koin.get<ActivityDetailViewModel> { parametersOf(1L) })
        assertNotNull(koin.get<SensorsViewModel>())
        assertNotNull(koin.get<DataPagesEditorViewModel>())
        assertNotNull(koin.get<DataPagesEditorViewModel> { parametersOf(1L) })
        assertNotNull(koin.get<ActivityProfilesViewModel>())
        assertNotNull(koin.get<SafetyViewModel>())
        assertNotNull(koin.get<BackupViewModel>())
        assertNotNull(koin.get<StravaViewModel>())
        assertNotNull(koin.get<LanguageViewModel>())
        assertNotNull(koin.get<RoutesViewModel>())
        assertNotNull(koin.get<RouteDetailViewModel> { parametersOf(1L) })
        assertNotNull(koin.get<IncidentManager>())
        assertNotNull(koin.get<ActivityProfileEditorViewModel> { parametersOf(1L) })
        assertNotNull(koin.get<BikesViewModel>())
        assertNotNull(koin.get<ComponentEditorViewModel> { parametersOf(ComponentTarget(1L)) })
        assertNotNull(koin.get<BikeEditorViewModel> { parametersOf(BikeTarget.Existing(1L)) })
    }

    @Test
    fun `the update check resolves from the app graph`() {
        assertNotNull(getKoin().get<AppUpdateViewModel>())
    }
}
