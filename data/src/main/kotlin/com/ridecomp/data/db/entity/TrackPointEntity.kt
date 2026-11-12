package com.ridecomp.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import com.ridecomp.data.db.DbNames

/** One record per second (05-DATA-MODEL §1 `track_points`). Composite PK `(activity_id, t)`. */
@Entity(
    tableName = DbNames.TABLE_TRACK_POINTS,
    primaryKeys = ["activity_id", "t"],
    foreignKeys = [
        ForeignKey(
            entity = ActivityEntity::class,
            parentColumns = ["id"],
            childColumns = ["activity_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class TrackPointEntity(
    @ColumnInfo(name = "activity_id") val activityId: Long,
    @ColumnInfo(name = "t") val timeMs: Long,
    @ColumnInfo(name = "lat") val lat: Double?,
    @ColumnInfo(name = "lon") val lon: Double?,
    @ColumnInfo(name = "alt_m") val altM: Double?,
    @ColumnInfo(name = "gps_accuracy_m") val gpsAccuracyM: Double?,
    @ColumnInfo(name = "distance_m") val distanceM: Double,
    @ColumnInfo(name = "speed_mps") val speedMps: Double?,
    @ColumnInfo(name = "hr") val hr: Int?,
    @ColumnInfo(name = "cadence") val cadence: Int?,
    @ColumnInfo(name = "power") val power: Int?,
    @ColumnInfo(name = "est_power") val estPower: Int?,
    @ColumnInfo(name = "temp_c") val tempC: Double?,
)
