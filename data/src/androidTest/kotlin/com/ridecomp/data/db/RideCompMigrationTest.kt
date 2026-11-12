package com.ridecomp.data.db

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Migration tests (TEST-07, DATA-02). For every new version add a test that creates the previous schema,
 * inserts data, runs the migration and checks the data survived.
 */
@RunWith(AndroidJUnit4::class)
class RideCompMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        RideCompDatabase::class.java,
    )

    @Test
    fun all_migrations_open_on_latest_schema() {
        helper.createDatabase(TEST_DB, FIRST_VERSION).close()

        Room.databaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            RideCompDatabase::class.java,
            TEST_DB,
        ).apply { RideCompMigrations.ALL.forEach { addMigrations(it) } }.build().apply {
            openHelper.writableDatabase.close()
            close()
        }
    }

    private companion object {
        const val TEST_DB = "migration-test.db"
        const val FIRST_VERSION = 1
    }
}
