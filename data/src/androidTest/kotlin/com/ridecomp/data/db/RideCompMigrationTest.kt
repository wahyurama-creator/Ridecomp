package com.ridecomp.data.db

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
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
    }
}
