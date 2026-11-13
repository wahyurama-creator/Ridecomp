package com.ridecomp.data.backup

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import com.ridecomp.core.engine.backup.BackupError
import com.ridecomp.core.source.DispatcherProvider
import com.ridecomp.core.source.Logger
import com.ridecomp.core.usecase.BackupDatabase
import com.ridecomp.data.db.DbNames
import com.ridecomp.data.db.RideCompDatabase
import com.ridecomp.data.mapper.SettingsKeys
import java.io.File
import kotlinx.coroutines.withContext

/**
 * Database side of backup & restore (12-FEATURE-SPECS §12).
 *
 * Snapshot: the database file and its WAL are copied while a write transaction blocks writers, so the pair
 * is consistent on every Android version (no `VACUUM INTO` before Android 11). The copy is then opened on
 * its own: the WAL is folded in, emergency contacts are deleted and the file is vacuumed so their old pages
 * are gone too (SEC-BK-01, SEC-SMS-05).
 *
 * Restore: the candidate is checked (integrity, schema version, expected tables) and staged next to the
 * live database; [PendingRestore] swaps it before Room opens on the next start (SEC-BK-03).
 */
class RoomBackupDatabase(
    private val context: Context,
    private val database: RideCompDatabase,
    private val dispatchers: DispatcherProvider,
    private val logger: Logger,
) : BackupDatabase {
    override val schemaVersion: Int = RideCompDatabase.VERSION

    override suspend fun snapshot(target: File): Unit = withContext(dispatchers.io) {
        val live = context.getDatabasePath(DbNames.DATABASE_FILE)
        val liveWal = File(live.path + WAL_SUFFIX)
        val targetWal = File(target.path + WAL_SUFFIX)
        val writable = database.openHelper.writableDatabase
        writable.beginTransaction()
        try {
            live.copyTo(target, overwrite = true)
            if (liveWal.exists()) liveWal.copyTo(targetWal, overwrite = true)
        } finally {
            writable.endTransaction()
        }
        SQLiteDatabase.openDatabase(target.path, null, SQLiteDatabase.OPEN_READWRITE).use { copy ->
            copy.execSQL("DELETE FROM `${DbNames.TABLE_EMERGENCY_CONTACTS}`")
            copy.execSQL(
                "UPDATE `${DbNames.TABLE_SETTINGS}` SET value = ? WHERE `key` = ?",
                arrayOf(false.toString(), SettingsKeys.SAFETY_ENABLED),
            )
            // Single-file archive member, and deleted rows physically removed.
            copy.rawQuery(JOURNAL_MODE_DELETE, null).use { it.moveToFirst() }
            copy.execSQL(VACUUM)
        }
        listOf(targetWal, File(target.path + SHM_SUFFIX)).forEach(File::delete)
    }

    override suspend fun stageRestore(candidate: File): BackupError? = withContext(dispatchers.io) {
        val error = validate(candidate)
        if (error == null) PendingRestore.stage(context, candidate)
        error
    }

    private fun validate(candidate: File): BackupError? = try {
        SQLiteDatabase.openDatabase(candidate.path, null, SQLiteDatabase.OPEN_READONLY).use(::inspect)
    } catch (e: SQLiteException) {
        logger.warn(TAG, e) { "Backup database cannot be opened" }
        BackupError.INVALID_DATABASE
    }

    private fun inspect(db: SQLiteDatabase): BackupError? {
        val integrity = db.rawQuery(INTEGRITY_CHECK, null).use { if (it.moveToFirst()) it.getString(0) else null }
        val version = db.version
        val hasActivities = db.rawQuery(TABLE_EXISTS, arrayOf(DbNames.TABLE_ACTIVITIES)).use { it.moveToFirst() }
        return when {
            integrity != INTEGRITY_OK || !hasActivities || version < 1 -> BackupError.INVALID_DATABASE
            version > RideCompDatabase.VERSION -> BackupError.NEWER_SCHEMA
            else -> null
        }
    }

    private companion object {
        const val TAG = "BackupDatabase"
        const val WAL_SUFFIX = "-wal"
        const val SHM_SUFFIX = "-shm"
        const val INTEGRITY_CHECK = "PRAGMA integrity_check"
        const val INTEGRITY_OK = "ok"
        const val JOURNAL_MODE_DELETE = "PRAGMA journal_mode = DELETE"
        const val VACUUM = "VACUUM"
        const val TABLE_EXISTS = "SELECT name FROM sqlite_master WHERE type = 'table' AND name = ?"
    }
}

/**
 * The restored database waits next to the live one until the next start, then replaces it before Room
 * opens. A rename within one folder is atomic: either the old or the new data, never a mix.
 */
object PendingRestore {
    private const val PENDING_SUFFIX = ".pending"
    private const val STAGING_SUFFIX = ".staging"
    private val SIDE_FILES = listOf("-wal", "-shm", "-journal")

    internal fun stage(context: Context, candidate: File) {
        val live = context.getDatabasePath(DbNames.DATABASE_FILE)
        val staging = File(live.path + STAGING_SUFFIX)
        candidate.copyTo(staging, overwrite = true)
        if (!staging.renameTo(File(live.path + PENDING_SUFFIX))) error("Cannot stage restored database")
    }

    /** Called once before the database is first opened (`RestoringOpenHelperFactory`). True when applied. */
    fun applyIfPresent(context: Context): Boolean {
        val live = context.getDatabasePath(DbNames.DATABASE_FILE)
        val pending = File(live.path + PENDING_SUFFIX)
        if (!pending.exists()) return false
        SIDE_FILES.forEach { File(live.path + it).delete() }
        return pending.renameTo(live)
    }
}
