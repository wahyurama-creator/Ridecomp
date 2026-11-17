package com.ridecomp.data.db

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ridecomp.core.config.ProfileDefaults
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Migration tests (TEST-07, DATA-02): each migration runs on a database of the previous version filled with
 * data, Room validates the resulting schema against the exported one, and user data must survive.
 */
@RunWith(AndroidJUnit4::class)
class RideCompMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        RideCompDatabase::class.java,
    )

    @Test
    fun migrate_1_to_2_keeps_rides_and_adds_sensors() {
        helper.createDatabase(TEST_DB, 1).apply {
            execSQL(
                "INSERT INTO activities (id, uuid, name, status, started_at, elapsed_s, moving_s, distance_m, " +
                    "ascent_m, descent_m, avg_speed, max_speed, last_checkpoint_at) " +
                    "VALUES (1, 'u1', 'Morning ride', 'finished', 1000, 60, 50, 400.0, 3.0, 2.0, 8.0, 10.0, 2000)",
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 2, true, RideCompMigrations.MIGRATION_1_2)

        db.query("SELECT name FROM activities WHERE id = 1").use { cursor ->
            cursor.moveToFirst()
            assertEquals("Morning ride", cursor.getString(0))
        }
        db.execSQL(
            "INSERT INTO sensors (remote_id, name, services, auto_connect, created_at) VALUES ('AA:BB', 'HRM', 'hr', 1, 5)",
        )
        db.query("SELECT COUNT(*) FROM sensors").use { cursor ->
            cursor.moveToFirst()
            assertEquals(1, cursor.getInt(0))
        }
    }

    @Test
    fun migrate_2_to_3_seeds_the_built_in_profiles() {
        helper.createDatabase(TEST_DB_3, 2).close()

        val db = helper.runMigrationsAndValidate(TEST_DB_3, 3, true, RideCompMigrations.MIGRATION_2_3)

        db.query("SELECT kind FROM activity_profiles ORDER BY sort_order").use { cursor ->
            val kinds = buildList { while (cursor.moveToNext()) add(cursor.getString(0)) }
            assertEquals(listOf("road", "gravel", "mtb", "indoor", "commute"), kinds)
        }
    }

    @Test
    fun migrate_5_to_6_keeps_rides_marks_them_for_analysis_and_adds_phase_4_tables() {
        helper.createDatabase(TEST_DB_6, 5).apply {
            execSQL(
                "INSERT INTO activities (id, uuid, name, status, started_at, elapsed_s, moving_s, distance_m, " +
                    "ascent_m, descent_m, avg_speed, max_speed, last_checkpoint_at) " +
                    "VALUES (1, 'u1', 'Evening ride', 'finished', 1000, 60, 50, 400.0, 3.0, 2.0, 8.0, 10.0, 2000)",
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(TEST_DB_6, 6, true, RideCompMigrations.MIGRATION_5_6)

        db.query("SELECT name, analysis_version FROM activities WHERE id = 1").use { cursor ->
            cursor.moveToFirst()
            assertEquals("Evening ride", cursor.getString(0))
            assertEquals(0, cursor.getInt(1))
        }
        db.execSQL(
            "INSERT INTO best_efforts (activity_id, profile_kind, type, `key`, value, is_pr) " +
                "VALUES (1, 'road', 'other', 'longest_ride', 400.0, 1)",
        )
        // Room turns foreign keys on when it opens the app database; the test helper does not.
        db.execSQL("PRAGMA foreign_keys = ON")
        db.execSQL("DELETE FROM activities WHERE id = 1")
        db.query("SELECT COUNT(*) FROM best_efforts").use { cursor ->
            cursor.moveToFirst()
            assertEquals(0, cursor.getInt(0))
        }
    }

    @Test
    fun migrate_6_to_7_adds_strava_uploads_deleted_with_the_ride() {
        helper.createDatabase(TEST_DB_7, 6).apply {
            execSQL(
                "INSERT INTO activities (id, uuid, name, status, started_at, elapsed_s, moving_s, distance_m, " +
                    "ascent_m, descent_m, avg_speed, max_speed, last_checkpoint_at, analysis_version) " +
                    "VALUES (1, 'u1', 'Evening ride', 'finished', 1000, 60, 50, 400.0, 3.0, 2.0, 8.0, 10.0, 2000, 3)",
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(TEST_DB_7, 7, true, RideCompMigrations.MIGRATION_6_7)

        db.execSQL("INSERT INTO strava_uploads (activity_id, state, updated_at) VALUES (1, 'queued', 5)")
        db.execSQL("PRAGMA foreign_keys = ON")
        db.execSQL("DELETE FROM activities WHERE id = 1")
        db.query("SELECT COUNT(*) FROM strava_uploads").use { cursor ->
            cursor.moveToFirst()
            assertEquals(0, cursor.getInt(0))
        }
    }

    @Test
    fun migrate_7_to_8_makes_the_first_bike_from_the_profile_and_last_used_type() {
        helper.createDatabase(TEST_DB_8, 7).apply {
            insertProfiles()
            execSQL(
                "INSERT INTO profile (id, weight_kg, bike_weight_kg, wheel_circumference_mm, riding_position, " +
                    "surface) VALUES (1, 68.0, 11.5, 2136, 'hoods', 'road')",
            )
            execSQL("INSERT INTO settings (`key`, value) VALUES ('app.last_profile_id', '3')")
            close()
        }

        val db = helper.runMigrationsAndValidate(TEST_DB_8, 8, true, RideCompMigrations.MIGRATION_7_8)

        db.query("SELECT name, profile_id, weight_kg, wheel_circumference_mm FROM bikes").use { cursor ->
            assertEquals(1, cursor.count)
            cursor.moveToFirst()
            assertEquals("", cursor.getString(0))
            assertEquals(3L, cursor.getLong(1))
            assertEquals(11.5, cursor.getDouble(2), 0.0)
            assertEquals(2_136, cursor.getInt(3))
        }
    }

    @Test
    fun migrate_7_to_8_without_a_profile_uses_the_defaults_and_road() {
        helper.createDatabase(TEST_DB_8_EMPTY, 7).apply {
            insertProfiles()
            close()
        }

        val db = helper.runMigrationsAndValidate(TEST_DB_8_EMPTY, 8, true, RideCompMigrations.MIGRATION_7_8)

        db.query("SELECT profile_id, weight_kg, wheel_circumference_mm FROM bikes").use { cursor ->
            assertEquals(1, cursor.count)
            cursor.moveToFirst()
            assertEquals(1L, cursor.getLong(0))
            assertEquals(ProfileDefaults.BIKE_WEIGHT_KG, cursor.getDouble(1), 0.0)
            assertEquals(ProfileDefaults.WHEEL_CIRCUMFERENCE_MM, cursor.getInt(2))
        }
    }

    @Test
    fun migrate_8_to_9_gives_old_rides_the_first_bike_and_parts_go_with_their_bike() {
        helper.createDatabase(TEST_DB_9, 8).apply {
            execSQL(
                "INSERT INTO bikes (id, name, profile_id, weight_kg, wheel_circumference_mm) VALUES (4, '', 1, 9.0, 2105)",
            )
            execSQL(
                "INSERT INTO activities (id, uuid, name, status, started_at, elapsed_s, moving_s, distance_m, " +
                    "ascent_m, descent_m, avg_speed, max_speed, last_checkpoint_at, analysis_version) " +
                    "VALUES (1, 'u1', 'Ride', 'finished', 1000, 60, 50, 400.0, 3.0, 2.0, 8.0, 10.0, 2000, 3)",
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(TEST_DB_9, 9, true, RideCompMigrations.MIGRATION_8_9)

        db.query("SELECT bike_id FROM activities WHERE id = 1").use { cursor ->
            cursor.moveToFirst()
            assertEquals(4L, cursor.getLong(0))
        }
        db.execSQL(
            "INSERT INTO bike_components (bike_id, type, name, installed_at, initial_distance_m, limit_m) " +
                "VALUES (4, 'chain', '', 0, 0.0, 3000000.0)",
        )
        db.execSQL("PRAGMA foreign_keys = ON")
        db.execSQL("DELETE FROM bikes WHERE id = 4")
        db.query("SELECT COUNT(*) FROM bike_components").use { cursor ->
            cursor.moveToFirst()
            assertEquals(0, cursor.getInt(0))
        }
    }

    /** Road (id 1) and MTB (id 3), as seeded on a real install. */
    private fun SupportSQLiteDatabase.insertProfiles() {
        listOf(1 to "road", 3 to "mtb").forEachIndexed { order, (id, kind) ->
            execSQL(
                "INSERT INTO activity_profiles (id, name, kind, gps_enabled, surface, riding_position, fit_sport, " +
                    "fit_sub_sport, sort_order) VALUES ($id, '', '$kind', 1, 'road', 'hoods', 2, 0, $order)",
            )
        }
    }

    @Test
    fun all_migrations_open_on_latest_schema() {
        helper.createDatabase(TEST_DB_ALL, 1).close()

        Room.databaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            RideCompDatabase::class.java,
            TEST_DB_ALL,
        ).apply { RideCompMigrations.ALL.forEach { addMigrations(it) } }.build().apply {
            openHelper.writableDatabase.close()
            close()
        }
    }

    private companion object {
        const val TEST_DB = "migration-test.db"
        const val TEST_DB_ALL = "migration-all-test.db"
        const val TEST_DB_3 = "migration-3-test.db"
        const val TEST_DB_7 = "migration-7-test.db"
        const val TEST_DB_6 = "migration-6-test.db"
        const val TEST_DB_8 = "migration-8-test.db"
        const val TEST_DB_8_EMPTY = "migration-8-empty-test.db"
        const val TEST_DB_9 = "migration-9-test.db"
    }
}
