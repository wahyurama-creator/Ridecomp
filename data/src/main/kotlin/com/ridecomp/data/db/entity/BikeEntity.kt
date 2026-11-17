package com.ridecomp.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.ridecomp.data.db.DbNames

/**
 * A saved bike (`bikes`, schema v8, D-89). `profile_id` has no foreign key: a deleted custom profile
 * falls back to the default one when the ride starts, and the bike stays.
 */
@Entity(tableName = DbNames.TABLE_BIKES)
data class BikeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "profile_id") val profileId: Long,
    @ColumnInfo(name = "weight_kg") val weightKg: Double,
    @ColumnInfo(name = "wheel_circumference_mm") val wheelCircumferenceMm: Int,
)
