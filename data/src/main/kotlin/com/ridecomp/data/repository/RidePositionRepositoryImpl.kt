package com.ridecomp.data.repository

import com.ridecomp.core.engine.map.TrailPoint
import com.ridecomp.core.repository.RidePositionRepository
import com.ridecomp.data.db.dao.TrackPointDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class RidePositionRepositoryImpl(private val trackPointDao: TrackPointDao) : RidePositionRepository {
    override fun observeLastPosition(): Flow<TrailPoint?> = trackPointDao.observeLastFinishedPosition()
        .map { row -> row?.let { TrailPoint(it.lat, it.lon) } }
        .distinctUntilChanged()
}
