package com.ridecomp.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.ridecomp.data.db.DbNames

/**
 * One best effort of a ride (schema v6, 05-DATA-MODEL `best_efforts`, T4.1). `profile_kind`, `type` and
 * `key` are stable strings; `value` is seconds, watts, meters or m/s depending on the key.
 */
@Entity(
    tableName = DbNames.TABLE_BEST_EFFORTS,
    foreignKeys = [
        ForeignKey(
            entity = ActivityEntity::class,
            parentColumns = ["id"],
            childColumns = ["activity_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["activity_id"]),
        Index(value = ["profile_kind", "type", "key", "value"]),
    ],
)
data class BestEffortEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "activity_id") val activityId: Long,
    @ColumnInfo(name = "profile_kind") val profileKind: String,
    @ColumnInfo(name = "type") val type: String,
    @ColumnInfo(name = "key") val key: String,
    @ColumnInfo(name = "value") val value: Double,
    @ColumnInfo(name = "start_t") val startT: Long?,
    @ColumnInfo(name = "end_t") val endT: Long?,
    @ColumnInfo(name = "is_pr") val isPr: Boolean,
)

/** Personal segment (schema v6, 05-DATA-MODEL `segments`, T4.2); the bounding box speeds up matching. */
@Entity(tableName = DbNames.TABLE_SEGMENTS)
data class SegmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "length_m") val lengthM: Double,
    @ColumnInfo(name = "gain_m") val gainM: Double,
    @ColumnInfo(name = "avg_grade") val avgGrade: Double,
    @ColumnInfo(name = "start_lat") val startLat: Double,
    @ColumnInfo(name = "start_lon") val startLon: Double,
    @ColumnInfo(name = "end_lat") val endLat: Double,
    @ColumnInfo(name = "end_lon") val endLon: Double,
    @ColumnInfo(name = "min_lat") val minLat: Double,
    @ColumnInfo(name = "min_lon") val minLon: Double,
    @ColumnInfo(name = "max_lat") val maxLat: Double,
    @ColumnInfo(name = "max_lon") val maxLon: Double,
    @ColumnInfo(name = "created_from") val createdFrom: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

@Entity(
    tableName = DbNames.TABLE_SEGMENT_POINTS,
    primaryKeys = ["segment_id", "seq"],
    foreignKeys = [
        ForeignKey(
            entity = SegmentEntity::class,
            parentColumns = ["id"],
            childColumns = ["segment_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class SegmentPointEntity(
    @ColumnInfo(name = "segment_id") val segmentId: Long,
    @ColumnInfo(name = "seq") val seq: Int,
    @ColumnInfo(name = "lat") val lat: Double,
    @ColumnInfo(name = "lon") val lon: Double,
    @ColumnInfo(name = "cum_dist_m") val cumDistM: Double,
)

/** One ride over a segment (schema v6, 05-DATA-MODEL `segment_efforts`, T4.2). */
@Entity(
    tableName = DbNames.TABLE_SEGMENT_EFFORTS,
    foreignKeys = [
        ForeignKey(
            entity = SegmentEntity::class,
            parentColumns = ["id"],
            childColumns = ["segment_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ActivityEntity::class,
            parentColumns = ["id"],
            childColumns = ["activity_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["segment_id", "elapsed_s"]), Index(value = ["activity_id"])],
)
data class SegmentEffortEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "segment_id") val segmentId: Long,
    @ColumnInfo(name = "activity_id") val activityId: Long,
    @ColumnInfo(name = "start_t") val startT: Long,
    @ColumnInfo(name = "end_t") val endT: Long,
    @ColumnInfo(name = "elapsed_s") val elapsedS: Double,
    @ColumnInfo(name = "avg_speed") val avgSpeedMps: Double,
    @ColumnInfo(name = "avg_hr") val avgHr: Int?,
    @ColumnInfo(name = "avg_power") val avgPower: Int?,
    @ColumnInfo(name = "is_pr") val isPr: Boolean,
)

/** Training load per local day with fitness/fatigue/form (schema v6, 05-DATA-MODEL `daily_training`, T4.4). */
@Entity(tableName = DbNames.TABLE_DAILY_TRAINING)
data class DailyTrainingEntity(
    /** Local date `yyyy-MM-dd`. */
    @PrimaryKey @ColumnInfo(name = "date") val date: String,
    @ColumnInfo(name = "load") val load: Double,
    @ColumnInfo(name = "ctl") val ctl: Double,
    @ColumnInfo(name = "atl") val atl: Double,
    @ColumnInfo(name = "tsb") val tsb: Double,
)
