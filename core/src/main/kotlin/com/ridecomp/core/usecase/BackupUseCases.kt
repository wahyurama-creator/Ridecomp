package com.ridecomp.core.usecase

import com.ridecomp.core.config.Units
import com.ridecomp.core.engine.backup.BackupArchiveConfig
import com.ridecomp.core.engine.backup.BackupArchiveException
import com.ridecomp.core.engine.backup.BackupArchiveReader
import com.ridecomp.core.engine.backup.BackupArchiveWriter
import com.ridecomp.core.engine.backup.BackupCrypto
import com.ridecomp.core.engine.backup.BackupCryptoConfig
import com.ridecomp.core.engine.backup.BackupDecryptionException
import com.ridecomp.core.engine.backup.BackupEntrySource
import com.ridecomp.core.engine.backup.BackupError
import com.ridecomp.core.engine.backup.BackupManifest
import com.ridecomp.core.engine.backup.BackupManifestCodec
import com.ridecomp.core.repository.ActivityRepository
import com.ridecomp.core.source.AppRestarter
import com.ridecomp.core.source.Clock
import com.ridecomp.core.source.DispatcherProvider
import com.ridecomp.core.source.Logger
import com.ridecomp.core.source.RideController
import com.ridecomp.core.util.Outcome
import com.ridecomp.core.util.asFailure
import com.ridecomp.core.util.asSuccess
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/** Backup reminder rules (12-FEATURE-SPECS §12.4). */
public object BackupReminderConfig {
    public const val MIN_DAYS_SINCE_BACKUP: Long = 30
    public const val MIN_NEW_ACTIVITIES: Int = 5
    public const val SNOOZE_DAYS: Long = 7
    public const val MS_PER_DAY: Long = Units.SECONDS_PER_HOUR * HOURS_PER_DAY * Units.MS_PER_SECOND
}

private const val HOURS_PER_DAY = 24L

/** The database side of a backup (implemented in :data). */
public interface BackupDatabase {
    public val schemaVersion: Int

    /** Consistent copy of the database into [target], without emergency contacts (SEC-BK-01). */
    public suspend fun snapshot(target: File)

    /** Validates [candidate] (integrity, schema version) and stages it to replace the database on next start. */
    public suspend fun stageRestore(candidate: File): BackupError?
}

/** Files and documents of a backup (implemented in :platform). */
public interface BackupFiles {
    public val appVersion: String

    /** A fresh, empty folder in internal cache; the caller deletes it (SEC-BK-04). */
    public fun newWorkDir(): File

    /** FIT files already exported, included so the backup is useful outside the app. */
    public fun exportedFitFiles(): List<File>

    /** Puts restored FIT files back in the export folder. */
    public fun restoreFitFiles(files: List<File>)

    public fun openOutput(uri: String): OutputStream

    public fun openInput(uri: String): InputStream

    /** Local, unencrypted safety copy written before a restore (internal storage only). */
    public fun emergencyBackupFile(): File
}

/** Last backup facts, for the reminder (stored in `settings`). */
public interface BackupStateRepository {
    public fun observeLastBackupAtMs(): Flow<Long?>

    public fun observeActivityCountAtBackup(): Flow<Int>

    public fun observeReminderSnoozedUntilMs(): Flow<Long?>

    public suspend fun recordBackup(atMs: Long, activityCount: Int)

    public suspend fun snoozeReminder(untilMs: Long)
}

/** Result of writing one archive. */
public data class BackupWritten(val activityCount: Int, val createdAtMs: Long)

/** What the restore screen shows before the rider confirms (12-FEATURE-SPECS §12.5 step 1). */
public data class BackupSummary(val manifest: BackupManifest, val isEncrypted: Boolean)

