package com.ridecomp.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import com.ridecomp.data.db.DbNames

/** One lap (05-DATA-MODEL §1 `laps`). `index` is a SQL keyword, so the column is `lap_index`. */
@Entity(
    tableName = DbNames.TABLE_LAPS,
    primaryKeys = ["activity_id", "lap_index"],
    foreignKeys = [
        ForeignKey(
            entity = ActivityEntity::class,
            parentColumns = ["id"],
            childColumns = ["activity_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class LapEntity(
    @ColumnInfo(name = "activity_id") val activityId: Long,
    @ColumnInfo(name = "lap_index") val lapIndex: Int,
    @ColumnInfo(name = "start_t") val startTimeMs: Long,
    @ColumnInfo(name = "end_t") val endTimeMs: Long,
    @ColumnInfo(name = "distance_m") val distanceM: Double,
    @ColumnInfo(name = "moving_s") val movingS: Long,
    @ColumnInfo(name = "avg_speed") val avgSpeedMps: Double,
    @ColumnInfo(name = "max_speed") val maxSpeedMps: Double,
    @ColumnInfo(name = "avg_hr") val avgHr: Int?,
    @ColumnInfo(name = "max_hr") val maxHr: Int?,
    @ColumnInfo(name = "avg_cadence") val avgCadence: Int?,
    @ColumnInfo(name = "avg_power") val avgPower: Int?,
    @ColumnInfo(name = "max_power") val maxPower: Int?,
    @ColumnInfo(name = "ascent_m") val ascentM: Double,
    @ColumnInfo(name = "trigger") val trigger: String,
)
