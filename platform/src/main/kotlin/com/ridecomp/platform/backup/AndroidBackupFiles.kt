package com.ridecomp.platform.backup

import android.content.Context
import android.content.pm.PackageManager
import androidx.core.net.toUri
import com.ridecomp.core.usecase.BackupFiles
import com.ridecomp.platform.export.ExportPaths
import java.io.File
import java.io.FileNotFoundException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

/**
 * Files of backup & restore (12-FEATURE-SPECS §12): work folders in internal cache, deleted by the use
 * cases; documents only where the rider chose them with SAF (SEC-BK-04); the pre-restore safety copy in
 * internal storage (SEC-STO-01).
 */
class AndroidBackupFiles(private val context: Context) : BackupFiles {
    private val exportDir = File(context.filesDir, ExportPaths.EXPORT_DIRECTORY)

    override val appVersion: String by lazy {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
        } catch (_: PackageManager.NameNotFoundException) {
            ""
        }
    }

    override fun newWorkDir(): File =
        File(File(context.cacheDir, WORK_DIRECTORY), UUID.randomUUID().toString()).apply { mkdirs() }

    override fun exportedFitFiles(): List<File> =
        exportDir.listFiles { file -> file.isFile && file.extension.equals(FIT_EXTENSION, ignoreCase = true) }
            .orEmpty()
            .sortedBy { it.name }

    override fun restoreFitFiles(files: List<File>) {
        exportDir.mkdirs()
        files.filter { it.isFile }.forEach { it.copyTo(File(exportDir, it.name), overwrite = true) }
    }

    override fun openOutput(uri: String): OutputStream =
        context.contentResolver.openOutputStream(uri.toUri(), WRITE_TRUNCATE)
            ?: throw FileNotFoundException("Document not writable")

    override fun openInput(uri: String): InputStream =
        context.contentResolver.openInputStream(uri.toUri()) ?: throw FileNotFoundException("Document not readable")

    override fun emergencyBackupFile(): File =
        File(File(context.filesDir, BACKUP_DIRECTORY).apply { mkdirs() }, EMERGENCY_FILE)

    private companion object {
        const val WORK_DIRECTORY = "backup-work"
        const val BACKUP_DIRECTORY = "backups"
        const val EMERGENCY_FILE = "before-restore.rcbackup"
        const val FIT_EXTENSION = "fit"

        /** "wt": replace the document content instead of writing over its beginning. */
        const val WRITE_TRUNCATE = "wt"
    }
}
