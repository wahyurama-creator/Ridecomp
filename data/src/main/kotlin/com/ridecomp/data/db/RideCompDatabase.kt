package com.ridecomp.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.ridecomp.data.db.dao.ActivityDao
import com.ridecomp.data.db.dao.ActivityProfileDao
import com.ridecomp.data.db.dao.EventDao
import com.ridecomp.data.db.dao.LapDao
import com.ridecomp.data.db.dao.ProfileDao
import com.ridecomp.data.db.dao.SensorDao
import com.ridecomp.data.db.dao.SettingsDao
import com.ridecomp.data.db.dao.TrackPointDao
import com.ridecomp.data.db.entity.ActivityEntity
import com.ridecomp.data.db.entity.ActivityProfileEntity
import com.ridecomp.data.db.entity.EventEntity
import com.ridecomp.data.db.entity.LapEntity
import com.ridecomp.data.db.entity.ProfileEntity
import com.ridecomp.data.db.entity.SensorEntity
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
        SensorEntity::class,
        ActivityProfileEntity::class,
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

    abstract fun sensorDao(): SensorDao

    abstract fun activityProfileDao(): ActivityProfileDao

    companion object {
        const val VERSION = 3

        /**
         * Builds the database in internal storage (SEC-STO-01). Destructive migration is never enabled:
         * users' rides must survive every update (DATA-02).
         */
        fun build(context: Context): RideCompDatabase =
            Room.databaseBuilder(context.applicationContext, RideCompDatabase::class.java, DbNames.DATABASE_FILE)
                .apply { RideCompMigrations.ALL.forEach { addMigrations(it) } }
                .addCallback(SeedCallback)
                .build()
    }
}

/** Ordered list of all migrations (DATA-02). SQL is copied from the exported schemas. */
object RideCompMigrations {
    /** v2: paired BLE sensors (T2.1). */
    val MIGRATION_1_2: Migration = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `${DbNames.TABLE_SENSORS}` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`remote_id` TEXT NOT NULL, `name` TEXT NOT NULL, `services` TEXT NOT NULL, " +
                    "`last_battery` INTEGER, `auto_connect` INTEGER NOT NULL, `created_at` INTEGER NOT NULL)",
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_sensors_remote_id` " +
                    "ON `${DbNames.TABLE_SENSORS}` (`remote_id`)",
            )
        }
    }

    /** v3: activity profiles (T2.13), seeded with the built-ins. */
    val MIGRATION_2_3: Migration = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `${DbNames.TABLE_ACTIVITY_PROFILES}` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `kind` TEXT NOT NULL, " +
                    "`gps_enabled` INTEGER NOT NULL, `data_pages_json` TEXT, `auto_pause_enabled` INTEGER, " +
                    "`auto_pause_speed_mps` REAL, `auto_pause_delay_s` INTEGER, `idle_prompt_min` INTEGER, " +
                    "`drink_interval_min` INTEGER, `eat_interval_min` INTEGER, `reminder_by_distance_m` REAL, " +
                    "`auto_lap_m` REAL, `screen_mode` TEXT, `surface` TEXT NOT NULL, " +
                    "`riding_position` TEXT NOT NULL, " +
                    "`fit_sport` INTEGER NOT NULL, `fit_sub_sport` INTEGER NOT NULL, `sort_order` INTEGER NOT NULL)",
            )
            ProfileSeed.insertBuiltIns(db)
        }
    }

    val ALL: List<Migration> = listOf(MIGRATION_1_2, MIGRATION_2_3)
}

/** New installs get the built-in profiles too (existing ones get them from MIGRATION_2_3). */
private object SeedCallback : RoomDatabase.Callback() {
    override fun onCreate(db: SupportSQLiteDatabase) {
        ProfileSeed.insertBuiltIns(db)
    }
}
