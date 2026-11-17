package com.ridecomp.data.repository

import com.ridecomp.core.model.Activity
import com.ridecomp.core.model.ActivityStatus
import com.ridecomp.core.model.Lap
import com.ridecomp.core.model.PeriodTotals
import com.ridecomp.core.model.RideEvent
import com.ridecomp.core.model.RideSample
import com.ridecomp.core.model.RideTotals
import com.ridecomp.core.repository.ActivityRepository
import com.ridecomp.core.repository.NewActivity
import com.ridecomp.core.source.DispatcherProvider
import com.ridecomp.data.db.dao.ActivityDao
import com.ridecomp.data.db.dao.EventDao
import com.ridecomp.data.db.dao.LapDao
import com.ridecomp.data.db.dao.TrackPointDao
import com.ridecomp.data.db.entity.ActivityEntity
import com.ridecomp.data.mapper.toDb
import com.ridecomp.data.mapper.toDomain
import com.ridecomp.data.mapper.toEntity
import com.ridecomp.data.mapper.withTotals
import java.io.File
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/** Room implementation of [ActivityRepository] (ARCH-03). All work off the main thread (DATA-01). */
class ActivityRepositoryImpl(
    private val activityDao: ActivityDao,
    private val trackPointDao: TrackPointDao,
    private val eventDao: EventDao,
    private val lapDao: LapDao,
    private val dispatchers: DispatcherProvider,
) : ActivityRepository {
    override suspend fun create(activity: NewActivity): Long = activityDao.insert(
        ActivityEntity(
            uuid = activity.uuid,
            name = activity.name,
            status = ActivityStatus.RECORDING.toDb(),
            startedAt = activity.startedAtMs,
            lastCheckpointAt = activity.startedAtMs,
            profileId = activity.profileId,
            routeId = activity.routeId,
            bikeId = activity.bikeId,
        ),
    )

    override suspend fun appendSamples(activityId: Long, samples: List<RideSample>) {
        trackPointDao.insertAll(samples.map { it.toEntity(activityId) })
    }

    override suspend fun appendEvents(activityId: Long, events: List<RideEvent>) {
        eventDao.insertAll(events.map { it.toEntity(activityId) })
    }

    override suspend fun saveLap(activityId: Long, lap: Lap) {
        lapDao.upsert(lap.toEntity(activityId))
    }

    override suspend fun checkpoint(activityId: Long, status: ActivityStatus, totals: RideTotals, atMs: Long) {
        modify(activityId) { it.withTotals(totals).copy(status = status.toDb(), lastCheckpointAt = atMs) }
    }

    override suspend fun finish(activityId: Long, name: String, totals: RideTotals, endedAtMs: Long) {
        modify(activityId) {
            it.withTotals(totals).copy(
                status = ActivityStatus.FINISHED.toDb(),
                name = name,
                endedAt = endedAtMs,
                lastCheckpointAt = endedAtMs,
            )
        }
    }

    /** Deletes the row (children cascade) and the exported FIT file (SEC-STO-06). */
    override suspend fun delete(activityId: Long) {
        val fitPath = activityDao.getById(activityId)?.fitPath
        activityDao.deleteById(activityId)
        if (fitPath != null) withContext(dispatchers.io) { File(fitPath).delete() }
    }

    override suspend fun rename(activityId: Long, name: String) {
        modify(activityId) { it.copy(name = name) }
    }

    override suspend fun markFitExported(activityId: Long, fitPath: String, atMs: Long) {
        modify(activityId) { it.copy(fitPath = fitPath, fitExportedAt = atMs) }
    }

    override suspend fun get(activityId: Long): Activity? = activityDao.getById(activityId)?.toDomain()

    override fun observe(activityId: Long): Flow<Activity?> = activityDao.observeById(activityId).map { it?.toDomain() }

    override suspend fun findRecoverable(): Activity? = activityDao.getUnfinished().firstNotNullOfOrNull {
        it.toDomain()
    }

    override fun observeFinished(limit: Int, offset: Int): Flow<List<Activity>> =
        activityDao.observeFinishedPage(limit, offset).map { page -> page.mapNotNull { it.toDomain() } }

    override fun observeFinishedCount(): Flow<Int> = activityDao.observeFinishedCount()

    override fun observeTotalsSince(sinceMs: Long): Flow<PeriodTotals> =
        activityDao.observeTotalsSince(sinceMs).map { PeriodTotals(it.rides, it.distanceM, it.ascentM) }

    override suspend fun getLaps(activityId: Long): List<Lap> = lapDao.getForActivity(activityId).map { it.toDomain() }

    override suspend fun getEvents(activityId: Long): List<RideEvent> =
        eventDao.getForActivity(activityId).mapNotNull { it.toDomain() }

    override suspend fun getSamples(activityId: Long, afterTimeMs: Long, limit: Int): List<RideSample> =
        trackPointDao.getChunk(activityId, afterTimeMs, limit).map { it.toDomain() }

    override suspend fun getSampleCount(activityId: Long): Int = trackPointDao.count(activityId)

    private suspend fun modify(activityId: Long, change: (ActivityEntity) -> ActivityEntity) {
        activityDao.getById(activityId)?.let { activityDao.update(change(it)) }
    }
}
