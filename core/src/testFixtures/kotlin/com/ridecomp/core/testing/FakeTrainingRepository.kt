package com.ridecomp.core.testing

import com.ridecomp.core.engine.training.DailyTraining
import com.ridecomp.core.engine.training.LoadMethod
import com.ridecomp.core.engine.training.RideLoad
import com.ridecomp.core.model.ActivityStatus
import com.ridecomp.core.repository.DatedLoad
import com.ridecomp.core.repository.TrainingRepository
import com.ridecomp.core.repository.TrainingTotals
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/** In-memory [TrainingRepository] over the rides of a [FakeActivityRepository]. */
public class FakeTrainingRepository(private val activities: FakeActivityRepository) : TrainingRepository {
    public val loads: MutableStateFlow<Map<Long, RideLoad>> = MutableStateFlow(emptyMap())
    public val days: MutableStateFlow<Map<LocalDate, DailyTraining>> = MutableStateFlow(emptyMap())

    override suspend fun setRideLoad(activityId: Long, load: RideLoad?) {
        loads.value = if (load == null) loads.value - activityId else loads.value + (activityId to load)
    }

    override suspend fun loadsSince(sinceMs: Long): List<DatedLoad> = finished()
        .filter { it.startedAtMs >= sinceMs }
        .mapNotNull { activity -> loads.value[activity.id]?.let { DatedLoad(activity.startedAtMs, it.load) } }

    override suspend fun latestDayBefore(date: LocalDate): DailyTraining? =
        days.value.values.filter { it.date < date }.maxByOrNull { it.date }

    override suspend fun replaceFrom(from: LocalDate, days: List<DailyTraining>) {
        this.days.value = this.days.value.filterKeys { it < from } + days.associateBy { it.date }
    }

    override fun observeDays(from: LocalDate): Flow<List<DailyTraining>> =
        days.map { all -> all.values.filter { it.date >= from }.sortedBy { it.date } }

    override fun observeTotalsSince(sinceMs: Long): Flow<TrainingTotals> = loads.map {
        val rides = finished().filter { it.startedAtMs >= sinceMs }
        TrainingTotals(
            rides = rides.size,
            distanceM = rides.sumOf { it.totals.distanceM },
            movingS = rides.sumOf { it.totals.movingS },
            load = rides.sumOf { loads.value[it.id]?.load ?: 0.0 },
        )
    }

    override fun observeMethodsSince(sinceMs: Long): Flow<List<LoadMethod>> = combine(loads, days) { all, _ ->
        finished().filter { it.startedAtMs >= sinceMs }.mapNotNull { all[it.id]?.method }.distinct()
    }

    override suspend fun rideIdsSince(sinceMs: Long): List<Long> =
        finished().filter { it.startedAtMs >= sinceMs }.map { it.id }

    private fun finished() = activities.all().filter { it.status == ActivityStatus.FINISHED }
}
