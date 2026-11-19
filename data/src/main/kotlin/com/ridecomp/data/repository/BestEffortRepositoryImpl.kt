package com.ridecomp.data.repository

import com.ridecomp.core.engine.efforts.BestEffort
import com.ridecomp.core.engine.efforts.BestEffortKey
import com.ridecomp.core.engine.efforts.BestEffortType
import com.ridecomp.core.model.ActivityKind
import com.ridecomp.core.repository.BestEffortRepository
import com.ridecomp.core.repository.PersonalRecord
import com.ridecomp.core.repository.RankedEffort
import com.ridecomp.core.repository.RideEffort
import com.ridecomp.data.db.dao.BestEffortDao
import com.ridecomp.data.db.entity.BestEffortEntity
import com.ridecomp.data.mapper.toDbValue
import com.ridecomp.data.mapper.toEnumOrNull
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Room implementation of [BestEffortRepository]; unknown stored keys/kinds are ignored, never crash. */
class BestEffortRepositoryImpl(private val dao: BestEffortDao) : BestEffortRepository {
    override suspend fun bestsBefore(kind: ActivityKind, beforeStartedAtMs: Long): Map<BestEffortKey, Double> =
        dao.rangesBefore(kind.toDbValue(), beforeStartedAtMs).mapNotNull { row ->
            val key = BestEffortKey.fromStorage(row.key) ?: return@mapNotNull null
            key to if (key.isHigherBetter) row.maxValue else row.minValue
        }.toMap()

    override suspend fun replace(activityId: Long, kind: ActivityKind, efforts: List<RideEffort>) {
        dao.replace(
            activityId,
            efforts.map { (effort, isPr) ->
                BestEffortEntity(
                    activityId = activityId,
                    profileKind = kind.toDbValue(),
                    type = effort.key.type.storageValue,
                    key = effort.key.storageKey,
                    value = effort.value,
                    startT = effort.startMs,
                    endT = effort.endMs,
                    isPr = isPr,
                )
            },
        )
    }

    override fun observeRecords(kind: ActivityKind): Flow<List<PersonalRecord>> =
        dao.observeRecords(kind.toDbValue(), BestEffortType.DISTANCE.storageValue).map { rows ->
            // Rows come oldest first: on a tie the first ride to reach the value keeps the record.
            rows.mapNotNull { row ->
                BestEffortKey.fromStorage(row.key)?.let {
                    PersonalRecord(it, row.value, row.activityId, row.activityName, row.startedAt)
                }
            }.distinctBy { it.key }.sortedBy { it.key.ordinal }
        }

    override fun observeKinds(): Flow<List<ActivityKind>> = dao.observeKinds().map { kinds ->
        kinds.mapNotNull { it.toEnumOrNull<ActivityKind>() }.sorted()
    }

    override suspend fun countRecords(activityId: Long): Int = dao.countRecords(activityId)

    override suspend fun bestSince(key: BestEffortKey, sinceStartedAtMs: Long): Double? {
        // MAX only fits keys where higher is better (power); the FTP estimate asks for those only.
        require(key.isHigherBetter) { "bestSince needs a higher-is-better key" }
        return dao.maxSince(key.storageKey, sinceStartedAtMs)
    }

    override fun observeRanked(activityId: Long): Flow<List<RankedEffort>> =
        dao.observeRanked(activityId, BestEffortType.DISTANCE.storageValue).map { rows ->
            rows.mapNotNull { row ->
                BestEffortKey.fromStorage(row.key)?.let {
                    RankedEffort(BestEffort(it, row.value, row.startT, row.endT), row.rank)
                }
            }
        }
}
