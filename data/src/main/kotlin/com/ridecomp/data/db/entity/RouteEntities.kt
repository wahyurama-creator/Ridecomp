package com.ridecomp.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.ridecomp.data.db.DbNames

/** Imported route (schema v5, 05-DATA-MODEL `routes`). `source_file` is a label only (SEC-IN-04). */
@Entity(tableName = DbNames.TABLE_ROUTES)
data class RouteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "source_file") val sourceFile: String?,
    @ColumnInfo(name = "distance_m") val distanceM: Double,
    @ColumnInfo(name = "ascent_m") val ascentM: Double,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

@Entity(
    tableName = DbNames.TABLE_ROUTE_POINTS,
    primaryKeys = ["route_id", "seq"],
    foreignKeys = [
        ForeignKey(
            entity = RouteEntity::class,
            parentColumns = ["id"],
            childColumns = ["route_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class RoutePointEntity(
    @ColumnInfo(name = "route_id") val routeId: Long,
    @ColumnInfo(name = "seq") val seq: Int,
    @ColumnInfo(name = "lat") val lat: Double,
    @ColumnInfo(name = "lon") val lon: Double,
    @ColumnInfo(name = "ele") val ele: Double?,
    @ColumnInfo(name = "cum_dist_m") val cumDistM: Double,
)

/** Climb found at import (T3.11, 05-DATA-MODEL `route_climbs`). */
@Entity(
    tableName = DbNames.TABLE_ROUTE_CLIMBS,
    primaryKeys = ["route_id", "idx"],
    foreignKeys = [
        ForeignKey(
            entity = RouteEntity::class,
            parentColumns = ["id"],
            childColumns = ["route_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class RouteClimbEntity(
    @ColumnInfo(name = "route_id") val routeId: Long,
    @ColumnInfo(name = "idx") val idx: Int,
    @ColumnInfo(name = "start_dist_m") val startDistM: Double,
    @ColumnInfo(name = "end_dist_m") val endDistM: Double,
    @ColumnInfo(name = "length_m") val lengthM: Double,
    @ColumnInfo(name = "gain_m") val gainM: Double,
    @ColumnInfo(name = "avg_grade") val avgGrade: Double,
    @ColumnInfo(name = "max_grade") val maxGrade: Double,
    @ColumnInfo(name = "grade_profile_json") val gradeProfileJson: String,
)

/** Turn found at import (T3.12, 05-DATA-MODEL `route_turns`). */
@Entity(
    tableName = DbNames.TABLE_ROUTE_TURNS,
    foreignKeys = [
        ForeignKey(
            entity = RouteEntity::class,
            parentColumns = ["id"],
            childColumns = ["route_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["route_id", "dist_m"])],
)
data class RouteTurnEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "route_id") val routeId: Long,
    @ColumnInfo(name = "dist_m") val distM: Double,
    @ColumnInfo(name = "angle_deg") val angleDeg: Double,
    /** `left|right|uturn`. */
    @ColumnInfo(name = "direction") val direction: String,
    /** `normal|sharp|uturn`. */
    @ColumnInfo(name = "severity") val severity: String,
    /** `geometry|waypoint`. */
    @ColumnInfo(name = "source") val source: String,
)
