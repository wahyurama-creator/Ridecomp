package com.ridecomp.feature.ride

import app.cash.turbine.test
import com.ridecomp.core.engine.map.NetworkStatus
import com.ridecomp.core.engine.recording.LiveRide
import com.ridecomp.core.engine.recording.RideAction
import com.ridecomp.core.engine.safety.IncidentAction
import com.ridecomp.core.engine.sensors.TrainerControl
import com.ridecomp.core.engine.training.TargetSource
import com.ridecomp.core.engine.training.TargetStatus
import com.ridecomp.core.engine.update.AppRelease
import com.ridecomp.core.model.ActivePlanState
import com.ridecomp.core.model.ActivityKind
import com.ridecomp.core.model.ActivityStatus
import com.ridecomp.core.model.Bike
import com.ridecomp.core.model.BuiltInPlan
import com.ridecomp.core.model.EmergencyContact
import com.ridecomp.core.model.Intensity
import com.ridecomp.core.model.PlanRef
import com.ridecomp.core.model.PlanSession
import com.ridecomp.core.model.RideSample
import com.ridecomp.core.model.RideStatus
import com.ridecomp.core.model.RiderProfile
import com.ridecomp.core.model.SafetySettings
import com.ridecomp.core.model.UnitSystem
import com.ridecomp.core.repository.NewActivity
import com.ridecomp.core.testing.FakeActivityProfileRepository
import com.ridecomp.core.testing.FakeActivityRepository
import com.ridecomp.core.testing.FakeAppInfoSource
import com.ridecomp.core.testing.FakeBackupStateRepository
import com.ridecomp.core.testing.FakeBatteryLevelSource
import com.ridecomp.core.testing.FakeBikeComponentRepository
import com.ridecomp.core.testing.FakeBikeRepository
import com.ridecomp.core.testing.FakeClock
import com.ridecomp.core.testing.FakeLocationSource
import com.ridecomp.core.testing.FakeNetworkStatusSource
import com.ridecomp.core.testing.FakeOfflineMapRepository
import com.ridecomp.core.testing.FakeProfileRepository
import com.ridecomp.core.testing.FakeReleaseSource
import com.ridecomp.core.testing.FakeRideController
import com.ridecomp.core.testing.FakeRouteRepository
import com.ridecomp.core.testing.FakeSafetyRepository
import com.ridecomp.core.testing.FakeSensorRepository
import com.ridecomp.core.testing.FakeSettingsRepository
import com.ridecomp.core.testing.FakeTrainingPlanRepository
import com.ridecomp.core.testing.FakeUpdateRepository
import com.ridecomp.core.testing.TestDispatcherProvider
import com.ridecomp.core.usecase.CheckForUpdateUseCase
import com.ridecomp.core.usecase.CheckGpsAvailabilityUseCase
import com.ridecomp.core.usecase.DismissUpdateUseCase
import com.ridecomp.core.usecase.GetRecoverableRideUseCase
import com.ridecomp.core.usecase.GetRoutePointsUseCase
import com.ridecomp.core.usecase.GpsStatus
import com.ridecomp.core.usecase.HandleRecoveredRideUseCase
import com.ridecomp.core.usecase.LoadRideTrailUseCase
import com.ridecomp.core.usecase.ObserveActivePlanUseCase
import com.ridecomp.core.usecase.ObserveActivityProfilesUseCase
import com.ridecomp.core.usecase.ObserveActivityUseCase
import com.ridecomp.core.usecase.ObserveAvailableUpdateUseCase
import com.ridecomp.core.usecase.ObserveBackupReminderUseCase
import com.ridecomp.core.usecase.ObserveBikeChoiceUseCase
import com.ridecomp.core.usecase.ObserveComponentWearUseCase
import com.ridecomp.core.usecase.ObserveDataPagesUseCase
import com.ridecomp.core.usecase.ObserveGpsStatusUseCase
import com.ridecomp.core.usecase.ObserveHasProfileUseCase
import com.ridecomp.core.usecase.ObserveHistoryPageUseCase
import com.ridecomp.core.usecase.ObserveLiveRideUseCase
import com.ridecomp.core.usecase.ObserveMapTilesUseCase
import com.ridecomp.core.usecase.ObserveProfileChoiceUseCase
import com.ridecomp.core.usecase.ObserveProfileUseCase
import com.ridecomp.core.usecase.ObserveRideSettingsUseCase
import com.ridecomp.core.usecase.ObserveRouteUseCase
import com.ridecomp.core.usecase.ObserveSafetyUseCase
import com.ridecomp.core.usecase.ObserveSavedSensorsUseCase
import com.ridecomp.core.usecase.ObserveTrainerUseCase
import com.ridecomp.core.usecase.PerformIncidentActionUseCase
import com.ridecomp.core.usecase.PerformRideActionUseCase
import com.ridecomp.core.usecase.ReadBatteryUseCase
import com.ridecomp.core.usecase.RecoveryChoice
import com.ridecomp.core.usecase.RenameActivityUseCase
import com.ridecomp.core.usecase.SaveRideSettingsUseCase
import com.ridecomp.core.usecase.SelectBikeUseCase
import com.ridecomp.core.usecase.SetTrainerTargetUseCase
import com.ridecomp.core.usecase.SnoozeBackupReminderUseCase
import com.ridecomp.core.usecase.StartRideUseCase
import com.ridecomp.core.usecase.TrainingCalendar
import com.ridecomp.core.util.UnitFormatter
import com.ridecomp.feature.ride.model.DefaultDataPages
import com.ridecomp.feature.ride.model.MetricCategory
import com.ridecomp.feature.ride.model.RideMetric
import com.ridecomp.feature.ride.ui.component.ErgPresets
import com.ridecomp.feature.ride.ui.component.TrainerAction
import com.ridecomp.feature.ride.viewmodel.HomeDeviceUseCases
import com.ridecomp.feature.ride.viewmodel.HomeEffect
import com.ridecomp.feature.ride.viewmodel.HomeUiEvent
import com.ridecomp.feature.ride.viewmodel.HomeUpdateUseCases
import com.ridecomp.feature.ride.viewmodel.HomeUseCases
import com.ridecomp.feature.ride.viewmodel.HomeViewModel
import com.ridecomp.feature.ride.viewmodel.PreRideBikeUseCases
import com.ridecomp.feature.ride.viewmodel.PreRideEffect
import com.ridecomp.feature.ride.viewmodel.PreRideOptionUseCases
import com.ridecomp.feature.ride.viewmodel.PreRideUiEvent
import com.ridecomp.feature.ride.viewmodel.PreRideUseCases
import com.ridecomp.feature.ride.viewmodel.PreRideViewModel
import com.ridecomp.feature.ride.viewmodel.RideEffect
import com.ridecomp.feature.ride.viewmodel.RideMapUseCases
import com.ridecomp.feature.ride.viewmodel.RideTrainerUseCases
import com.ridecomp.feature.ride.viewmodel.RideUseCases
import com.ridecomp.feature.ride.viewmodel.RideViewModel
import com.ridecomp.feature.ride.viewmodel.SummaryEffect
import com.ridecomp.feature.ride.viewmodel.SummaryUiEvent
import com.ridecomp.feature.ride.viewmodel.SummaryViewModel
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class RideViewModelsTest {
    private val controller = FakeRideController()
    private val repository = FakeActivityRepository()
    private val settings = FakeSettingsRepository()
    private val profiles = FakeActivityProfileRepository()
    private val network = FakeNetworkStatusSource()
    private val plans = FakeTrainingPlanRepository()
    private val trainer = TrainerControl()
    private val trainerProfile = FakeProfileRepository()
    private val bikes = FakeBikeRepository(
        listOf(Bike(id = 1, profileId = 1), Bike(id = TRAINER_BIKE_ID, name = "Trainer", profileId = INDOOR_ID)),
    )

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val backupState = FakeBackupStateRepository()
    private val updates = FakeUpdateRepository()
    private val riderProfiles = FakeProfileRepository()
    private val releases = FakeReleaseSource()

    private fun homeViewModel() = HomeViewModel(
        HomeUseCases(
            GetRecoverableRideUseCase(repository),
            HandleRecoveredRideUseCase(controller, repository),
            ObserveHistoryPageUseCase(repository),
            ObserveLiveRideUseCase(controller),
            ObserveRideSettingsUseCase(settings),
            ObserveBackupReminderUseCase(backupState, repository, FakeClock()),
            SnoozeBackupReminderUseCase(backupState, FakeClock()),
            HomeDeviceUseCases(
                ReadBatteryUseCase(FakeBatteryLevelSource()),
                ObserveSavedSensorsUseCase(FakeSensorRepository()),
                CheckGpsAvailabilityUseCase(FakeLocationSource()),
            ),
            ObserveActivePlanUseCase(plans, TrainingCalendar(FakeClock())),
            HomeUpdateUseCases(
                CheckForUpdateUseCase(updates, releases, FakeAppInfoSource("0.9.1"), FakeClock()),
                ObserveAvailableUpdateUseCase(updates, FakeAppInfoSource("0.9.1")),
                DismissUpdateUseCase(updates),
            ),
            emptyHomeMapUseCases(controller),
        ),
    )

    @Test
    fun `home checks for a new version once, opens its page and can put it off`() = runTest {
        releases.release = AppRelease("0.9.2", UPDATE_PAGE)
        val viewModel = homeViewModel()
        assertEquals("0.9.2", viewModel.uiState.value.update?.version)
        assertEquals(1, releases.requests)

        viewModel.effects.test {
            viewModel.onEvent(HomeUiEvent.OpenUpdate)
            assertEquals(HomeEffect.OpenUrl(UPDATE_PAGE), awaitItem())
        }
        viewModel.onEvent(HomeUiEvent.DismissUpdate)
        assertEquals(null, viewModel.uiState.value.update)
        homeViewModel()
        assertEquals(1, releases.requests)
    }

    @Test
    fun `home offers recovery for an unfinished ride and resumes it`() = runTest {
        val id = repository.create(NewActivity("u", "Ride", 0))
        val viewModel = homeViewModel()
        assertEquals(id, viewModel.uiState.value.recoverable?.id)

        viewModel.effects.test {
            viewModel.onEvent(HomeUiEvent.Recover(RecoveryChoice.RESUME))
            assertEquals(HomeEffect.OpenRide, awaitItem())
        }
        assertEquals(listOf("RESUME:$id"), controller.commands)
        assertNull(viewModel.uiState.value.recoverable)
    }

    @Test
    fun `the ride currently recorded is never offered for recovery`() = runTest {
        repository.create(NewActivity("u", "Ride", 0))
        controller.live.value = LiveRide(status = RideStatus.RECORDING)

        val viewModel = homeViewModel()

        assertNull(viewModel.uiState.value.recoverable)
        assertTrue(viewModel.uiState.value.isRideInProgress)
    }

    private fun preRideViewModel(location: FakeLocationSource = FakeLocationSource()) = PreRideViewModel(
        routeId = null,
        PreRideUseCases(
            ObserveGpsStatusUseCase(location),
            ReadBatteryUseCase(FakeBatteryLevelSource()),
            PreRideBikeUseCases(
                ObserveBikeChoiceUseCase(bikes),
                SelectBikeUseCase(bikes),
                ObserveProfileChoiceUseCase(profiles),
                ObserveComponentWearUseCase(FakeBikeComponentRepository()),
            ),
            ObserveRouteUseCase(FakeRouteRepository()),
            StartRideUseCase(controller),
            PreRideOptionUseCases(
                ObserveSavedSensorsUseCase(FakeSensorRepository()),
                ObserveRideSettingsUseCase(settings),
                SaveRideSettingsUseCase(settings),
                ObserveActivePlanUseCase(plans, TrainingCalendar(FakeClock())),
                ObserveHasProfileUseCase(riderProfiles),
            ),
        ),
    )

    @Test
    fun `a skipped profile is offered on the pre-ride check until it is saved`() = runTest {
        val viewModel = preRideViewModel()
        assertTrue(viewModel.uiState.value.isProfileMissing)

        riderProfiles.save(RiderProfile())
        assertFalse(viewModel.uiState.value.isProfileMissing)
    }

    @Test
    fun `today's planned session is used by default and can be turned off`() = runTest {
        plans.active.value = ActivePlanState(PlanRef.BuiltIn(BuiltInPlan.ZONE2_BASE), FakeClock().nowMs() / DAY_MS)
        val viewModel = preRideViewModel()
        assertEquals(PlanSession(Intensity.ENDURANCE, 45), viewModel.uiState.value.session)

        viewModel.onEvent(PreRideUiEvent.Start)
        assertTrue(controller.startedWithSession)

        viewModel.onEvent(PreRideUiEvent.SessionOn(false))
        viewModel.onEvent(PreRideUiEvent.Start)
        assertFalse(controller.startedWithSession)
    }

    @Test
    fun `pre ride start launches the ride with the picked bike's profile`() = runTest {
        val location = FakeLocationSource()
        val viewModel = preRideViewModel(location)
        assertEquals(ActivityKind.ROAD, viewModel.uiState.value.selected?.kind)
        // The trainer bike is an indoor bike: its type decides the profile (D-89).
        viewModel.onEvent(PreRideUiEvent.SelectBike(TRAINER_BIKE_ID))
        assertEquals(TRAINER_BIKE_ID, viewModel.uiState.value.bike?.id)
        assertEquals(ActivityKind.INDOOR, viewModel.uiState.value.selected?.kind)
        viewModel.gpsStatus.test {
            // Indoor: no GPS is started at all (BAT-01).
            assertEquals(GpsStatus.NotUsed, expectMostRecentItem())
            assertEquals(0, location.activeCollectors)
        }
        assertEquals(80, viewModel.uiState.value.battery?.percent)

        viewModel.effects.test {
            viewModel.onEvent(PreRideUiEvent.Start)
            assertEquals(PreRideEffect.OpenRide, awaitItem())
        }
        assertEquals(listOf(FakeRideController.START), controller.commands)
        assertEquals(INDOOR_ID, controller.startedProfileId)
        assertEquals(false, controller.startedWithSession)
    }

    private fun rideViewModel(safety: FakeSafetyRepository = FakeSafetyRepository()) = RideViewModel(
        RideUseCases(
            ObserveLiveRideUseCase(controller),
            ObserveRideSettingsUseCase(settings),
            ObserveDataPagesUseCase(settings, profiles),
            ObserveActivityProfilesUseCase(profiles),
            ObserveSafetyUseCase(safety),
            PerformRideActionUseCase(controller),
            PerformIncidentActionUseCase(controller),
            GetRoutePointsUseCase(FakeRouteRepository()),
        ),
        TestDispatcherProvider(UnconfinedTestDispatcher()),
        RideMapUseCases(
            ObserveMapTilesUseCase(network, settings, FakeOfflineMapRepository()),
            LoadRideTrailUseCase(repository),
        ),
        RideTrainerUseCases(
            ObserveTrainerUseCase(trainer),
            SetTrainerTargetUseCase(trainer),
            ObserveProfileUseCase(trainerProfile),
        ),
    )

    @Test
    fun `an interval session on a trainer offers easy and zone buttons that set ERG`() = runTest {
        trainerProfile.save(RiderProfile(ftpW = 200))
        val viewModel = rideViewModel()
        controller.live.value = LiveRide(
            status = RideStatus.RECORDING,
            target = TargetStatus(Intensity.THRESHOLD, 3_600_000, TargetSource.POWER),
        )

        viewModel.ergPresets.test {
            assertEquals(ErgPresets(easyW = 130, zone = 4, zoneW = 196), expectMostRecentItem())
        }
        viewModel.onTrainer(TrainerAction.Set(196))
        viewModel.onTrainer(TrainerAction.Adjust(10))
        assertEquals(206, trainer.state.value.targetW)
    }

    @Test
    fun `a ride without a route draws the stored and live trail on the online map`() = runTest {
        val id = repository.create(NewActivity("u", "Ride", 0))
        repository.appendSamples(id, listOf(RideSample(timeMs = 0, latDeg = -7.0, lonDeg = 110.0)))
        controller.activityId.value = id
        controller.live.value = LiveRide(status = RideStatus.RECORDING, latDeg = -7.0, lonDeg = 110.0)
        val viewModel = rideViewModel()

        viewModel.map.test {
            assertEquals(1, expectMostRecentItem().trail.size)
            controller.live.value = LiveRide(status = RideStatus.RECORDING, latDeg = -7.001, lonDeg = 110.0)
            val map = expectMostRecentItem()
            assertEquals(2, map.trail.size)
            assertTrue(map.isOnline)
            network.status.value = NetworkStatus.OFFLINE
            // The tiles just seen online stay on the map from the cache, without the network (T11.11).
            val offline = expectMostRecentItem()
            assertEquals(false, offline.isOnline)
            assertTrue(offline.isCached)
            assertTrue(offline.hasMap)
        }
        // Leaving the screen stops watching the network (09 §1).
        advanceTimeBy(STOP_TIMEOUT_MS + 1)
        assertEquals(0, network.activeCollectors)
    }

    @Test
    fun `back to start follows the ridden line once turned on`() = runTest {
        val id = repository.create(NewActivity("u", "Ride", 0))
        repository.appendSamples(id, listOf(RideSample(timeMs = 0, latDeg = -7.0, lonDeg = 110.0)))
        controller.activityId.value = id
        controller.live.value = LiveRide(status = RideStatus.RECORDING, latDeg = -7.01, lonDeg = 110.0)
        val viewModel = rideViewModel()

        viewModel.backToStart.test {
            assertEquals(null, expectMostRecentItem())
            viewModel.toggleBackToStart()
            val back = assertNotNull(expectMostRecentItem())
            assertEquals(-7.0, back.start.latDeg)
            assertTrue(back.straightM > 1_000.0)
            viewModel.toggleBackToStart()
            assertEquals(null, expectMostRecentItem())
        }
    }

    @Test
    fun `ride screen opens the summary when finished and closes when the ride ends`() = runTest {
        val safety = FakeSafetyRepository()
        val viewModel = rideViewModel(safety)
        // SOS only with the feature on and a contact (12-FEATURE-SPECS §5.3).
        viewModel.isSosAvailable.test {
            assertEquals(false, awaitItem())
            safety.addContact(EmergencyContact(displayName = "Ibu", phone = "0811000000"))
            safety.saveSettings(SafetySettings(isEnabled = true))
            assertEquals(true, expectMostRecentItem())
        }
        viewModel.onIncident(IncidentAction.SOS)
        assertEquals("${FakeRideController.INCIDENT_PREFIX}SOS", controller.commands.single())
        controller.commands.clear()

        viewModel.effects.test {
            controller.live.value = LiveRide(status = RideStatus.RECORDING)
            controller.live.value = LiveRide(status = RideStatus.FINISHED)
            assertEquals(RideEffect.OpenSummary, awaitItem())
            controller.live.value = LiveRide(status = RideStatus.IDLE)
            assertEquals(RideEffect.Close, awaitItem())
        }
        viewModel.onEvent(RideAction.LAP)
        assertEquals(listOf("LAP"), controller.commands)
    }

    @Test
    fun `summary renames before saving`() = runTest {
        val id = repository.create(NewActivity("u", "Morning ride", 0))
        controller.activityId.value = id
        val viewModel = SummaryViewModel(
            ObserveLiveRideUseCase(controller),
            ObserveActivityUseCase(repository),
            ObserveRideSettingsUseCase(settings),
            RenameActivityUseCase(repository),
            PerformRideActionUseCase(controller),
        )
        assertEquals("Morning ride", viewModel.uiState.value.name)

        viewModel.effects.test {
            viewModel.onEvent(SummaryUiEvent.NameChanged("Kaliurang"))
            viewModel.onEvent(SummaryUiEvent.Save)
            assertEquals(SummaryEffect.Saved(id), awaitItem())
        }
        assertEquals("Kaliurang", repository.get(id)?.name)
        assertEquals(listOf("SAVE"), controller.commands)
        assertEquals(ActivityStatus.RECORDING, repository.get(id)?.status)
    }

    @Test
    fun `every metric is offered in exactly one picker category`() {
        val listed = MetricCategory.entries.flatMap { it.metrics }
        assertEquals(RideMetric.entries.toSet(), listed.toSet())
        assertEquals(listed.size, listed.toSet().size)
    }

    @Test
    fun `metrics show placeholders for missing data and units`() {
        val formatter = UnitFormatter(UnitSystem.METRIC, Locale.US)
        val live = LiveRide(distanceM = 12_345.0, movingMs = 3_723_000)

        assertEquals("12.35", RideMetric.DISTANCE.value(live, formatter))
        assertEquals("1:02:03", RideMetric.MOVING_TIME.value(live, formatter))
        assertEquals(UnitFormatter.NO_VALUE, RideMetric.SPEED.value(live, formatter))
        assertEquals(UnitFormatter.NO_VALUE, RideMetric.HEART_RATE.value(live, formatter))
        // Hero + 6 fields (13-UI-SCREENS #6, D-54).
        assertEquals(7, DefaultDataPages.MAIN.metrics.size)
    }

    private companion object {
        /** Built-in ids follow BuiltInProfiles order in the fake (Road = 1 … Indoor = 4). */
        const val INDOOR_ID = 4L
        const val TRAINER_BIKE_ID = 2L
        const val UPDATE_PAGE = "https://github.com/wahyurama-creator/Ridecomp/releases/tag/v0.9.2"
        const val STOP_TIMEOUT_MS = 5_000L
        const val DAY_MS = 86_400_000L
    }
}
