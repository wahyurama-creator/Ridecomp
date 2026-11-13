package com.ridecomp.core.usecase

import com.ridecomp.core.engine.backup.BackupError
import com.ridecomp.core.engine.recording.LiveRide
import com.ridecomp.core.model.RideStatus
import com.ridecomp.core.model.RideTotals
import com.ridecomp.core.repository.NewActivity
import com.ridecomp.core.testing.FakeActivityRepository
import com.ridecomp.core.testing.FakeBackupDatabase
import com.ridecomp.core.testing.FakeBackupFiles
import com.ridecomp.core.testing.FakeBackupManifestCodec
import com.ridecomp.core.testing.FakeBackupStateRepository
import com.ridecomp.core.testing.FakeClock
import com.ridecomp.core.testing.FakeRideController
import com.ridecomp.core.testing.RecordingLogger
import com.ridecomp.core.testing.TestDispatcherProvider
import com.ridecomp.core.util.Outcome
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest

class BackupUseCasesTest {
    private val root: File = Files.createTempDirectory("backup-uc").toFile()
    private val clock = FakeClock()
    private val activities = FakeActivityRepository()
    private val database = FakeBackupDatabase(root)
    private val files = FakeBackupFiles(root)
    private val state = FakeBackupStateRepository()
    private val controller = FakeRideController()
    private val codec = FakeBackupManifestCodec()
    private val writer = BackupWriter(database, files, activities, codec, clock)

    @AfterTest
    fun cleanUp() {
        root.deleteRecursively()
    }

    private suspend fun finishedRides(count: Int) = repeat(count) {
        val id = activities.create(NewActivity("u$it", "Ride", it.toLong()))
        activities.finish(id, "Ride", RideTotals(), it + 1L)
    }

    @Test
    fun `encrypted backup round trip restores the database and fit files`() = runTest {
        val dispatchers = TestDispatcherProvider(StandardTestDispatcher(testScheduler))
        finishedRides(2)
        File(files.fitDir, "Morning ride (1).fit").writeText("fit-a")
        val create = CreateBackupUseCase(writer, files, state, controller, dispatchers, RecordingLogger())

        assertEquals(Outcome.Success(2), create(DOC, PASSWORD.toCharArray()))
        assertEquals(clock.nowMs(), state.lastBackupAtMs.value)

        val read = ReadBackupSummaryUseCase(files, codec, dispatchers)
        assertTrue(read.isEncrypted(DOC))
        assertEquals(Outcome.Failure(BackupError.WRONG_PASSWORD_OR_DAMAGED), read(DOC, "wrong".toCharArray()))
        val summary = assertNotNull((read(DOC, PASSWORD.toCharArray()) as? Outcome.Success)?.value)
        assertEquals(2, summary.manifest.activityCount)

        database.content = "current data"
        val restore = RestoreBackupUseCase(writer, database, files, codec, controller, dispatchers)
        assertTrue(restore(DOC, PASSWORD.toCharArray()) is Outcome.Success)
        assertEquals("rides", database.staged)
        assertEquals(listOf("fit-a"), files.restoredFit)
        // An emergency copy of the data before the restore exists, and temp folders are gone (SEC-BK-04).
        assertTrue(files.emergencyBackupFile().length() > 0)
        assertTrue(
            root.listFiles().orEmpty().none {
                it.name.startsWith("work") && it.listFiles().orEmpty().isNotEmpty()
            },
        )
    }

    @Test
    fun `no backup or restore during a ride, newer schemas and bad databases are refused`() = runTest {
        val dispatchers = TestDispatcherProvider(StandardTestDispatcher(testScheduler))
        val create = CreateBackupUseCase(writer, files, state, controller, dispatchers, RecordingLogger())
        val restore = RestoreBackupUseCase(writer, database, files, codec, controller, dispatchers)
        assertTrue(create(DOC, null) is Outcome.Success)

        controller.live.value = LiveRide(status = RideStatus.RECORDING)
        assertEquals(Outcome.Failure(BackupError.RIDE_IN_PROGRESS), create(DOC, null))
        assertEquals(Outcome.Failure(BackupError.RIDE_IN_PROGRESS), restore(DOC, null))
        controller.live.value = LiveRide()

        val older =
            RestoreBackupUseCase(
                writer,
                FakeBackupDatabase(root, schemaVersion = 3),
                files,
                codec,
                controller,
                dispatchers,
            )
        assertEquals(Outcome.Failure(BackupError.NEWER_SCHEMA), older(DOC, null))

        database.stageError = BackupError.INVALID_DATABASE
        assertEquals(Outcome.Failure(BackupError.INVALID_DATABASE), restore(DOC, null))
        assertNull(database.staged)
    }

    @Test
    fun `reminder after 30 days and 5 new rides, snoozed for 7 days`() = runTest {
        val observe = ObserveBackupReminderUseCase(state, activities, clock)
        finishedRides(4)
        assertNull(observe().first())

        finishedRides(1)
        assertEquals(BackupReminder(daysSinceBackup = null, newActivities = 5), observe().first())

        state.recordBackup(clock.nowMs(), 5)
        finishedRides(5)
        assertNull(observe().first())
        clock.advanceBy((BackupReminderConfig.MIN_DAYS_SINCE_BACKUP + 1) * BackupReminderConfig.MS_PER_DAY)
        assertEquals(31, observe().first()?.daysSinceBackup)

        SnoozeBackupReminderUseCase(state, clock)()
        assertNull(observe().first())
        clock.advanceBy(BackupReminderConfig.SNOOZE_DAYS * BackupReminderConfig.MS_PER_DAY)
        assertNotNull(observe().first())
    }

    private companion object {
        const val DOC = "backup.rcbackup"
        const val PASSWORD = "kata-sandi-kuat"
    }
}
