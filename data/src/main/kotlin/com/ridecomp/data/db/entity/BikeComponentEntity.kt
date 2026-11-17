package com.ridecomp.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.ridecomp.data.db.DbNames

/** A part of a saved bike (`bike_components`, schema v9, T7.3); deleted with its bike. */
@Entity(
    tableName = DbNames.TABLE_BIKE_COMPONENTS,
    foreignKeys = [
        ForeignKey(
            entity = BikeEntity::class,
            parentColumns = ["id"],
            childColumns = ["bike_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["bike_id"])],
)
data class BikeComponentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "bike_id") val bikeId: Long,
    /** `chain|tires|brake_pads|cassette|service|other`. */
    @ColumnInfo(name = "type") val type: String,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "installed_at") val installedAt: Long,
    @ColumnInfo(name = "initial_distance_m") val initialDistanceM: Double,
    @ColumnInfo(name = "limit_m") val limitM: Double?,
)

/** A part with the distance of the finished rides on its bike since it was fitted. */
data class ComponentWearRow(
    @Embedded val component: BikeComponentEntity,
    @ColumnInfo(name = "ridden_m") val riddenM: Double,
)
