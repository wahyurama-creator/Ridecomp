package com.ridecomp.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ridecomp.data.db.DbNames
import com.ridecomp.data.db.entity.TrackPointEntity

/** Track points are written in 5 s batches and read in bounded chunks, never all at once (DATA-03/04). */
@Dao
interface TrackPointDao {
    /**
     * Inserts one flush batch in a single transaction (Room wraps list inserts in one).
     * A point with an existing `(activity_id, t)` replaces the old one, which makes re-flushing after
     * recovery idempotent.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(points: List<TrackPointEntity>)

    @Query("SELECT COUNT(*) FROM ${DbNames.TABLE_TRACK_POINTS} WHERE activity_id = :activityId")
    suspend fun count(activityId: Long): Int

    /** Next chunk of points strictly after [afterTimeMs] (keyset paging for export/streaming). */
    @Query(
        "SELECT * FROM ${DbNames.TABLE_TRACK_POINTS} WHERE activity_id = :activityId AND t > :afterTimeMs " +
            "ORDER BY t ASC LIMIT :limit",
    )
    suspend fun getChunk(activityId: Long, afterTimeMs: Long, limit: Int): List<TrackPointEntity>

    /** Last stored point of a ride, used to resume after a crash. */
    @Query("SELECT * FROM ${DbNames.TABLE_TRACK_POINTS} WHERE activity_id = :activityId ORDER BY t DESC LIMIT 1")
    suspend fun getLast(activityId: Long): TrackPointEntity?
}
