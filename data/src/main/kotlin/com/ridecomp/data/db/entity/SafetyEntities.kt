package com.ridecomp.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.ridecomp.data.db.DbNames

/** Emergency contact (schema v4). Third-party personal data: never logged or exported (SEC-SMS-05). */
@Entity(tableName = DbNames.TABLE_EMERGENCY_CONTACTS)
data class EmergencyContactEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "display_name") val displayName: String,
    @ColumnInfo(name = "phone_e164") val phone: String,
    @ColumnInfo(name = "sort_order") val sortOrder: Int,
) {
    override fun toString(): String = "EmergencyContactEntity(id=$id)"
}

/** One detected or manual incident (schema v4, 05-DATA-MODEL `incidents`). */
@Entity(
    tableName = DbNames.TABLE_INCIDENTS,
    foreignKeys = [
        ForeignKey(
            entity = ActivityEntity::class,
            parentColumns = ["id"],
            childColumns = ["activity_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["activity_id"])],
)
data class IncidentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "activity_id") val activityId: Long,
    @ColumnInfo(name = "detected_t") val detectedAt: Long,
    @ColumnInfo(name = "peak_g") val peakG: Double?,
    @ColumnInfo(name = "speed_before_mps") val speedBeforeMps: Double?,
    /** `cancelled|sent|manual_sos|failed`. */
    @ColumnInfo(name = "outcome") val outcome: String,
    @ColumnInfo(name = "sms_sent_count") val smsSentCount: Int,
    @ColumnInfo(name = "last_status") val lastStatus: String?,
)
