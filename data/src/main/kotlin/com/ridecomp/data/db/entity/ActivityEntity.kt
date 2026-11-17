package com.ridecomp.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.ridecomp.data.db.DbNames

/**
 * One recorded ride (05-DATA-MODEL §1 `activities`). All columns known today are present from v1 so later
 * phases do not need migrations for them. Times are epoch ms UTC; speeds m/s; distances m.
 */
@Entity(
    tableName = DbNames.TABLE_ACTIVITIES,
    indices = [
        Index(value = ["uuid"], unique = true),
        Index(value = ["status"]),
        Index(value = ["started_at"]),
        Index(value = ["bike_id"]),
    ],
)
data class ActivityEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "uuid") val uuid: String,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "status") val status: String,
    @ColumnInfo(name = "started_at") val startedAt: Long,
    @ColumnInfo(name = "ended_at") val endedAt: Long? = null,
    @ColumnInfo(name = "elapsed_s") val elapsedS: Long = 0,
    @ColumnInfo(name = "moving_s") val movingS: Long = 0,
    @ColumnInfo(name = "distance_m") val distanceM: Double = 0.0,
    @ColumnInfo(name = "ascent_m") val ascentM: Double = 0.0,
    @ColumnInfo(name = "descent_m") val descentM: Double = 0.0,
    @ColumnInfo(name = "avg_speed") val avgSpeedMps: Double = 0.0,
    @ColumnInfo(name = "max_speed") val maxSpeedMps: Double = 0.0,
    @ColumnInfo(name = "avg_hr") val avgHr: Int? = null,
    @ColumnInfo(name = "max_hr") val maxHr: Int? = null,
    @ColumnInfo(name = "avg_cadence") val avgCadence: Int? = null,
    @ColumnInfo(name = "avg_power") val avgPower: Int? = null,
    @ColumnInfo(name = "max_power") val maxPower: Int? = null,
    @ColumnInfo(name = "np_power") val npPower: Int? = null,
    @ColumnInfo(name = "est_avg_power") val estAvgPower: Int? = null,
    @ColumnInfo(name = "calories") val calories: Int? = null,
    @ColumnInfo(name = "route_id") val routeId: Long? = null,
    @ColumnInfo(name = "profile_id") val profileId: Long? = null,
    @ColumnInfo(name = "training_load") val trainingLoad: Double? = null,
    @ColumnInfo(name = "load_method") val loadMethod: String? = null,
    @ColumnInfo(name = "intensity_factor") val intensityFactor: Double? = null,
    @ColumnInfo(name = "fit_exported_at") val fitExportedAt: Long? = null,
    @ColumnInfo(name = "fit_path") val fitPath: String? = null,
    @ColumnInfo(name = "strava_activity_id") val stravaActivityId: String? = null,
    @ColumnInfo(name = "last_checkpoint_at") val lastCheckpointAt: Long,
    /** Post-ride analysis done up to this version (v6, D-67); 0 = never analyzed. */
    @ColumnInfo(name = "analysis_version", defaultValue = "0") val analysisVersion: Int = 0,
    /** Bike ridden (v9, T7.3); no foreign key: deleting a bike keeps its rides. */
    @ColumnInfo(name = "bike_id") val bikeId: Long? = null,
)
