package com.ridecomp.data.backup

import android.database.sqlite.SQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ridecomp.core.model.EmergencyContact
import com.ridecomp.core.source.DispatcherProvider
import com.ridecomp.core.source.Logger
import com.ridecomp.data.db.DbNames
import com.ridecomp.data.db.RideCompDatabase
import com.ridecomp.data.repository.SafetyRepositoryImpl
import java.io.File
import kotlin.test.assertEquals
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith

/** Device test: a snapshot is a valid single-file database without emergency contacts (SEC-BK-01). */
@RunWith(AndroidJUnit4::class)
class RoomBackupDatabaseTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private val dispatchers = object : DispatcherProvider {
        override val main = Dispatchers.Main
        override val default = Dispatchers.Default
        override val io = Dispatchers.IO
    }

    private val logger = object : Logger {
        override fun debug(tag: String, message: () -> String) = Unit

        override fun info(tag: String, message: () -> String) = Unit

        override fun warn(tag: String, throwable: Throwable?, message: () -> String) = Unit

        override fun error(tag: String, throwable: Throwable?, message: () -> String) = Unit
    }

    @Test
    fun snapshot_has_no_emergency_contacts_and_passes_integrity_check() = runTest {
        val database = RideCompDatabase.build(context)
        val safety = SafetyRepositoryImpl(database.emergencyContactDao(), database.settingsDao())
        safety.addContact(EmergencyContact(displayName = "Ibu", phone = "0811000000"))
        val target = File(context.cacheDir, SNAPSHOT_NAME).apply { delete() }

        RoomBackupDatabase(context, database, dispatchers, logger).snapshot(target)

        SQLiteDatabase.openDatabase(target.path, null, SQLiteDatabase.OPEN_READONLY).use { copy ->
            copy.rawQuery("SELECT COUNT(*) FROM ${DbNames.TABLE_EMERGENCY_CONTACTS}", null).use {
                it.moveToFirst()
                assertEquals(0, it.getInt(0))
            }
            copy.rawQuery("PRAGMA integrity_check", null).use {
                it.moveToFirst()
                assertEquals("ok", it.getString(0))
            }
            assertEquals(RideCompDatabase.VERSION, copy.version)
        }
        safety.getContacts().forEach { safety.removeContact(it.id) }
        database.close()
        target.delete()
    }

    private companion object {
        const val SNAPSHOT_NAME = "snapshot-test.db"
    }
}
