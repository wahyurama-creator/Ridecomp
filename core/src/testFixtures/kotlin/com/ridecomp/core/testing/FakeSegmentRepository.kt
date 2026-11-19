package com.ridecomp.core.testing

import com.ridecomp.core.engine.segments.Segment
import com.ridecomp.core.repository.ActivityRepository
import com.ridecomp.core.repository.RankedSegmentEffort
import com.ridecomp.core.repository.SegmentEffort
import com.ridecomp.core.repository.SegmentEffortView
import com.ridecomp.core.repository.SegmentRepository
import com.ridecomp.core.repository.SegmentSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/** In-memory [SegmentRepository]; ride dates and names come from [activities]. */
public class FakeSegmentRepository(private val activities: ActivityRepository) : SegmentRepository {
    public val segments: MutableStateFlow<Map<Long, Segment>> = MutableStateFlow(emptyMap())
    public val efforts: MutableStateFlow<List<SegmentEffort>> = MutableStateFlow(emptyList())
    private var nextId = 1L

    override suspend fun create(segment: Segment): Long {
        val id = nextId++
        segments.value = segments.value + (id to segment.copy(id = id))
        return id
    }

    override suspend fun delete(id: Long) {
        segments.value = segments.value - id
        efforts.value = efforts.value.filterNot { it.segmentId == id }
    }

    override fun observeSummaries(): Flow<List<SegmentSummary>> = combine(segments, efforts) { all, efforts ->
        all.values.map { segment ->
            val own = efforts.filter { it.segmentId == segment.id }
            SegmentSummary(segment, own.minOfOrNull { it.match.elapsedS }, own.size)
        }
    }

    override fun observe(id: Long): Flow<Segment?> = segments.map { it[id] }

    override fun observeEfforts(segmentId: Long): Flow<List<SegmentEffortView>> = efforts.map { all ->
        all.filter { it.segmentId == segmentId }.sortedBy { it.match.elapsedS }.map { effort ->
            val activity = activities.get(effort.activityId)
            SegmentEffortView(effort, activity?.name.orEmpty(), activity?.startedAtMs ?: 0L)
        }
    }

    override suspend fun getAll(): List<Segment> = segments.value.values.toList()

    override suspend fun bestBefore(segmentId: Long, beforeStartedAtMs: Long): Double? = efforts.value
        .filter { it.segmentId == segmentId }
        .filter { (activities.get(it.activityId)?.startedAtMs ?: Long.MAX_VALUE) < beforeStartedAtMs }
        .minOfOrNull { it.match.elapsedS }

    override suspend fun replaceForActivity(activityId: Long, efforts: List<SegmentEffort>) {
        this.efforts.value = this.efforts.value.filterNot { it.activityId == activityId } + efforts
    }

    override suspend fun countRecords(activityId: Long): Int = efforts.value.count {
        it.activityId == activityId &&
            it.isPr
    }

    override fun observeRanked(activityId: Long): Flow<List<RankedSegmentEffort>> =
        combine(segments, efforts) { all, efforts ->
            val startedAt = startedAt(activityId)
            efforts.filter { it.activityId == activityId }.sortedBy { it.match.startMs }.mapNotNull { effort ->
                val segment = all[effort.segmentId] ?: return@mapNotNull null
                val ahead = efforts.count {
                    it.segmentId == effort.segmentId &&
                        startedAt(it.activityId) < startedAt &&
                        it.match.elapsedS <= effort.match.elapsedS
                }
                RankedSegmentEffort(segment.id, segment.name, segment.lengthM, effort.match, 1 + ahead)
            }
        }

    private suspend fun startedAt(id: Long): Long = activities.get(id)?.startedAtMs ?: Long.MAX_VALUE
}
