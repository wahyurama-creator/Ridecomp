package com.ridecomp.core.usecase

import com.ridecomp.core.engine.efforts.BestEffort
import com.ridecomp.core.engine.efforts.BestEffortKey
import com.ridecomp.core.model.Activity
import com.ridecomp.core.model.ActivityKind
import com.ridecomp.core.model.ActivityStatus
import com.ridecomp.core.model.RideSample
import com.ridecomp.core.model.RideSettings
import com.ridecomp.core.model.RideTotals
import com.ridecomp.core.model.RiderLevel
import com.ridecomp.core.model.RiderProfile
import com.ridecomp.core.repository.NewActivity
import com.ridecomp.core.repository.RideEffort
import com.ridecomp.core.testing.FakeActivityRepository
import com.ridecomp.core.testing.FakeBestEffortRepository
import com.ridecomp.core.testing.FakeClock
import com.ridecomp.core.testing.FakeGuideRepository
import com.ridecomp.core.testing.FakeProfileRepository
import com.ridecomp.core.testing.FakeSettingsRepository
import com.ridecomp.core.testing.FakeTrainingRepository
import com.ridecomp.core.testing.TestDispatcherProvider
import com.ridecomp.core.util.Outcome
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest

class TimeOfDayTest {
    @Test
    fun `hours map to parts of the day`() {
        assertEquals(TimeOfDay.EVENING, TimeOfDay.ofHour(2))
        assertEquals(TimeOfDay.MORNING, TimeOfDay.ofHour(6))
        assertEquals(TimeOfDay.NOON, TimeOfDay.ofHour(12))
        assertEquals(TimeOfDay.AFTERNOON, TimeOfDay.ofHour(16))
        assertEquals(TimeOfDay.EVENING, TimeOfDay.ofHour(20))
    }
}

class ActivityUseCasesTest {
    private val repository = FakeActivityRepository()
    private val profiles = FakeProfileRepository()

    private val efforts = FakeBestEffortRepository(repository)
    private val guide = FakeGuideRepository()
    private val estimateFtp = EstimateFtpUseCase(profiles, efforts, guide, FakeClock())

    private fun analysisUseCase() =
        LoadActivityAnalysisUseCase(LoadRideChartUseCase(repository), repository, profiles, estimateFtp, FakeClock())

    private fun stored(id: Long, status: ActivityStatus, startedAt: Long) = Activity(
        id = id,
        uuid = "u$id",
        name = "Ride $id",
        status = status,
        startedAtMs = startedAt,
        endedAtMs = null,
        totals = RideTotals(),
        lastCheckpointAtMs = startedAt,
    )

    @Test
    fun `recoverable ride is the latest unfinished one`() = runTest {
        repository.put(stored(1, ActivityStatus.FINISHED, 10))
        repository.put(stored(2, ActivityStatus.PAUSED, 20))

        assertEquals(2L, GetRecoverableRideUseCase(repository)()?.id)
    }

    @Test
    fun `no recoverable ride when all are finished`() = runTest {
        repository.put(stored(1, ActivityStatus.FINISHED, 10))

        assertNull(GetRecoverableRideUseCase(repository)())
    }

    @Test
    fun `history is paged newest first`() = runTest {
        (1L..5L).forEach { repository.put(stored(it, ActivityStatus.FINISHED, it)) }

        val page = ObserveHistoryPageUseCase(repository)(page = 1, pageSize = 2).first().map { it.id }

        assertEquals(listOf(3L, 2L), page)
        assertEquals(5, ObserveHistoryCountUseCase(repository)().first())
    }

    @Test
    fun `rename trims and ignores blank names`() = runTest {
        val id = repository.create(NewActivity("u", "Old", 0))
        val rename = RenameActivityUseCase(repository)

        rename(id, "  Gowes Kaliurang  ")
        rename(id, "   ")

        assertEquals("Gowes Kaliurang", repository.get(id)?.name)
        assertEquals("Gowes Kaliurang", ObserveActivityUseCase(repository)(id).first()?.name)
    }

    @Test
    fun `delete removes the ride`() = runTest {
        val id = repository.create(NewActivity("u", "Ride", 0))

        DeleteActivityUseCase(
            repository,
            RecomputeTrainingUseCase(
                FakeTrainingRepository(repository),
                TrainingCalendar(FakeClock()),
                TestDispatcherProvider(StandardTestDispatcher(testScheduler)),
            ),
        )(id)

        assertNull(repository.get(id))
        assertEquals(emptyList(), GetLapsUseCase(repository)(id))
    }

