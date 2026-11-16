package com.ridecomp.core.repository

import com.ridecomp.core.model.StravaUpload
import kotlinx.coroutines.flow.Flow

/** The Strava upload of each ride (`strava_uploads`, T5.3); deleted with the ride. */
public interface StravaUploadRepository {
    public fun observe(activityId: Long): Flow<StravaUpload?>

    public suspend fun get(activityId: Long): StravaUpload?

    public suspend fun save(upload: StravaUpload)

    /** Ids of the rides already on Strava (done or linked duplicate), for the history list. */
    public fun observeOnStrava(): Flow<Set<Long>>

    /** Uploads still on their way (queued or processing), oldest first. */
    public suspend fun getActive(): List<StravaUpload>
}
