package com.ridecomp.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.ridecomp.data.db.DbNames

/**
 * Key-value setting (05-DATA-MODEL §1 `settings`). Kept in Room (not DataStore) so settings are part of
 * the database snapshot used by backup & restore (12-FEATURE-SPECS §12).
 */
@Entity(tableName = DbNames.TABLE_SETTINGS)
data class SettingEntity(
    @PrimaryKey @ColumnInfo(name = "key") val key: String,
    @ColumnInfo(name = "value") val value: String,
)
