package com.ridecomp.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.ridecomp.data.db.dao.ActivityDao
import com.ridecomp.data.db.dao.ActivityProfileDao
import com.ridecomp.data.db.dao.EmergencyContactDao
import com.ridecomp.data.db.dao.EventDao
import com.ridecomp.data.db.dao.IncidentDao
import com.ridecomp.data.db.dao.LapDao
import com.ridecomp.data.db.dao.ProfileDao
import com.ridecomp.data.db.dao.RouteDao
import com.ridecomp.data.db.dao.SensorDao
import com.ridecomp.data.db.dao.SettingsDao
import com.ridecomp.data.db.dao.TrackPointDao
import com.ridecomp.data.db.entity.ActivityEntity
import com.ridecomp.data.db.entity.ActivityProfileEntity
import com.ridecomp.data.db.entity.EmergencyContactEntity
import com.ridecomp.data.db.entity.EventEntity
import com.ridecomp.data.db.entity.IncidentEntity
import com.ridecomp.data.db.entity.LapEntity
import com.ridecomp.data.db.entity.ProfileEntity
import com.ridecomp.data.db.entity.RouteClimbEntity
import com.ridecomp.data.db.entity.RouteEntity
import com.ridecomp.data.db.entity.RoutePointEntity
import com.ridecomp.data.db.entity.RouteTurnEntity
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
        EmergencyContactEntity::class,
        IncidentEntity::class,
        RouteEntity::class,
        RoutePointEntity::class,
        RouteClimbEntity::class,
        RouteTurnEntity::class,
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

    abstract fun emergencyContactDao(): EmergencyContactDao

    abstract fun incidentDao(): IncidentDao

    abstract fun routeDao(): RouteDao

    companion object {
        const val VERSION = 5

        /**
         * Builds the database in internal storage (SEC-STO-01). Destructive migration is never enabled:
         * users' rides must survive every update (DATA-02).
         */
        fun build(context: Context): RideCompDatabase =
            Room.databaseBuilder(context.applicationContext, RideCompDatabase::class.java, DbNames.DATABASE_FILE)
                // A restore staged by the previous run replaces the data before the first open (§12.5),
                // on Room's background thread rather than here (often the main thread, STB-01).
                .openHelperFactory(RestoringOpenHelperFactory(context))
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

    /** v4: emergency contacts and incidents (T2.14–T2.16). */
    val MIGRATION_3_4: Migration = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `${DbNames.TABLE_EMERGENCY_CONTACTS}` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `display_name` TEXT NOT NULL, " +
                    "`phone_e164` TEXT NOT NULL, `sort_order` INTEGER NOT NULL)",
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `${DbNames.TABLE_INCIDENTS}` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `activity_id` INTEGER NOT NULL, " +
                    "`detected_t` INTEGER NOT NULL, `peak_g` REAL, `speed_before_mps` REAL, " +
                    "`outcome` TEXT NOT NULL, `sms_sent_count` INTEGER NOT NULL, `last_status` TEXT, " +
                    "FOREIGN KEY(`activity_id`) REFERENCES `activities`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_incidents_activity_id` " +
                    "ON `${DbNames.TABLE_INCIDENTS}` (`activity_id`)",
            )
        }
    }

    /** v5: imported routes, their points, climbs and turns (T3.1, T3.11, T3.12). */
    val MIGRATION_4_5: Migration = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            val cascade = "REFERENCES `${DbNames.TABLE_ROUTES}`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `${DbNames.TABLE_ROUTES}` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `source_file` TEXT, " +
                    "`distance_m` REAL NOT NULL, `ascent_m` REAL NOT NULL, `created_at` INTEGER NOT NULL)",
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `${DbNames.TABLE_ROUTE_POINTS}` (" +
                    "`route_id` INTEGER NOT NULL, `seq` INTEGER NOT NULL, `lat` REAL NOT NULL, `lon` REAL NOT NULL, " +
                    "`ele` REAL, `cum_dist_m` REAL NOT NULL, PRIMARY KEY(`route_id`, `seq`), " +
                    "FOREIGN KEY(`route_id`) $cascade",
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `${DbNames.TABLE_ROUTE_CLIMBS}` (" +
                    "`route_id` INTEGER NOT NULL, `idx` INTEGER NOT NULL, `start_dist_m` REAL NOT NULL, " +
                    "`end_dist_m` REAL NOT NULL, `length_m` REAL NOT NULL, `gain_m` REAL NOT NULL, " +
                    "`avg_grade` REAL NOT NULL, `max_grade` REAL NOT NULL, `grade_profile_json` TEXT NOT NULL, " +
                    "PRIMARY KEY(`route_id`, `idx`), FOREIGN KEY(`route_id`) $cascade",
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `${DbNames.TABLE_ROUTE_TURNS}` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `route_id` INTEGER NOT NULL, " +
                    "`dist_m` REAL NOT NULL, `angle_deg` REAL NOT NULL, `direction` TEXT NOT NULL, " +
                    "`severity` TEXT NOT NULL, `source` TEXT NOT NULL, FOREIGN KEY(`route_id`) $cascade",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_route_turns_route_id_dist_m` " +
                    "ON `${DbNames.TABLE_ROUTE_TURNS}` (`route_id`, `dist_m`)",
            )
        }
    }

    val ALL: List<Migration> = listOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
}

/** New installs get the built-in profiles too (existing ones get them from MIGRATION_2_3). */
private object SeedCallback : RoomDatabase.Callback() {
    override fun onCreate(db: SupportSQLiteDatabase) {
        ProfileSeed.insertBuiltIns(db)
    }
}