    @Test
    fun `chart is downsampled to the maximum point count and keeps order`() = runTest {
        val id = repository.create(NewActivity("u", "Ride", 0))
        repository.appendSamples(id, (1..3_500).map { RideSample(timeMs = it * 1_000L, distanceM = it * 8.0) })

        val chart = LoadRideChartUseCase(repository)(id, maxPoints = 1_000)

        assertTrue(chart.size <= 1_000)
        assertTrue(chart.size >= 800)
        assertEquals(chart.map { it.distanceM }.sorted(), chart.map { it.distanceM })
    }

    @Test
    fun `a 3 hour ride becomes at most 300 averaged points, gaps stay gaps`() = runTest {
        val id = repository.create(NewActivity("u", "Ride", 0))
        val samples = (1..10_800).map {
            RideSample(
                timeMs = it * 1_000L,
                distanceM = it * 7.0,
                speedMps = if (it % 2 == 0) 6.0 else 8.0,
                // No heart rate in the first hour (sensor not connected yet).
                heartRateBpm = if (it > 3_600) 140 else null,
            )
        }
        repository.appendSamples(id, samples)

        val chart = LoadRideChartUseCase(repository)(id)

        assertEquals(300, chart.size)
        assertTrue(chart.all { it.speedMps == 7.0 })
        assertTrue(chart.take(99).all { it.heartRateBpm == null })
        assertTrue(chart.drop(101).all { it.heartRateBpm == 140 })
        assertEquals(samples.last().distanceM, chart.last().distanceM)
    }

    @Test
    fun `chart of an empty ride is empty`() = runTest {
        val id = repository.create(NewActivity("u", "Ride", 0))

        assertEquals(emptyList(), LoadRideChartUseCase(repository)(id))
    }

    @Test
    fun `analysis shows only sections with data, in page order, numbers from the totals`() = runTest {
        val id = rideWithSpeedElevationPowerCadence()
        val totals =
            RideTotals(avgSpeedMps = 5.5, maxSpeedMps = 9.0, ascentM = 12.0, descentM = 7.0, normalizedPowerW = 230)

        val analysis = analysisUseCase()(id, totals)

        // No heart rate: that section is hidden.
        assertEquals(
            listOf(
                AnalysisSectionKind.SPEED,
                AnalysisSectionKind.ELEVATION,
                AnalysisSectionKind.POWER,
                AnalysisSectionKind.CADENCE,
            ),
            analysis.sections.map { it.kind },
        )
        val stats = analysis.sections.associate { section ->
            section.kind to
                section.stats.associate { it.kind to it.value }
        }
        assertEquals(mapOf(AnalysisStatKind.AVG to 5.5, AnalysisStatKind.MAX to 9.0), stats[AnalysisSectionKind.SPEED])
        // Elevation (T11.4): gain and highest point first.
        assertEquals(
            listOf(
                AnalysisStat(AnalysisStatKind.ASCENT, 12.0),
                AnalysisStat(AnalysisStatKind.MAX, 106.0),
                AnalysisStat(AnalysisStatKind.DESCENT, 7.0),
                AnalysisStat(AnalysisStatKind.MIN, 101.0),
            ),
            analysis.sections.single { it.kind == AnalysisSectionKind.ELEVATION }.stats,
        )
        // Without measured power in the totals, from the chart; NP from the totals.
        assertEquals(
            mapOf(AnalysisStatKind.AVG to 200.0, AnalysisStatKind.NORMALIZED to 230.0, AnalysisStatKind.MAX to 200.0),
            stats[AnalysisSectionKind.POWER],
        )
        // Cadence averages only while pedaling.
        assertEquals(90.0, stats.getValue(AnalysisSectionKind.CADENCE).getValue(AnalysisStatKind.AVG))
        assertEquals(6, analysis.points.size)
    }

    private suspend fun rideWithSpeedElevationPowerCadence(): Long {
        val id = repository.create(NewActivity("u", "Ride", 0))
        repository.appendSamples(
            id,
            (1..6).map {
                RideSample(
                    timeMs = it * 1_000L,
                    distanceM = it * 10.0,
                    speedMps = if (it == 3) 0.0 else 5.0,
                    altitudeM = 100.0 + it,
                    powerW = 200,
                    cadenceRpm = if (it == 3) 0 else 90,
                )
            },
        )
        return id
    }

