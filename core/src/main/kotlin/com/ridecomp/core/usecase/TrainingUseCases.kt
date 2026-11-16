package com.ridecomp.core.usecase

import com.ridecomp.core.engine.track.RideTrack
import com.ridecomp.core.engine.training.DailyTraining
import com.ridecomp.core.engine.training.LoadInputs
import com.ridecomp.core.engine.training.LoadMethod
import com.ridecomp.core.engine.training.TrainingConfig
import com.ridecomp.core.engine.training.TrainingLoadCalculator
import com.ridecomp.core.engine.training.TrainingTrend
import com.ridecomp.core.engine.training.ZoneTime
import com.ridecomp.core.model.Activity
import com.ridecomp.core.repository.ProfileRepository
import com.ridecomp.core.repository.TrainingRepository
import com.ridecomp.core.repository.TrainingTotals
import com.ridecomp.core.source.Clock
import com.ridecomp.core.source.DispatcherProvider
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext

/** The rider's FTP, HR max (from age when empty), resting HR and sex for loads and zones. */
public class GetLoadInputsUseCase(private val profiles: ProfileRepository, private val clock: Clock) {
    public suspend operator fun invoke(): LoadInputs = LoadInputs.of(profiles.get(), clock.nowMs())
}

/** Local dates of the phone's time zone (daily training is per local day, 05-DATA-MODEL). */
public class TrainingCalendar(private val clock: Clock, private val zone: () -> ZoneId = ZoneId::systemDefault) {
    public fun dateOf(ms: Long): LocalDate = Instant.ofEpochMilli(ms).atZone(zone()).toLocalDate()

    public fun today(): LocalDate = dateOf(clock.nowMs())

    public fun startOfDayMs(date: LocalDate): Long = date.atStartOfDay(zone()).toInstant().toEpochMilli()

    /** Monday of the current week. */
    public fun weekStart(): LocalDate = today().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
}

/**
 * Recomputes the daily trend from the day of [fromMs] to today (12-FEATURE-SPECS §10.2, incremental). Days
 * are only stored when something is computed, so the start is the day after the latest stored day before
 * [fromMs]: the days in between are filled and fitness/fatigue decay instead of restarting from zero.
 */
public class RecomputeTrainingUseCase(
    private val training: TrainingRepository,
    private val calendar: TrainingCalendar,
    private val dispatchers: DispatcherProvider,
) {
    public suspend operator fun invoke(fromMs: Long) {
        val changed = calendar.dateOf(fromMs)
        val dayBefore = training.latestDayBefore(changed)
        val from = dayBefore?.date?.plusDays(1)?.takeIf { it < changed } ?: changed
        val loads = training.loadsSince(calendar.startOfDayMs(from))
        if (dayBefore == null && loads.isEmpty()) {
            // No load left from here on (e.g. the only ride was deleted): clear what was stored.
            training.replaceFrom(from, emptyList())
            return
        }
        val days = withContext(dispatchers.default) {
            val byDate = loads.groupBy { calendar.dateOf(it.startedAtMs) }.mapValues { (_, l) -> l.sumOf { it.load } }
            TrainingTrend.compute(from, maxOf(from, calendar.today()), dayBefore, byDate)
        }
        training.replaceFrom(from, days)
    }
}

/** Extends the trend to today (fitness and fatigue decay on days without rides); on opening the Training tab. */
public class ExtendTrainingToTodayUseCase(
    private val recompute: RecomputeTrainingUseCase,
    private val calendar: TrainingCalendar,
) {
    public suspend operator fun invoke() {
        recompute(calendar.startOfDayMs(calendar.today()))
    }
}

/**
 * Analysis step 3 (T4.4): the ride's load; the trend is recomputed once per batch from the earliest changed
 * day (not once per ride: the first run over many old rides would rewrite the table again and again).
 */
public class TrainingLoadStep(
    private val training: TrainingRepository,
    private val profiles: ProfileRepository,
    private val recompute: RecomputeTrainingUseCase,
    private val clock: Clock,
    private val dispatchers: DispatcherProvider,
) : AnalysisStep {
    override val version: Int = AnalysisConfig.TRAINING_VERSION

    override suspend fun analyze(activity: Activity, track: RideTrack) {
        val inputs = LoadInputs.of(profiles.get(), clock.nowMs())
        val load = withContext(dispatchers.default) { TrainingLoadCalculator.calculate(track, activity.totals, inputs) }
        training.setRideLoad(activity.id, load)
        earliestChangedMs = minOf(earliestChangedMs ?: activity.startedAtMs, activity.startedAtMs)
    }

    override suspend fun afterBatch() {
        val from = earliestChangedMs ?: return
        earliestChangedMs = null
        recompute(from)
    }

    /** Only touched under the analysis mutex. */
    private var earliestChangedMs: Long? = null
}

/** Everything the Training tab shows except zones (13-UI-SCREENS #20). */
public data class TrainingOverview(
    /** Oldest first, [TrainingConfig.TREND_WEEKS] weeks up to today. */
    val days: List<DailyTraining>,
    val week: TrainingTotals,
    val methods: List<LoadMethod>,
) {
    val today: DailyTraining? get() = days.lastOrNull()

    /** Fitness change over [TrainingConfig.FITNESS_CHANGE_WEEKS] weeks. */
    val fitnessChange: Double?
        get() {
            val back = days.getOrNull(
                days.size - 1 - (TrainingConfig.FITNESS_CHANGE_WEEKS * TrainingConfig.DAYS_PER_WEEK).toInt(),
            )
            return today?.let { now -> back?.let { now.ctl - it.ctl } }
        }
}

public class ObserveTrainingUseCase(private val training: TrainingRepository, private val calendar: TrainingCalendar) {
    public operator fun invoke(): Flow<TrainingOverview> {
        val trendStart = calendar.today().minusWeeks(TrainingConfig.TREND_WEEKS)
        val weekStartMs = calendar.startOfDayMs(calendar.weekStart())
        return combine(
            training.observeDays(trendStart),
            training.observeTotalsSince(weekStartMs),
            training.observeMethodsSince(calendar.startOfDayMs(trendStart)),
        ) { days, week, methods -> TrainingOverview(days, week, methods) }
    }
}

/** Seconds per heart-rate zone of this week's rides (12-FEATURE-SPECS §10.2); empty without HR. */
public class LoadWeekZonesUseCase(
    private val training: TrainingRepository,
    private val readTrack: ReadRideTrackUseCase,
    private val profiles: ProfileRepository,
    private val calendar: TrainingCalendar,
    private val clock: Clock,
    private val dispatchers: DispatcherProvider,
) {
    public suspend operator fun invoke(): List<Long> {
        val hrMax = LoadInputs.of(profiles.get(), clock.nowMs()).hrMaxBpm ?: return emptyList()
        val rides = training.rideIdsSince(calendar.startOfDayMs(calendar.weekStart()))
        return rides.map { id ->
            readTrack(id).let { withContext(dispatchers.default) { ZoneTime.heartRate(it, hrMax) } }
        }
            .filter { it.isNotEmpty() }
            .reduceOrNull { total, ride -> total.zip(ride) { a, b -> a + b } }
            .orEmpty()
    }
}
