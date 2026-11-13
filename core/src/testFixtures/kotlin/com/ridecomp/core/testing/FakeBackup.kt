package com.ridecomp.core.testing

import com.ridecomp.core.engine.backup.BackupError
import com.ridecomp.core.source.AppRestarter
import com.ridecomp.core.usecase.BackupDatabase
import com.ridecomp.core.usecase.BackupFiles
import com.ridecomp.core.usecase.BackupStateRepository
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** "Database" = a text file; staging copies the candidate to [staged]. */
public class FakeBackupDatabase(private val root: File, override val schemaVersion: Int = 4) : BackupDatabase {
    public var content: String = "rides"
    public var staged: String? = null
    public var stageError: BackupError? = null

    override suspend fun snapshot(target: File) {
        target.writeText(content)
    }

    override suspend fun stageRestore(candidate: File): BackupError? {
        stageError?.let { return it }
        staged = candidate.readText()
        return null
    }
}

/** Documents are files named by their "uri" inside [root]. */
public class FakeBackupFiles(private val root: File) : BackupFiles {
    override val appVersion: String = "test"
    public val fitDir: File = File(root, "fit").apply { mkdirs() }
    public val restoredFit: MutableList<String> = mutableListOf()
    private var workDirs = 0

    override fun newWorkDir(): File = File(root, "work${workDirs++}").apply { mkdirs() }

    override fun exportedFitFiles(): List<File> = fitDir.listFiles().orEmpty().sortedBy { it.name }

    override fun restoreFitFiles(files: List<File>) {
        restoredFit += files.map { it.readText() }
    }

    override fun openOutput(uri: String): OutputStream = File(root, uri).outputStream()

    override fun openInput(uri: String): InputStream = File(root, uri).inputStream()

    override fun emergencyBackupFile(): File = File(root, "emergency.rcbackup")
}

public class FakeBackupStateRepository : BackupStateRepository {
    public val lastBackupAtMs: MutableStateFlow<Long?> = MutableStateFlow(null)
    public val countAtBackup: MutableStateFlow<Int> = MutableStateFlow(0)
    public val snoozedUntilMs: MutableStateFlow<Long?> = MutableStateFlow(null)

    override fun observeLastBackupAtMs(): Flow<Long?> = lastBackupAtMs

    override fun observeActivityCountAtBackup(): Flow<Int> = countAtBackup

    override fun observeReminderSnoozedUntilMs(): Flow<Long?> = snoozedUntilMs

    override suspend fun recordBackup(atMs: Long, activityCount: Int) {
        lastBackupAtMs.value = atMs
        countAtBackup.value = activityCount
    }

    override suspend fun snoozeReminder(untilMs: Long) {
        snoozedUntilMs.value = untilMs
    }
}

public class FakeAppRestarter : AppRestarter {
    public var restarts: Int = 0
        private set

    override fun restart() {
        restarts++
    }
}