    @Test
    fun `analysis without totals takes speed from the moving chart values`() = runTest {
        val id = repository.create(NewActivity("u", "Ride", 0))
        repository.appendSamples(
            id,
            listOf(0.0, 4.0, 8.0).mapIndexed { i, speed ->
                RideSample((i + 1) * 1_000L, distanceM = i * 5.0, speedMps = speed)
            },
        )

        val speed = analysisUseCase()(id, totals = null).sections.single()

        assertEquals(
            listOf(AnalysisStat(AnalysisStatKind.AVG, 6.0), AnalysisStat(AnalysisStatKind.MAX, 8.0)),
            speed.stats,
        )
    }

    @Test
    fun `speed analysis lists average, filtered max, moving and total time`() = runTest {
        val id = rideWithSpeedElevationPowerCadence()
        val totals = RideTotals(movingS = 3_000, elapsedS = 3_600, avgSpeedMps = 6.0, maxSpeedMps = 12.5)

        val speed = analysisUseCase()(id, totals)
            .sections.first { it.kind == AnalysisSectionKind.SPEED }

        assertEquals(
            listOf(
                AnalysisStat(AnalysisStatKind.AVG, 6.0),
                AnalysisStat(AnalysisStatKind.MAX, 12.5),
                AnalysisStat(AnalysisStatKind.MOVING_TIME, 3_000.0),
                AnalysisStat(AnalysisStatKind.ELAPSED_TIME, 3_600.0),
            ),
            speed.stats,
        )
        assertTrue(AnalysisStatKind.MOVING_TIME.isDuration && !AnalysisStatKind.MAX.isDuration)
    }

    @Test
    fun `heart rate zones count every sample against the profile hr max`() = runTest {
        profiles.save(RiderProfile(hrMaxBpm = 200))
        val id = repository.create(NewActivity("u", "Ride", 0))
        // 60 s at 110 bpm (Z1 < 120), 30 s at 150 bpm (Z3 140–159), 10 s at 185 bpm (Z5 ≥ 180).
        val bpm = List(60) { 110 } + List(30) { 150 } + List(10) { 185 }
        repository.appendSamples(
            id,
            bpm.mapIndexed { i, hr -> RideSample((i + 1) * 1_000L, distanceM = i * 5.0, heartRateBpm = hr) },
        )

        val zones = assertNotNull(analysisUseCase()(id, totals = null).heartRateZones)

        assertEquals(200, zones.hrMaxBpm)
        assertEquals(HrMaxSource.PROFILE, zones.source)
        assertEquals(listOf(60L, 0L, 30L, 0L, 10L), zones.zones.map { it.seconds })
        assertEquals(100L, zones.totalSeconds)
        assertEquals(
            listOf(0 to 119, 120 to 139, 140 to 159, 160 to 179, 180 to null),
            zones.zones.map { it.low to it.high },
        )
    }

    @Test
    fun `without hr max the zones use the age, without either there are none`() = runTest {
        val id = repository.create(NewActivity("u", "Ride", 0))
        repository.appendSamples(id, (1..5).map { RideSample(it * 1_000L, distanceM = it * 5.0, heartRateBpm = 140) })
        val year = Instant.ofEpochMilli(FakeClock.DEFAULT_START_MS).atZone(ZoneOffset.UTC).year

        assertNull(analysisUseCase()(id, totals = null).heartRateZones)

        profiles.save(RiderProfile(birthYear = year - 30))
        val zones = assertNotNull(analysisUseCase()(id, totals = null).heartRateZones)
        assertEquals(190, zones.hrMaxBpm)
        assertEquals(HrMaxSource.AGE, zones.source)
    }

    @Test
    fun `a ride without heart rate has no zones`() = runTest {
        profiles.save(RiderProfile(hrMaxBpm = 190))
        val id = rideWithSpeedElevationPowerCadence()

        assertNull(analysisUseCase()(id, totals = null).heartRateZones)
    }

    @Test
    fun `ftp comes from the profile, else 95 percent of a recent 20 minute best, else weight by level`() = runTest {
        profiles.save(RiderProfile(weightKg = 70.0))
        // No power-meter rides: 70 kg × 2.5 W/kg (intermediate, the default level).
        assertEquals(FtpEstimate(175, FtpSource.WEIGHT, 70.0), estimateFtp())
        guide.setLevel(RiderLevel.BEGINNER)
        assertEquals(140, estimateFtp().ftpW)

        val recent = repository.create(NewActivity("r", "Recent", FakeClock.DEFAULT_START_MS - 1_000))
        val old = repository.create(NewActivity("o", "Old", FakeClock.DEFAULT_START_MS - FtpConfig.BEST_WINDOW_MS - 1))
        efforts.replace(
            old,
            ActivityKind.ROAD,
            listOf(RideEffort(BestEffort(BestEffortKey.POWER_20_MIN, 400.0, 0, 1), true)),
        )
        efforts.replace(
            recent,
            ActivityKind.ROAD,
            listOf(RideEffort(BestEffort(BestEffortKey.POWER_20_MIN, 200.0, 0, 1), true)),
        )
        // Only the ride of the last 6 weeks counts: 95 % of 200 W.
        assertEquals(FtpEstimate(190, FtpSource.BEST_20_MIN, 70.0), estimateFtp())

        profiles.save(RiderProfile(weightKg = 70.0, ftpW = 250))
        assertEquals(FtpSource.PROFILE, estimateFtp().source)
    }

