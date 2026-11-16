package com.ridecomp.data.repository

import com.ridecomp.core.model.StravaUpload
import com.ridecomp.core.model.StravaUploadState
import com.ridecomp.core.repository.StravaUploadRepository
import com.ridecomp.data.db.dao.StravaUploadDao
import com.ridecomp.data.mapper.StravaUploadMapper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class StravaUploadRepositoryImpl(private val dao: StravaUploadDao) : StravaUploadRepository {
    override fun observe(activityId: Long): Flow<StravaUpload?> =
        dao.observe(activityId).map { entity -> entity?.let(StravaUploadMapper::toDomain) }.distinctUntilChanged()

    override suspend fun get(activityId: Long): StravaUpload? = dao.get(activityId)?.let(StravaUploadMapper::toDomain)

    override suspend fun save(upload: StravaUpload) {
        dao.upsert(StravaUploadMapper.toEntity(upload))
    }

    override fun observeOnStrava(): Flow<Set<Long>> = dao
        .observeIdsInStates(StravaUploadState.entries.filter { it.isOnStrava }.map(StravaUploadMapper::storedName))
        .map { it.toSet() }
        .distinctUntilChanged()

    override suspend fun getActive(): List<StravaUpload> = dao
        .getInStates(StravaUploadState.entries.filter { it.isActive }.map(StravaUploadMapper::storedName))
        .map(StravaUploadMapper::toDomain)
}
