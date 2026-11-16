package com.ridecomp.data.repository

import com.ridecomp.core.engine.training.DailyTraining
import com.ridecomp.core.engine.training.LoadMethod
import com.ridecomp.core.engine.training.RideLoad
import com.ridecomp.core.repository.DatedLoad
import com.ridecomp.core.repository.TrainingRepository
import com.ridecomp.core.repository.TrainingTotals
import com.ridecomp.data.db.dao.TrainingDao
import com.ridecomp.data.db.entity.DailyTrainingEntity
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Room implementation of [TrainingRepository]; days are stored as ISO `yyyy-MM-dd` local dates. */
class TrainingRepositoryImpl(private val dao: TrainingDao) : TrainingRepository {
    override suspend fun setRideLoad(activityId: Long, load: RideLoad?) {
        dao.setRideLoad(activityId, load?.load, load?.method?.storageValue, load?.intensityFactor)
    }

    override suspend fun loadsSince(sinceMs: Long): List<DatedLoad> =
        dao.loadsSince(sinceMs).map { DatedLoad(it.startedAt, it.load) }

    override suspend fun latestDayBefore(date: LocalDate): DailyTraining? =
        dao.latestDayBefore(date.toString())?.toDomain()

    override suspend fun replaceFrom(from: LocalDate, days: List<DailyTraining>) {
        dao.replaceFrom(
            from.toString(),
            days.map {
                DailyTrainingEntity(it.date.toString(), it.load, it.ctl, it.atl, it.tsb)
            },
        )
    }

    override fun observeDays(from: LocalDate): Flow<List<DailyTraining>> =
        dao.observeDays(from.toString()).map { rows -> rows.mapNotNull { it.toDomain() } }

    override fun observeTotalsSince(sinceMs: Long): Flow<TrainingTotals> =
        dao.observeTotalsSince(sinceMs).map { TrainingTotals(it.rides, it.distanceM, it.movingS, it.load) }

    override fun observeMethodsSince(sinceMs: Long): Flow<List<LoadMethod>> =
        dao.observeMethodsSince(sinceMs).map { methods -> methods.mapNotNull(LoadMethod::fromStorage) }

    override suspend fun rideIdsSince(sinceMs: Long): List<Long> = dao.rideIdsSince(sinceMs)

    /** An unreadable date (never written by this app) is skipped rather than crashing. */
    private fun DailyTrainingEntity.toDomain(): DailyTraining? = runCatching { LocalDate.parse(date) }.getOrNull()
        ?.let { DailyTraining(it, load, ctl, atl, tsb) }
}