    @Test
    fun `power zones count moving seconds, from the meter or else the estimate`() = runTest {
        profiles.save(RiderProfile(ftpW = 200))
        // FTP 200: Z1 ≤ 110, Z2 ≤ 150, Z3 ≤ 180, Z4 ≤ 210, Z5 ≤ 240, Z6 ≤ 300, Z7 above.
        assertEquals(
            listOf(0 to 110, 111 to 150, 151 to 180, 181 to 210, 211 to 240, 241 to 300, 301 to null),
            PowerZoneBounds.of(200).map { it.low to it.high },
        )
        val estimated = repository.create(NewActivity("e", "No meter", 0))
        // 10 s estimated 100 W moving, 5 s estimated 250 W moving, 5 s stopped (not counted).
        repository.appendSamples(
            estimated,
            List(20) { i ->
                RideSample(
                    (i + 1) * 1_000L,
                    distanceM = i * 5.0,
                    speedMps = if (i >= 15) 0.0 else 5.0,
                    estPowerW = if (i < 10) 100 else 250,
                )
            },
        )

        val zones = assertNotNull(analysisUseCase()(estimated, totals = null).powerZones)

        assertTrue(zones.isEstimatedPower)
        assertEquals(listOf(10L, 0L, 0L, 0L, 0L, 5L, 0L), zones.zones.map { it.seconds })
    }

    @Test
    fun `a ride without power or estimate has no power zones`() = runTest {
        val id = repository.create(NewActivity("u", "Ride", 0))
        repository.appendSamples(id, (1..5).map { RideSample(it * 1_000L, distanceM = it * 5.0, speedMps = 5.0) })

        assertNull(analysisUseCase()(id, totals = null).powerZones)
    }
}

class ProfileUseCasesTest {
    private val repository = FakeProfileRepository()
    private val clock = FakeClock()
    private val save = SaveProfileUseCase(repository, clock)

    @Test
    fun `valid profile is saved`() = runTest {
        val result = save(RiderProfile(weightKg = 68.0, hrMaxBpm = 185, ftpW = 220, birthYear = 1999))

        assertEquals(Outcome.Success(Unit), result)
        assertTrue(HasProfileUseCase(repository)())
        assertEquals(68.0, ObserveProfileUseCase(repository)().first().weightKg)
    }

    @Test
    fun `every invalid field is reported at once and nothing is saved`() = runTest {
        val result = save(
            RiderProfile(
                weightKg = 5.0,
                bikeWeightKg = 500.0,
                wheelCircumferenceMm = 10,
                hrMaxBpm = 400,
                hrRestBpm = 300,
                ftpW = 5,
                birthYear = 3000,
            ),
        )

        assertEquals(Outcome.Failure(InvalidProfile(ProfileField.entries.toSet())), result)
        assertFalse(HasProfileUseCase(repository)())
    }

    @Test
    fun `non finite weight is rejected`() = runTest {
        val result = save(RiderProfile(weightKg = Double.NaN))

        assertEquals(Outcome.Failure(InvalidProfile(setOf(ProfileField.WEIGHT))), result)
    }

    @Test
    fun `hr max estimate is 220 minus age`() {
        // FakeClock starts on 2026-01-01.
        assertEquals(190, EstimateHrMaxUseCase(clock)(birthYear = 1996))
    }
}

class SettingsUseCasesTest {
    @Test
    fun `ride settings and onboarding round trip`() = runTest {
        val repository = FakeSettingsRepository()

        SaveRideSettingsUseCase(repository)(RideSettings(autoPauseDelayS = 10))
        CompleteOnboardingUseCase(repository)()

        assertEquals(10, ObserveRideSettingsUseCase(repository)().first().autoPauseDelayS)
        assertTrue(ObserveOnboardingDoneUseCase(repository)().first())
    }
}
