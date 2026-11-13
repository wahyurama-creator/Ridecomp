package com.ridecomp.core.testing

import com.ridecomp.core.engine.backup.BackupManifest
import com.ridecomp.core.engine.backup.BackupManifestCodec

/** Line-based codec for JVM tests (the app uses org.json in :platform). */
public class FakeBackupManifestCodec : BackupManifestCodec {
    override fun encode(manifest: BackupManifest): ByteArray = buildString {
        appendLine(manifest.appVersion)
        appendLine(manifest.dbSchemaVersion)
        appendLine(manifest.createdAtMs)
        appendLine(manifest.activityCount)
        appendLine(manifest.isEncrypted)
        manifest.checksums.forEach { (name, hash) -> appendLine("$name$SEPARATOR$hash") }
    }.toByteArray()

    override fun decode(bytes: ByteArray): BackupManifest? {
        val lines = bytes.decodeToString().lines().filter { it.isNotEmpty() }
        if (lines.size < HEADER_LINES) return null
        return BackupManifest(
            appVersion = lines[0],
            dbSchemaVersion = lines[1].toIntOrNull() ?: return null,
            createdAtMs = lines[2].toLongOrNull() ?: return null,
            activityCount = lines[3].toIntOrNull() ?: return null,
            isEncrypted = lines[4].toBooleanStrictOrNull() ?: return null,
            checksums = lines.drop(HEADER_LINES).associate {
                it.substringBefore(SEPARATOR) to
                    it.substringAfter(SEPARATOR)
            },
        )
    }

    private companion object {
        const val HEADER_LINES = 5
        const val SEPARATOR = "="
    }
}
