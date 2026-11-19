package com.ridecomp.core.testing

import com.ridecomp.core.engine.efforts.BestEffortKey
import com.ridecomp.core.model.ActivityKind
import com.ridecomp.core.repository.ActivityRepository
import com.ridecomp.core.repository.BestEffortRepository
import com.ridecomp.core.repository.PersonalRecord
import com.ridecomp.core.repository.RankedEffort
import com.ridecomp.core.repository.RideEffort
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** In-memory [BestEffortRepository]; ride dates and names come from [activities]. */
public class FakeBestEffortRepository(private val activities: ActivityRepository) : BestEffortRepository {
    /** Stored efforts per ride, with the ride's kind. */
    public val stored: MutableStateFlow<Map<Long, Pair<ActivityKind, List<RideEffort>>>> = MutableStateFlow(emptyMap())

    override suspend fun bestsBefore(kind: ActivityKind, beforeStartedAtMs: Long): Map<BestEffortKey, Double> {
        val earlier = stored.value.filter { (id, entry) ->
            entry.first == kind && (activities.get(id)?.startedAtMs ?: Long.MAX_VALUE) < beforeStartedAtMs
        }
        return earlier.values.flatMap { it.second }.map { it.effort }
            .groupBy { it.key }
            .mapValues { (key, efforts) ->
                efforts.map { it.value }.reduce { best, value -> if (key.isBetter(value, best)) value else best }
            }
    }

    override suspend fun replace(activityId: Long, kind: ActivityKind, efforts: List<RideEffort>) {
        stored.value = stored.value + (activityId to (kind to efforts))
    }

    override fun observeRecords(kind: ActivityKind): Flow<List<PersonalRecord>> = stored.map { all ->
        all.filterValues { it.first == kind }
            .flatMap { (id, entry) -> entry.second.map { id to it.effort } }
            .groupBy { it.second.key }
            .map { (key, efforts) ->
                val (id, best) = efforts.reduce { a, b -> if (key.isBetter(b.second.value, a.second.value)) b else a }
                val activity = activities.get(id)
                PersonalRecord(key, best.value, id, activity?.name.orEmpty(), activity?.startedAtMs ?: 0L)
            }
    }

    override fun observeKinds(): Flow<List<ActivityKind>> =
        stored.map { all -> all.values.map { it.first }.distinct().sorted() }

    override suspend fun countRecords(activityId: Long): Int = stored.value[activityId]?.second?.count { it.isPr } ?: 0

    override suspend fun bestSince(key: BestEffortKey, sinceStartedAtMs: Long): Double? =
        stored.value.filter { (id, _) -> (activities.get(id)?.startedAtMs ?: Long.MIN_VALUE) >= sinceStartedAtMs }
            .values.flatMap { it.second }.map { it.effort }
            .filter { it.key == key }
            .maxOfOrNull { it.value }

    override fun observeRanked(activityId: Long): Flow<List<RankedEffort>> = stored.map { all ->
        val (kind, efforts) = all[activityId] ?: return@map emptyList()
        val startedAt = startedAt(activityId)
        val earlier = all.filter { (id, entry) -> entry.first == kind && startedAt(id) < startedAt }
            .values.flatMap { it.second }.map { it.effort }
        efforts.map { (effort) ->
            // As good or better: a tie with an earlier ride is not a record (rank 1 = is_pr).
            val ahead = earlier.count { it.key == effort.key && !effort.key.isBetter(effort.value, it.value) }
            RankedEffort(effort, 1 + ahead)
        }
    }

    private suspend fun startedAt(id: Long): Long = activities.get(id)?.startedAtMs ?: Long.MAX_VALUE
}
