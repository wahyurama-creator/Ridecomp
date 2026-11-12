package com.ridecomp.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.ridecomp.data.db.DbNames

/** Rider profile, single row (05-DATA-MODEL §1 `profile`). SI units. */
@Entity(tableName = DbNames.TABLE_PROFILE)
data class ProfileEntity(
    @PrimaryKey val id: Int = DbNames.SINGLE_PROFILE_ID,
    @ColumnInfo(name = "weight_kg") val weightKg: Double,
    @ColumnInfo(name = "bike_weight_kg") val bikeWeightKg: Double,
    @ColumnInfo(name = "wheel_circumference_mm") val wheelCircumferenceMm: Int,
    @ColumnInfo(name = "hr_max") val hrMax: Int?,
    @ColumnInfo(name = "hr_rest") val hrRest: Int?,
    @ColumnInfo(name = "ftp_w") val ftpW: Int?,
    @ColumnInfo(name = "birth_year") val birthYear: Int?,
    @ColumnInfo(name = "sex") val sex: String?,
    @ColumnInfo(name = "riding_position") val ridingPosition: String,
    @ColumnInfo(name = "surface") val surface: String,
)
