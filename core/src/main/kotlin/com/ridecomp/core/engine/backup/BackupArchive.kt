package com.ridecomp.core.engine.backup

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Archive layout and safety limits (12-FEATURE-SPECS §12.1 and §12.6, SEC-BK-03). */
public object BackupArchiveConfig {
    public const val MANIFEST: String = "manifest.json"
    public const val DATABASE: String = "db/ridecomp.db"
    public const val FIT_DIR: String = "fit/"
    public const val ROUTES_DIR: String = "routes/"

    /** Only these names can be extracted: no `..`, no absolute paths, no subfolders (zip slip). */
    public val ALLOWED_NAME: Regex =
        Regex("""^(manifest\.json|db/ridecomp\.db|fit/[A-Za-z0-9_\-]{1,100}\.fit|routes/[A-Za-z0-9_\-]{1,100}\.gpx)$""")

    public const val MAX_MANIFEST_BYTES: Long = 64L * 1024
    public const val MAX_ENTRY_BYTES: Long = 1L * 1024 * 1024 * 1024
    public const val MAX_TOTAL_BYTES: Long = 2L * 1024 * 1024 * 1024
    public const val MAX_ENTRIES: Int = 20_000
    public const val DIGEST: String = "SHA-256"
    public const val COPY_BUFFER_BYTES: Int = 64 * 1024
}

/** `manifest.json` (12-FEATURE-SPECS §12.1). */
public data class BackupManifest(
    val appVersion: String,
    val dbSchemaVersion: Int,
    val createdAtMs: Long,
    val activityCount: Int,
    val isEncrypted: Boolean,
    /** Entry name → lowercase hex SHA-256. */
    val checksums: Map<String, String>,
)

/** JSON encoding lives with the platform JSON library (decision D-26). */
public interface BackupManifestCodec {
    public fun encode(manifest: BackupManifest): ByteArray

    /** Null when the bytes are not a valid manifest (untrusted input, SEC-IN-01). */
    public fun decode(bytes: ByteArray): BackupManifest?
}

public enum class BackupError {
    WRONG_PASSWORD_OR_DAMAGED,
    NOT_A_BACKUP,
    UNSAFE_ENTRY,
    TOO_LARGE,
    CHECKSUM_MISMATCH,
    MISSING_ENTRY,
    NEWER_SCHEMA,
    INVALID_DATABASE,
    RIDE_IN_PROGRESS,
    WRITE_FAILED,
}

/** Thrown inside archive code and turned into a [BackupError] by the use cases. */
public class BackupArchiveException(public val error: BackupError, message: String = error.name) : IOException(message)

/** One file to put in the archive. */
public data class BackupEntrySource(val name: String, val file: File)

/**
 * Writes a backup ZIP in one streaming pass: checksums are computed first from the local files, so the
 * manifest can be the first entry (a summary is readable without the rest of the file).
 */
public class BackupArchiveWriter(private val codec: BackupManifestCodec) {
    public fun write(output: OutputStream, manifest: BackupManifest, entries: List<BackupEntrySource>) {
        val checksums = entries.associate { it.name to sha256(it.file) }
        ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry(BackupArchiveConfig.MANIFEST))
            zip.write(codec.encode(manifest.copy(checksums = checksums)))
            zip.closeEntry()
            entries.forEach { entry ->
                zip.putNextEntry(ZipEntry(entry.name))
                entry.file.inputStream().use { it.copyTo(zip, BackupArchiveConfig.COPY_BUFFER_BYTES) }
                zip.closeEntry()
            }
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance(BackupArchiveConfig.DIGEST)
        file.inputStream().use { input ->
            val buffer = ByteArray(BackupArchiveConfig.COPY_BUFFER_BYTES)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().toHex()
    }
}

/**
 * Reads a backup ZIP as untrusted input (SEC-BK-03): the manifest must come first; every other entry must
 * have an allowed name, appear once, stay within the size limits and match its checksum; entries listed in
 * the manifest must all be present. Extraction goes to [targetDir] only.
 */
public class BackupArchiveReader(private val codec: BackupManifestCodec) {
    /** Reads just the manifest (first entry) for the restore summary. */
    public fun readManifest(input: InputStream): BackupManifest = ZipInputStream(input).use { zip -> manifestOf(zip) }

