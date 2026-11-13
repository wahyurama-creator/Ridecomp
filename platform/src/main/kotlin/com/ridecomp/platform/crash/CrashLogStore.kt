package com.ridecomp.platform.crash

import com.ridecomp.core.source.Clock
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Crash log limits and names (ERR-06), in one place. */
object CrashLogConfig {
    const val DIRECTORY_NAME = "crash_logs"
    const val MAX_FILES = 10
    const val FILE_PREFIX = "crash_"
    const val FILE_EXTENSION = ".txt"
    const val FILE_TIME_PATTERN = "yyyyMMdd_HHmmss_SSS"
    const val UTC_ZONE = "UTC"
    const val REDACTED = "<redacted>"

    /** Decimal numbers with ≥ 4 fraction digits look like coordinates; they are removed (SEC-STO-04). */
    val COORDINATE_PATTERN = Regex("""-?\d{1,3}\.\d{4,}""")
}

/**
 * Local crash log storage (ERR-06): one text file per crash in internal storage, newest [maxFiles] kept.
 * No third-party crash reporting. Content is sanitized so exported logs do not leak locations.
 */
class CrashLogStore(
    /** Resolved on first use: `Context.filesDir` touches the disk, and the store is built on the main thread. */
    directoryProvider: () -> File,
    private val clock: Clock,
    private val maxFiles: Int = CrashLogConfig.MAX_FILES,
) {
    private val directory: File by lazy(directoryProvider)

    /** Writes a crash report and removes the oldest files beyond [maxFiles]. Returns the new file. */
    fun write(threadName: String, throwable: Throwable): File {
        directory.mkdirs()
        val file = File(directory, fileName())
        file.writeText(sanitize(report(threadName, throwable)))
        rotate()
        return file
    }

    /** Stored logs, newest first. */
    fun list(): List<File> = directory.listFiles { file -> file.isCrashLog() }
        .orEmpty()
        .sortedByDescending { it.name }

    /** Deletes every stored log. */
    fun clear() {
        list().forEach { it.delete() }
    }

    private fun rotate() {
        list().drop(maxFiles).forEach { it.delete() }
    }

    private fun fileName(): String {
        val format = SimpleDateFormat(CrashLogConfig.FILE_TIME_PATTERN, Locale.ROOT).apply {
            timeZone = TimeZone.getTimeZone(CrashLogConfig.UTC_ZONE)
        }
        return CrashLogConfig.FILE_PREFIX + format.format(Date(clock.nowMs())) + CrashLogConfig.FILE_EXTENSION
    }

    private fun report(threadName: String, throwable: Throwable): String {
        val stackTrace = StringWriter().also { throwable.printStackTrace(PrintWriter(it)) }.toString()
        return buildString {
            appendLine("time_ms=${clock.nowMs()}")
            appendLine("thread=$threadName")
            append(stackTrace)
        }
    }

    private fun File.isCrashLog(): Boolean =
        isFile && name.startsWith(CrashLogConfig.FILE_PREFIX) && name.endsWith(CrashLogConfig.FILE_EXTENSION)

    companion object {
        /** Removes coordinate-like numbers from crash text. */
        fun sanitize(text: String): String = text.replace(CrashLogConfig.COORDINATE_PATTERN, CrashLogConfig.REDACTED)
    }
}
