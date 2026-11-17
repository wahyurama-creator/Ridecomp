package com.ridecomp.core.repository

import com.ridecomp.core.model.Activity
import com.ridecomp.core.model.ActivityStatus
import com.ridecomp.core.model.Lap
import com.ridecomp.core.model.PeriodTotals
import com.ridecomp.core.model.RideEvent
import com.ridecomp.core.model.RideSample
import com.ridecomp.core.model.RideTotals
import kotlinx.coroutines.flow.Flow

/** New activity row created when a ride starts. */
public data class NewActivity(
    val uuid: String,
    val name: String,
    val startedAtMs: Long,
    /** Activity profile used (12-FEATURE-SPECS §4); null for rides recorded before profiles. */
    val profileId: Long? = null,
    /** Route followed during the ride (T3.6). */
    val routeId: Long? = null,
    /** Bike ridden (T7.3): its parts count this ride's distance. */
    val bikeId: Long? = null,
)

/** Rides and their track, events and laps (05-DATA-MODEL §1). Implemented in :data (ARCH-03). */
public interface ActivityRepository {
    /** Creates a ride in `recording` status and returns its id. */
    public suspend fun create(activity: NewActivity): Long

    /** Writes one flush batch in a single transaction (DATA-03). */
    public suspend fun appendSamples(activityId: Long, samples: List<RideSample>)

    public suspend fun appendEvents(activityId: Long, events: List<RideEvent>)

    public suspend fun saveLap(activityId: Long, lap: Lap)

    /** Stores the running totals and status so a killed ride can be recovered (03-ARCH §4.5). */
    public suspend fun checkpoint(activityId: Long, status: ActivityStatus, totals: RideTotals, atMs: Long)

    /** Marks the ride as saved (`finished`). */
    public suspend fun finish(activityId: Long, name: String, totals: RideTotals, endedAtMs: Long)

    /** Deletes the ride, its children and its files (SEC-STO-06). */
    public suspend fun delete(activityId: Long)

    public suspend fun rename(activityId: Long, name: String)

    public suspend fun markFitExported(activityId: Long, fitPath: String, atMs: Long)

    public suspend fun get(activityId: Long): Activity?

    public fun observe(activityId: Long): Flow<Activity?>

    /** The most recent ride left in `recording`/`paused` by a crash or kill. */
    public suspend fun findRecoverable(): Activity?

    /** One page of saved rides, newest first (DATA-04). */
    public fun observeFinished(limit: Int, offset: Int): Flow<List<Activity>>

    public fun observeFinishedCount(): Flow<Int>

    /** Totals of finished rides started at or after [sinceMs] (history "this month" card). */
    public fun observeTotalsSince(sinceMs: Long): Flow<PeriodTotals>

    public suspend fun getLaps(activityId: Long): List<Lap>

    public suspend fun getEvents(activityId: Long): List<RideEvent>

    /** Bounded chunk of samples strictly after [afterTimeMs], oldest first (keyset paging). */
    public suspend fun getSamples(activityId: Long, afterTimeMs: Long, limit: Int): List<RideSample>

    public suspend fun getSampleCount(activityId: Long): Int
}

/** Which saved rides still need the post-ride analysis (D-67). Implemented in :data (ARCH-03). */
public interface ActivityAnalysisRepository {
    /**
     * Saved rides not yet analyzed at [version], with the version they reached, oldest first so "new
     * record" flags follow the order the rides happened.
     */
    public suspend fun getPendingAnalysis(version: Int, limit: Int): List<PendingAnalysis>

    /** Sets [version] unless the ride's version is no longer [expectedVersion] (lowered meanwhile). */
    public suspend fun markAnalyzed(activityId: Long, version: Int, expectedVersion: Int)

    /** Lowers every ride above [toVersion] to it, so the later steps run again. */
    public suspend fun resetAnalysis(toVersion: Int)
}

/** A ride waiting for analysis and the version it already reached. */
public data class PendingAnalysis(val activity: Activity, val doneVersion: Int)
