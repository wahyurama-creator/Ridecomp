package com.ridecomp.data.repository

import com.ridecomp.core.model.IncidentOutcome
import com.ridecomp.core.model.IncidentRecord
import com.ridecomp.core.repository.IncidentRepository
import com.ridecomp.data.db.dao.IncidentDao
import com.ridecomp.data.db.entity.IncidentEntity
import com.ridecomp.data.mapper.toDbValue
import com.ridecomp.data.mapper.toEnumOrNull

/** Room implementation of [IncidentRepository]. */
class IncidentRepositoryImpl(private val dao: IncidentDao) : IncidentRepository {
    override suspend fun save(incident: IncidentRecord): Long {
        val entity = incident.toEntity()
        return if (incident.id == 0L) {
            dao.insert(entity)
        } else {
            dao.upsert(entity)
            incident.id
        }
    }

    override suspend fun getForActivity(activityId: Long): List<IncidentRecord> =
        dao.getForActivity(activityId).map { it.toDomain() }

    private fun IncidentRecord.toEntity() = IncidentEntity(
        id = id,
        activityId = activityId,
        detectedAt = detectedAtMs,
        peakG = peakG,
        speedBeforeMps = speedBeforeMps,
        outcome = outcome.toDbValue(),
        smsSentCount = smsSentCount,
        lastStatus = null,
    )

    private fun IncidentEntity.toDomain() = IncidentRecord(
        id = id,
        activityId = activityId,
        detectedAtMs = detectedAt,
        peakG = peakG,
        speedBeforeMps = speedBeforeMps,
        // Unknown values from a newer version read as FAILED: never shown as "sent" by mistake.
        outcome = outcome.toEnumOrNull<IncidentOutcome>() ?: IncidentOutcome.FAILED,
        smsSentCount = smsSentCount,
    )
}