/** Shared steps of creating an archive. */
public class BackupWriter(
    private val database: BackupDatabase,
    private val files: BackupFiles,
    private val activities: ActivityRepository,
    private val codec: BackupManifestCodec,
    private val clock: Clock,
) {
    /** Streams a backup into [output] (closed at the end). */
    public suspend fun write(output: OutputStream, password: CharArray?): BackupWritten {
        val work = files.newWorkDir()
        try {
            val dbFile = File(work, DB_SNAPSHOT_NAME)
            database.snapshot(dbFile)
            val entries = listOf(BackupEntrySource(BackupArchiveConfig.DATABASE, dbFile)) + fitEntries()
            val count = activities.observeFinishedCount().first()
            val manifest = BackupManifest(
                appVersion = files.appVersion,
                dbSchemaVersion = database.schemaVersion,
                createdAtMs = clock.nowMs(),
                activityCount = count,
                isEncrypted = password != null,
                checksums = emptyMap(),
            )
            val target = password?.let { BackupCrypto.encrypt(output, it) } ?: output
            target.use { BackupArchiveWriter(codec).write(it, manifest, entries) }
            return BackupWritten(count, manifest.createdAtMs)
        } finally {
            work.deleteRecursively()
        }
    }

    /** FIT names are made archive-safe and unique (the reader only accepts `[A-Za-z0-9_-]`). */
    private fun fitEntries(): List<BackupEntrySource> {
        val used = mutableSetOf<String>()
        return files.exportedFitFiles().map { file ->
            val base = file.nameWithoutExtension.replace(UNSAFE_CHARS, "_").take(MAX_NAME_LENGTH).ifEmpty { "ride" }
            var name = base
            var suffix = 1
            while (!used.add(name)) name = "${base}_${suffix++}"
            BackupEntrySource("${BackupArchiveConfig.FIT_DIR}$name$FIT_EXTENSION", file)
        }
    }

    private companion object {
        const val DB_SNAPSHOT_NAME = "snapshot.db"
        const val FIT_EXTENSION = ".fit"
        const val MAX_NAME_LENGTH = 90
        val UNSAFE_CHARS = Regex("[^A-Za-z0-9_-]")
    }
}

/** "Create backup" to a document chosen with SAF (12-FEATURE-SPECS §12.3); never during a ride. */
public class CreateBackupUseCase(
    private val writer: BackupWriter,
    private val files: BackupFiles,
    private val state: BackupStateRepository,
    private val controller: RideController,
    private val dispatchers: DispatcherProvider,
    private val logger: Logger,
) {
    public suspend operator fun invoke(uri: String, password: CharArray?): Outcome<Int, BackupError> {
        if (controller.liveRide.value.isRideInProgress()) return BackupError.RIDE_IN_PROGRESS.asFailure()
        return withContext(dispatchers.io) {
            try {
                val written = writer.write(files.openOutput(uri), password)
                state.recordBackup(written.createdAtMs, written.activityCount)
                written.activityCount.asSuccess()
            } catch (e: IOException) {
                logger.warn(TAG, e) { "Backup failed" }
                BackupError.WRITE_FAILED.asFailure()
            } finally {
                password?.fill(CLEARED)
            }
        }
    }

    private companion object {
        const val TAG = "Backup"
    }
}

/** Reads the manifest of a chosen file; asks for the password first when the file is encrypted. */
public class ReadBackupSummaryUseCase(
    private val files: BackupFiles,
    private val codec: BackupManifestCodec,
    private val dispatchers: DispatcherProvider,
) {
    public suspend fun isEncrypted(uri: String): Boolean = withContext(dispatchers.io) {
        files.openInput(uri).use { input ->
            val head = ByteArray(BackupCryptoConfig.MAGIC.size)
            val read = input.read(head)
            read == head.size && BackupCrypto.isEncrypted(head)
        }
    }

    public suspend operator fun invoke(uri: String, password: CharArray?): Outcome<BackupSummary, BackupError> =
        withContext(dispatchers.io) {
            runBackupStep {
                val encrypted = isEncrypted(uri)
                files.openInput(uri).use { raw ->
                    val input = if (encrypted) BackupCrypto.decrypt(raw, password ?: CharArray(0)) else raw
                    BackupSummary(BackupArchiveReader(codec).readManifest(input), encrypted)
                }
            }
        }
}

/**
 * Restore (12-FEATURE-SPECS §12.5): an emergency copy of the current data first, then extract to a private
 * temporary folder, verify everything, and stage the database to be swapped atomically on the next app
 * start. Any failure leaves the current data untouched. Never during a ride.
 */
