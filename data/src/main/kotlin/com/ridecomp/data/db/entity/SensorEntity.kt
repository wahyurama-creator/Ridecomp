package com.ridecomp.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.ridecomp.data.db.DbNames

/** A paired BLE sensor (05-DATA-MODEL `sensors`). Added in schema version 2. */
@Entity(tableName = DbNames.TABLE_SENSORS, indices = [Index(value = ["remote_id"], unique = true)])
data class SensorEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** BLE address. */
    @ColumnInfo(name = "remote_id") val remoteId: String,
    @ColumnInfo(name = "name") val name: String,
    /** CSV of `hr`, `csc`, `cp`. */
    @ColumnInfo(name = "services") val services: String,
    @ColumnInfo(name = "last_battery") val lastBattery: Int?,
    @ColumnInfo(name = "auto_connect") val isAutoConnect: Boolean,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)