    /** Extracts every entry into [targetDir] and returns the verified manifest. */
    public fun extract(input: InputStream, targetDir: File): BackupManifest = ZipInputStream(input).use { zip ->
        val manifest = manifestOf(zip)
        val seen = mutableSetOf<String>()
        var totalBytes = 0L
        while (true) {
            val entry = zip.nextEntry ?: break
            val name = entry.name
            if (entry.isDirectory || !BackupArchiveConfig.ALLOWED_NAME.matches(name) || !seen.add(name)) {
                fail(BackupError.UNSAFE_ENTRY)
            }
            if (seen.size > BackupArchiveConfig.MAX_ENTRIES) fail(BackupError.TOO_LARGE)
            val expected = manifest.checksums[name] ?: fail(BackupError.UNSAFE_ENTRY)
            val target = File(targetDir, name).also { it.parentFile?.mkdirs() }
            val (bytes, checksum) = copyLimited(zip, target, BackupArchiveConfig.MAX_TOTAL_BYTES - totalBytes)
            totalBytes += bytes
            if (checksum != expected) fail(BackupError.CHECKSUM_MISMATCH)
        }
        if (seen != manifest.checksums.keys) fail(BackupError.MISSING_ENTRY)
        manifest
    }

    private fun manifestOf(zip: ZipInputStream): BackupManifest {
        val first = zip.nextEntry ?: fail(BackupError.NOT_A_BACKUP)
        if (first.name != BackupArchiveConfig.MANIFEST) fail(BackupError.NOT_A_BACKUP)
        val bytes = zip.readLimited(BackupArchiveConfig.MAX_MANIFEST_BYTES)
        val manifest = codec.decode(bytes) ?: fail(BackupError.NOT_A_BACKUP)
        if (manifest.checksums.keys.any { !BackupArchiveConfig.ALLOWED_NAME.matches(it) }) {
            fail(BackupError.UNSAFE_ENTRY)
        }
        return manifest
    }

    /** Copies one entry, stopping as soon as a size limit is passed (zip bomb). */
    private fun copyLimited(zip: ZipInputStream, target: File, remainingTotal: Long): Pair<Long, String> {
        val limit = minOf(BackupArchiveConfig.MAX_ENTRY_BYTES, remainingTotal)
        val digest = MessageDigest.getInstance(BackupArchiveConfig.DIGEST)
        var written = 0L
        FileOutputStream(target).use { out ->
            val buffer = ByteArray(BackupArchiveConfig.COPY_BUFFER_BYTES)
            while (true) {
                val read = zip.read(buffer)
                if (read < 0) break
                written += read
                if (written > limit) fail(BackupError.TOO_LARGE)
                digest.update(buffer, 0, read)
                out.write(buffer, 0, read)
            }
        }
        return written to digest.digest().toHex()
    }

    private fun ZipInputStream.readLimited(limit: Long): ByteArray {
        val bytes = readNBytesCompat(limit + 1)
        if (bytes.size > limit) fail(BackupError.TOO_LARGE)
        return bytes
    }

    private fun InputStream.readNBytesCompat(max: Long): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(BackupArchiveConfig.COPY_BUFFER_BYTES)
        while (out.size() < max) {
            val read = read(buffer, 0, minOf(buffer.size.toLong(), max - out.size()).toInt())
            if (read < 0) break
            out.write(buffer, 0, read)
        }
        return out.toByteArray()
    }
}

private const val HEX_DIGITS = "0123456789abcdef"
private const val NIBBLE_BITS = 4
private const val NIBBLE_MASK = 0x0F

private fun ByteArray.toHex(): String = buildString(size * 2) {
    this@toHex.forEach { byte ->
        val value = byte.toInt()
        append(HEX_DIGITS[(value shr NIBBLE_BITS) and NIBBLE_MASK])
        append(HEX_DIGITS[value and NIBBLE_MASK])
    }
}

/** Stops reading the archive with [error] (one place for the archive failure). */
private fun fail(error: BackupError): Nothing = throw BackupArchiveException(error)
