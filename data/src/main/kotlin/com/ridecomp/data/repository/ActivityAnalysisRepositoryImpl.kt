package com.ridecomp.data.repository

import com.ridecomp.core.repository.ActivityAnalysisRepository
import com.ridecomp.core.repository.PendingAnalysis
import com.ridecomp.data.db.dao.ActivityDao
import com.ridecomp.data.mapper.toDomain

/** Room implementation of [ActivityAnalysisRepository] (`activities.analysis_version`, D-67). */
class ActivityAnalysisRepositoryImpl(private val activityDao: ActivityDao) : ActivityAnalysisRepository {
    override suspend fun getPendingAnalysis(version: Int, limit: Int): List<PendingAnalysis> =
        activityDao.getPendingAnalysis(version, limit).mapNotNull { entity ->
            entity.toDomain()?.let { PendingAnalysis(it, entity.analysisVersion) }
        }

    override suspend fun markAnalyzed(activityId: Long, version: Int, expectedVersion: Int) {
        activityDao.setAnalysisVersion(activityId, version, expectedVersion)
    }

    override suspend fun resetAnalysis(toVersion: Int) {
        activityDao.lowerAnalysisVersion(toVersion)
    }
}
