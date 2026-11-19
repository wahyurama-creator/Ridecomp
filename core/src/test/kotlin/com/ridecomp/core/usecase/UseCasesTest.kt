package com.ridecomp.core.usecase

import com.ridecomp.core.model.Activity
import com.ridecomp.core.model.ActivityStatus
import com.ridecomp.core.model.RideSample
import com.ridecomp.core.model.RideSettings
import com.ridecomp.core.model.RideTotals
import com.ridecomp.core.model.RiderProfile
import com.ridecomp.core.repository.NewActivity
import com.ridecomp.core.testing.FakeActivityRepository
import com.ridecomp.core.testing.FakeClock
import com.ridecomp.core.testing.FakeProfileRepository
import com.ridecomp.core.testing.FakeSettingsRepository
import com.ridecomp.core.testing.FakeTrainingRepository
import com.ridecomp.core.testing.TestDispatcherProvider
import com.ridecomp.core.util.Outcome
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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

        val analysis = LoadActivityAnalysisUseCase(LoadRideChartUseCase(repository))(id, totals)

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

        val speed = LoadActivityAnalysisUseCase(LoadRideChartUseCase(repository))(id, totals = null).sections.single()

        assertEquals(
            listOf(AnalysisStat(AnalysisStatKind.AVG, 6.0), AnalysisStat(AnalysisStatKind.MAX, 8.0)),
            speed.stats,
        )
    }

    @Test
    fun `speed analysis lists average, filtered max, moving and total time`() = runTest {
        val id = rideWithSpeedElevationPowerCadence()
        val totals = RideTotals(movingS = 3_000, elapsedS = 3_600, avgSpeedMps = 6.0, maxSpeedMps = 12.5)

        val speed = LoadActivityAnalysisUseCase(LoadRideChartUseCase(repository))(id, totals)
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