public class RestoreBackupUseCase(
    private val writer: BackupWriter,
    private val database: BackupDatabase,
    private val files: BackupFiles,
    private val codec: BackupManifestCodec,
    private val controller: RideController,
    private val dispatchers: DispatcherProvider,
) {
    public suspend operator fun invoke(uri: String, password: CharArray?): Outcome<BackupManifest, BackupError> {
        if (controller.liveRide.value.isRideInProgress()) return BackupError.RIDE_IN_PROGRESS.asFailure()
        return withContext(dispatchers.io) {
            val work = files.newWorkDir()
            try {
                runBackupStep {
                    writer.write(files.emergencyBackupFile().outputStream(), password = null)
                    val manifest = files.openInput(uri).use { raw ->
                        val input = if (password != null) BackupCrypto.decrypt(raw, password) else raw
                        BackupArchiveReader(codec).extract(input, work)
                    }
                    if (manifest.dbSchemaVersion > database.schemaVersion) {
                        throw BackupArchiveException(BackupError.NEWER_SCHEMA)
                    }
                    database.stageRestore(File(work, BackupArchiveConfig.DATABASE))?.let {
                        throw BackupArchiveException(it)
                    }
                    files.restoreFitFiles(File(work, BackupArchiveConfig.FIT_DIR).listFiles().orEmpty().toList())
                    manifest
                }
            } finally {
                work.deleteRecursively()
                password?.fill(CLEARED)
            }
        }
    }
}

/** Home card "Last backup 34 days ago" (12-FEATURE-SPECS §12.4); null when not due. */
public data class BackupReminder(val daysSinceBackup: Long?, val newActivities: Int)

public class ObserveBackupReminderUseCase(
    private val state: BackupStateRepository,
    private val activities: ActivityRepository,
    private val clock: Clock,
) {
    public operator fun invoke(): Flow<BackupReminder?> = combine(
        state.observeLastBackupAtMs(),
        state.observeActivityCountAtBackup(),
        state.observeReminderSnoozedUntilMs(),
        activities.observeFinishedCount(),
    ) { lastAt, countAtBackup, snoozedUntil, count ->
        val now = clock.nowMs()
        val days = lastAt?.let { (now - it) / BackupReminderConfig.MS_PER_DAY }
        val newActivities = count - countAtBackup
        val isDue = (days == null || days > BackupReminderConfig.MIN_DAYS_SINCE_BACKUP) &&
            newActivities >= BackupReminderConfig.MIN_NEW_ACTIVITIES &&
            (snoozedUntil == null || now >= snoozedUntil)
        if (isDue) BackupReminder(days, newActivities) else null
    }
}

public class SnoozeBackupReminderUseCase(private val state: BackupStateRepository, private val clock: Clock) {
    public suspend operator fun invoke() {
        state.snoozeReminder(clock.nowMs() + BackupReminderConfig.SNOOZE_DAYS * BackupReminderConfig.MS_PER_DAY)
    }
}

/** Maps archive and crypto failures to user-facing errors; other I/O problems are WRITE_FAILED. */
private inline fun <T> runBackupStep(block: () -> T): Outcome<T, BackupError> = try {
    block().asSuccess()
} catch (e: BackupArchiveException) {
    e.error.asFailure()
} catch (_: BackupDecryptionException) {
    BackupError.WRONG_PASSWORD_OR_DAMAGED.asFailure()
} catch (_: java.util.zip.ZipException) {
    BackupError.NOT_A_BACKUP.asFailure()
} catch (_: IOException) {
    BackupError.WRITE_FAILED.asFailure()
}

private const val CLEARED = '\u0000'

/** After a successful restore the app restarts so the staged database replaces the old one (D-39). */
public class RestartAfterRestoreUseCase(private val restarter: AppRestarter) {
    public operator fun invoke() {
        restarter.restart()
    }
}

public object BackupFileNames {
    private val FORMAT: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyyMMdd_HHmm")
    private const val PREFIX = "RideComp_backup_"
    public const val EXTENSION: String = ".rcbackup"

    /** Shortest password accepted for an encrypted backup. */
    public const val MIN_PASSWORD_LENGTH: Int = 8

    /** `RideComp_backup_YYYYMMDD_HHMM.rcbackup` (12-FEATURE-SPECS §12.1). */
    public fun suggest(nowMs: Long, zone: ZoneId): String =
        PREFIX + FORMAT.format(Instant.ofEpochMilli(nowMs).atZone(zone)) + EXTENSION
}

public class SuggestBackupFileNameUseCase(private val clock: Clock, private val zone: ZoneId) {
    public operator fun invoke(): String = BackupFileNames.suggest(clock.nowMs(), zone)
}

public class ObserveLastBackupUseCase(private val state: BackupStateRepository) {
    public operator fun invoke(): Flow<Long?> = state.observeLastBackupAtMs()
}
