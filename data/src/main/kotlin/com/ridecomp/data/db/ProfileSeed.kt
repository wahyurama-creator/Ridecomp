package com.ridecomp.data.db

import androidx.sqlite.db.SupportSQLiteDatabase
import com.ridecomp.core.model.BuiltInProfiles
import com.ridecomp.data.mapper.toEntity

/**
 * Inserts the built-in activity profiles (12-FEATURE-SPECS §4). Runs once: in the 2→3 migration for
 * existing installs and in `onCreate` for new ones. Plain SQL with bind arguments (no string building).
 */
internal object ProfileSeed {
    private const val INSERT_SQL = "INSERT INTO `${DbNames.TABLE_ACTIVITY_PROFILES}` " +
        "(`name`, `kind`, `gps_enabled`, `data_pages_json`, `auto_pause_enabled`, `auto_lap_m`, `screen_mode`, " +
        "`surface`, `riding_position`, `fit_sport`, `fit_sub_sport`, `sort_order`) " +
        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"

    fun insertBuiltIns(db: SupportSQLiteDatabase) {
        BuiltInProfiles.ALL.map { it.toEntity() }.forEach { profile ->
            db.execSQL(
                INSERT_SQL,
                arrayOf<Any?>(
                    profile.name,
                    profile.kind,
                    profile.isGpsEnabled.toSqlInt(),
                    profile.dataPagesJson,
                    profile.isAutoPauseEnabled?.toSqlInt(),
                    profile.autoLapM,
                    profile.screenMode,
                    profile.surface,
                    profile.ridingPosition,
                    profile.fitSport,
                    profile.fitSubSport,
                    profile.sortOrder,
                ),
            )
        }
    }

    private fun Boolean.toSqlInt(): Int = if (this) 1 else 0
}
