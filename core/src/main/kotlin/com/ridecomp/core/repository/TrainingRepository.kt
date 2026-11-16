package com.ridecomp.core.repository

import com.ridecomp.core.engine.training.DailyTraining
import com.ridecomp.core.engine.training.LoadMethod
import com.ridecomp.core.engine.training.RideLoad
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

/** A saved ride's load and when it started. */
public data class DatedLoad(val startedAtMs: Long, val load: Double)

/** Totals of saved rides in a period (Training tab "MINGGU INI"). */
public data class TrainingTotals(
    val rides: Int = 0,
    val distanceM: Double = 0.0,
    val movingS: Long = 0,
    val load: Double = 0.0,
)

/** Ride loads and the daily trend (05-DATA-MODEL `activities.training_load`, `daily_training`). In :data. */
public interface TrainingRepository {
    /** Stores (or clears) the load columns of a ride. */
    public suspend fun setRideLoad(activityId: Long, load: RideLoad?)

    /** Loads of saved rides started at or after [sinceMs]. */
    public suspend fun loadsSince(sinceMs: Long): List<DatedLoad>

    /** The latest stored day strictly before [date], the starting point of a recompute. */
    public suspend fun latestDayBefore(date: LocalDate): DailyTraining?

    /** Replaces every day from [from] on with [days] in one transaction. */
    public suspend fun replaceFrom(from: LocalDate, days: List<DailyTraining>)

    public fun observeDays(from: LocalDate): Flow<List<DailyTraining>>

    public fun observeTotalsSince(sinceMs: Long): Flow<TrainingTotals>

    /** Load methods used by rides since [sinceMs], so the trend says what it is made of. */
    public fun observeMethodsSince(sinceMs: Long): Flow<List<LoadMethod>>

    /** Saved rides started at or after [sinceMs] (zone time of the week). */
    public suspend fun rideIdsSince(sinceMs: Long): List<Long>
}
