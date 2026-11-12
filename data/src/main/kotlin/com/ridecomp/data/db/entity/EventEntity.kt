package com.ridecomp.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.ridecomp.data.db.DbNames

/** Ride state transitions and notable events (05-DATA-MODEL §1 `events`), e.g. FIT timer start/stop. */
@Entity(
    tableName = DbNames.TABLE_EVENTS,
    foreignKeys = [
        ForeignKey(
            entity = ActivityEntity::class,
            parentColumns = ["id"],
            childColumns = ["activity_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["activity_id", "t"])],
)
data class EventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "activity_id") val activityId: Long,
    @ColumnInfo(name = "t") val timeMs: Long,
    @ColumnInfo(name = "type") val type: String,
)
