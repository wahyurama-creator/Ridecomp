package com.ridecomp.data.repository

import com.ridecomp.core.engine.map.MapBounds
import com.ridecomp.core.engine.segments.Segment
import com.ridecomp.core.engine.segments.SegmentMatch
import com.ridecomp.core.engine.segments.SegmentPoint
import com.ridecomp.core.engine.segments.SegmentSource
import com.ridecomp.core.repository.RankedSegmentEffort
import com.ridecomp.core.repository.SegmentEffort
import com.ridecomp.core.repository.SegmentEffortView
import com.ridecomp.core.repository.SegmentRepository
import com.ridecomp.core.repository.SegmentSummary
import com.ridecomp.data.db.dao.SegmentDao
import com.ridecomp.data.db.entity.SegmentEffortEntity
import com.ridecomp.data.db.entity.SegmentEntity
import com.ridecomp.data.db.entity.SegmentPointEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Room implementation of [SegmentRepository]. Lists carry no points (the summary only needs the numbers);
 * matching and the detail load them.
 */
class SegmentRepositoryImpl(private val dao: SegmentDao) : SegmentRepository {
    override suspend fun create(segment: Segment): Long = dao.insertWithPoints(segment.toEntity()) { id ->
        segment.points.mapIndexed { seq, point ->
            SegmentPointEntity(id, seq, point.latDeg, point.lonDeg, point.cumDistanceM)
        }
    }

    override suspend fun delete(id: Long) {
        dao.delete(id)
    }

    override fun observeSummaries(): Flow<List<SegmentSummary>> = dao.observeSummaries().map { rows ->
        rows.map { SegmentSummary(it.segment.toDomain(emptyList()), it.bestElapsedS, it.effortCount) }
    }

    override fun observe(id: Long): Flow<Segment?> = dao.observe(id).map { entity ->
        entity?.let { it.toDomain(dao.getPoints(it.id).map { point -> point.toDomain() }) }
    }

    override fun observeEfforts(segmentId: Long): Flow<List<SegmentEffortView>> =
        dao.observeEfforts(segmentId).map { rows ->
            rows.map { SegmentEffortView(it.effort.toDomain(), it.activityName, it.startedAt) }
        }

    override suspend fun getAll(): List<Segment> = dao.getAll().map { entity ->
        entity.toDomain(dao.getPoints(entity.id).map { it.toDomain() })
    }

    override suspend fun bestBefore(segmentId: Long, beforeStartedAtMs: Long): Double? =
        dao.bestBefore(segmentId, beforeStartedAtMs)

    override suspend fun countRecords(activityId: Long): Int = dao.countRecords(activityId)

    override suspend fun replaceForActivity(activityId: Long, efforts: List<SegmentEffort>) {
        dao.replaceEffortsForActivity(
            activityId,
            efforts.map { effort ->
                SegmentEffortEntity(
                    segmentId = effort.segmentId,
                    activityId = activityId,
                    startT = effort.match.startMs,
                    endT = effort.match.endMs,
                    elapsedS = effort.match.elapsedS,
                    avgSpeedMps = effort.match.avgSpeedMps,
                    avgHr = effort.match.avgHeartRateBpm,
                    avgPower = effort.match.avgPowerW,
                    isPr = effort.isPr,
                )
            },
        )
    }

    override fun observeRanked(activityId: Long): Flow<List<RankedSegmentEffort>> =
        dao.observeRanked(activityId).map { rows ->
            rows.map { row ->
                val match = row.effort.toDomain().match
                RankedSegmentEffort(row.effort.segmentId, row.segmentName, row.lengthM, match, row.rank)
            }
        }

    private fun Segment.toEntity() = SegmentEntity(
        name = name,
        lengthM = lengthM,
        gainM = gainM,
        avgGrade = avgGradePercent,
        startLat = start.latDeg,
        startLon = start.lonDeg,
        endLat = end.latDeg,
        endLon = end.lonDeg,
        minLat = bounds.minLat,
        minLon = bounds.minLon,
        maxLat = bounds.maxLat,
        maxLon = bounds.maxLon,
        createdFrom = source.storageValue,
        createdAt = createdAtMs,
    )

    private fun SegmentEntity.toDomain(points: List<SegmentPoint>) = Segment(
        id = id,
        name = name,
        lengthM = lengthM,
        gainM = gainM,
        avgGradePercent = avgGrade,
        // Lists load no points; the stored endpoints keep start/end usable.
        points = points.ifEmpty {
            listOf(SegmentPoint(startLat, startLon, 0.0), SegmentPoint(endLat, endLon, lengthM))
        },
        bounds = MapBounds(minLon = minLon, minLat = minLat, maxLon = maxLon, maxLat = maxLat),
        source = SegmentSource.fromStorage(createdFrom),
        createdAtMs = createdAt,
    )

    private fun SegmentPointEntity.toDomain() = SegmentPoint(lat, lon, cumDistM)

    private fun SegmentEffortEntity.toDomain() = SegmentEffort(
        segmentId = segmentId,
        activityId = activityId,
        match = SegmentMatch(startT, endT, elapsedS, avgSpeedMps, avgHr, avgPower),
        isPr = isPr,
    )
}
