package com.ridecomp.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import com.ridecomp.data.db.dao.ActivityDao
import com.ridecomp.data.db.dao.EventDao
import com.ridecomp.data.db.dao.LapDao
import com.ridecomp.data.db.dao.ProfileDao
import com.ridecomp.data.db.dao.SettingsDao
import com.ridecomp.data.db.dao.TrackPointDao
import com.ridecomp.data.db.entity.ActivityEntity
import com.ridecomp.data.db.entity.EventEntity
import com.ridecomp.data.db.entity.LapEntity
import com.ridecomp.data.db.entity.ProfileEntity
import com.ridecomp.data.db.entity.SettingEntity
import com.ridecomp.data.db.entity.TrackPointEntity

/**
 * The app database. Every schema change bumps [RideCompDatabase.VERSION], adds an explicit migration to
 * [RideCompMigrations.ALL] and a migration test (DATA-02). Schemas are exported to `data/schemas/`.
 */
@Database(
    entities = [
        ProfileEntity::class,
        SettingEntity::class,
        ActivityEntity::class,
        TrackPointEntity::class,
        EventEntity::class,
        LapEntity::class,
    ],
    version = RideCompDatabase.VERSION,
    exportSchema = true,
)
abstract class RideCompDatabase : RoomDatabase() {
    abstract fun activityDao(): ActivityDao

    abstract fun trackPointDao(): TrackPointDao

    abstract fun eventDao(): EventDao

    abstract fun lapDao(): LapDao

    abstract fun profileDao(): ProfileDao

    abstract fun settingsDao(): SettingsDao

    companion object {
        const val VERSION = 1

        /**
         * Builds the database in internal storage (SEC-STO-01). Destructive migration is never enabled:
         * users' rides must survive every update (DATA-02).
         */
        fun build(context: Context): RideCompDatabase =
            Room.databaseBuilder(context.applicationContext, RideCompDatabase::class.java, DbNames.DATABASE_FILE)
                .apply { RideCompMigrations.ALL.forEach { addMigrations(it) } }
                .build()
    }
}

/** Ordered list of all migrations. Empty while the schema is at version 1. */
object RideCompMigrations {
    val ALL: List<Migration> = emptyList()
}
