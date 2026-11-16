package com.ridecomp.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import com.ridecomp.data.db.DbNames

/**
 * The Strava upload of a ride (schema v7, T5.3): one row per ride, deleted with it. `state` and `error`
 * are stable strings ([com.ridecomp.data.mapper.StravaUploadMapper]); no token or file content is stored.
 */
@Entity(
    tableName = DbNames.TABLE_STRAVA_UPLOADS,
    foreignKeys = [
        ForeignKey(
            entity = ActivityEntity::class,
            parentColumns = ["id"],
            childColumns = ["activity_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class StravaUploadEntity(
    @PrimaryKey @ColumnInfo(name = "activity_id") val activityId: Long,
    @ColumnInfo(name = "state") val state: String,
    @ColumnInfo(name = "upload_id") val uploadId: Long?,
    @ColumnInfo(name = "strava_activity_id") val stravaActivityId: Long?,
    @ColumnInfo(name = "error") val error: String?,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)
