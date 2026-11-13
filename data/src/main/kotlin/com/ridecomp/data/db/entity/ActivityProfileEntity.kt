package com.ridecomp.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.ridecomp.data.db.DbNames

/**
 * Activity profile (05-DATA-MODEL `activity_profiles`), schema version 3. Nullable ride-setting columns
 * mean "use the general setting" (decision D-32); reminder columns are kept for later use.
 */
@Entity(tableName = DbNames.TABLE_ACTIVITY_PROFILES)
data class ActivityProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "name") val name: String,
    /** `road|gravel|mtb|indoor|commute|custom`. */
    @ColumnInfo(name = "kind") val kind: String,
    @ColumnInfo(name = "gps_enabled") val isGpsEnabled: Boolean,
    @ColumnInfo(name = "data_pages_json") val dataPagesJson: String?,
    @ColumnInfo(name = "auto_pause_enabled") val isAutoPauseEnabled: Boolean?,
    @ColumnInfo(name = "auto_pause_speed_mps") val autoPauseSpeedMps: Double?,
    @ColumnInfo(name = "auto_pause_delay_s") val autoPauseDelayS: Int?,
    @ColumnInfo(name = "idle_prompt_min") val idlePromptMin: Int?,
    @ColumnInfo(name = "drink_interval_min") val drinkIntervalMin: Int?,
    @ColumnInfo(name = "eat_interval_min") val eatIntervalMin: Int?,
    @ColumnInfo(name = "reminder_by_distance_m") val reminderByDistanceM: Double?,
    @ColumnInfo(name = "auto_lap_m") val autoLapM: Double?,
    @ColumnInfo(name = "screen_mode") val screenMode: String?,
    @ColumnInfo(name = "surface") val surface: String,
    @ColumnInfo(name = "riding_position") val ridingPosition: String,
    @ColumnInfo(name = "fit_sport") val fitSport: Int,
    @ColumnInfo(name = "fit_sub_sport") val fitSubSport: Int,
    @ColumnInfo(name = "sort_order") val sortOrder: Int,
)
