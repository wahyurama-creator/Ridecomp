package com.ridecomp.data.repository

import com.ridecomp.core.model.Activity
import com.ridecomp.core.repository.ActivityAnalysisRepository
import com.ridecomp.data.db.dao.ActivityDao
import com.ridecomp.data.mapper.toDomain

/** Room implementation of [ActivityAnalysisRepository] (`activities.analysis_version`, D-67). */
class ActivityAnalysisRepositoryImpl(private val activityDao: ActivityDao) : ActivityAnalysisRepository {
    override suspend fun getPendingAnalysis(version: Int, limit: Int): List<Activity> =
        activityDao.getPendingAnalysis(version, limit).mapNotNull { it.toDomain() }

    override suspend fun markAnalyzed(activityId: Long, version: Int) {
        activityDao.setAnalysisVersion(activityId, version)
    }
}
