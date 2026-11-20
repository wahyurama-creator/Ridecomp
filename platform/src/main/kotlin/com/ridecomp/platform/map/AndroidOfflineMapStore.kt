package com.ridecomp.platform.map

import android.content.ContentResolver
import android.content.Context
import android.os.storage.StorageManager
import android.provider.OpenableColumns
import androidx.core.net.toUri
import com.ridecomp.core.engine.map.MapImportError
import com.ridecomp.core.engine.map.OfflineMap
import com.ridecomp.core.engine.map.OfflineMapConfig
import com.ridecomp.core.engine.map.PmTilesHeader
import com.ridecomp.core.repository.OfflineMapRepository
import com.ridecomp.core.source.DispatcherProvider
import com.ridecomp.core.source.Logger
import com.ridecomp.core.util.Outcome
import com.ridecomp.core.util.asFailure
import com.ridecomp.core.util.asSuccess
import com.ridecomp.core.util.getOrNull
import java.io.File
import java.io.IOException
import java.io.InputStream
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.withContext

/**
 * Offline map files in `files/maps` (internal storage, SEC-STO-01, excluded from backups by size,
 * 12-FEATURE-SPECS §12.1). An import is copied to a temporary file and renamed only when complete, so a
 * half-copied region never reaches the map renderer.
 */
class AndroidOfflineMapStore(
    private val context: Context,
    private val dispatchers: DispatcherProvider,
    private val logger: Logger,
) : OfflineMapRepository {
    private val maps = MutableStateFlow<List<OfflineMap>>(emptyList())

    @Volatile
    private var isLoaded = false

    /** An import is copying: its `.part` file is not a leftover (T11.14). */
    @Volatile
    private var isImporting = false

    // Resolved on the IO thread: `filesDir` touches the disk (STB-01).
    private val directory by lazy { File(context.filesDir, MAP_DIRECTORY).apply { mkdirs() } }

    override fun observeAll(): Flow<List<OfflineMap>> = maps.onStart { if (!isLoaded) refresh() }

    override suspend fun import(uri: String, name: String, maxBytes: Long): Outcome<OfflineMap, MapImportError> =
        withContext(dispatchers.io) {
            val parsed = uri.toUri()
            if (parsed.scheme !=
                ContentResolver.SCHEME_CONTENT
            ) {
                return@withContext MapImportError.READ_FAILED.asFailure()
            }
            val size = documentSize(uri)
            when {
                size != null && size > OfflineMapConfig.MAX_FILE_BYTES -> MapImportError.TOO_LARGE.asFailure()
                size != null && size > maxBytes -> MapImportError.TOTAL_LIMIT.asFailure()
                size != null && allocatableBytes() - size < OfflineMapConfig.MIN_FREE_AFTER_IMPORT_BYTES ->
                    MapImportError.NO_SPACE.asFailure()
                else -> copy(uri, name, maxBytes)
            }.also { refresh() }
        }

    override suspend fun cleanTemporary(keepDownloads: Set<String>) {
        withContext(dispatchers.io) {
            directory.listFiles().orEmpty().forEach { file ->
                val isStaleImport = file.name.endsWith(TEMPORARY_SUFFIX) && !isImporting
                val isStaleDownload = file.name.endsWith(DOWNLOAD_SUFFIX) &&
                    file.name.removeSuffix(DOWNLOAD_SUFFIX) !in keepDownloads
                if (isStaleImport || isStaleDownload) file.delete()
            }
        }
    }

    private fun copy(uri: String, name: String, maxBytes: Long): Outcome<OfflineMap, MapImportError> {
        val target = File(directory, name + OfflineMapConfig.FILE_EXTENSION)
        val temporary = File(directory, name + TEMPORARY_SUFFIX)
        isImporting = true
        val result = try {
            context.contentResolver.openInputStream(uri.toUri())?.use { input ->
                copyValidated(input, temporary, target, maxBytes)
            }
                ?: MapImportError.READ_FAILED.asFailure()
        } catch (error: IOException) {
            logger.warn(TAG, error) { "Offline map import failed" }
            MapImportError.READ_FAILED.asFailure()
        } catch (error: SecurityException) {
            logger.warn(TAG, error) { "Offline map document not readable" }
            MapImportError.READ_FAILED.asFailure()
        }
        temporary.delete()
        isImporting = false
        return result
    }

    private fun copyValidated(
        input: InputStream,
        temporary: File,
        target: File,
        maxBytes: Long,
    ): Outcome<OfflineMap, MapImportError> {
        val header = input.readNBytesCompat(PmTilesHeader.SIZE_BYTES)
        val error = (PmTilesHeader.parse(header) as? Outcome.Failure)?.error
            ?: copyRest(header, input, temporary, maxBytes)
            ?: MapImportError.READ_FAILED.takeUnless { temporary.renameTo(target) }
        return error?.asFailure() ?: read(target)?.asSuccess() ?: MapImportError.READ_FAILED.asFailure()
    }

    /**
     * Copies the header and the rest of the stream; stops at [OfflineMapConfig.MAX_FILE_BYTES] or at
     * [maxBytes], the room under the total limit, for documents that did not tell their size.
     */
    private fun copyRest(header: ByteArray, input: InputStream, temporary: File, maxBytes: Long): MapImportError? =
        temporary.outputStream().use { output ->
            output.write(header)
            val cap = minOf(OfflineMapConfig.MAX_FILE_BYTES, maxBytes)
            var copied = header.size.toLong()
            val buffer = ByteArray(COPY_BUFFER_BYTES)
            var read = input.read(buffer)
            while (read >= 0 && copied + read <= cap) {
                output.write(buffer, 0, read)
                copied += read
                read = input.read(buffer)
            }
            when {
                read < 0 -> null
                cap < OfflineMapConfig.MAX_FILE_BYTES -> MapImportError.TOTAL_LIMIT
                else -> MapImportError.TOO_LARGE
            }
        }

    override suspend fun delete(name: String) {
        withContext(dispatchers.io) {
            File(directory, name + OfflineMapConfig.FILE_EXTENSION).delete()
            refresh()
        }
    }

    override suspend fun freeBytes(): Long = withContext(dispatchers.io) { allocatableBytes() }

    /** Where a pack download writes its partial file, next to the maps (T11.12). Call on IO. */
    internal fun downloadFile(name: String): File = File(directory, name + DOWNLOAD_SUFFIX)

    /**
     * Adds a complete, checksum-verified pack download as map [name] (T11.12): the PMTiles header is checked
     * like an import, then the file is renamed into place. Null when added.
     */
    internal suspend fun adopt(file: File, name: String): MapImportError? = withContext(dispatchers.io) {
        val header = try {
            file.inputStream().use { it.readNBytesCompat(PmTilesHeader.SIZE_BYTES) }
        } catch (error: IOException) {
            logger.warn(TAG, error) { "Downloaded map unreadable" }
            return@withContext MapImportError.READ_FAILED
        }
        val error = (PmTilesHeader.parse(header) as? Outcome.Failure)?.error
            ?: MapImportError.READ_FAILED.takeUnless {
                file.renameTo(File(directory, name + OfflineMapConfig.FILE_EXTENSION))
            }
        refresh()
        error
    }

    /** Free space including cache the system can clear for us (lint UsableSpace). Call on IO. */
    internal fun allocatableBytes(): Long {
        val storage = context.getSystemService(StorageManager::class.java)
        return storage.getAllocatableBytes(storage.getUuidForPath(directory))
    }

    /** A content provider query: on the IO thread, never the main one (the import starts from a screen). */
    override suspend fun displayName(uri: String): String? = withContext(dispatchers.io) {
        val parsed = uri.toUri()
        if (parsed.scheme != ContentResolver.SCHEME_CONTENT) return@withContext null
        context.contentResolver.query(parsed, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
    }

    private fun documentSize(uri: String): Long? =
        context.contentResolver.query(uri.toUri(), arrayOf(OpenableColumns.SIZE), null, null, null)
            ?.use { cursor -> if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0) else null }

    private suspend fun refresh() {
        maps.value = withContext(dispatchers.io) {
            directory.listFiles { file -> file.name.endsWith(OfflineMapConfig.FILE_EXTENSION) }
                .orEmpty()
                .mapNotNull(::read)
                .sortedBy { it.name }
        }
        isLoaded = true
    }

    /** A stored file whose header no longer parses (damaged) is left out, never shown to the renderer. */
    private fun read(file: File): OfflineMap? = try {
        val header = file.inputStream().use { it.readNBytesCompat(PmTilesHeader.SIZE_BYTES) }
        PmTilesHeader.parse(header).getOrNull()?.let { info ->
            OfflineMap(
                name = file.name.removeSuffix(OfflineMapConfig.FILE_EXTENSION),
                sizeBytes = file.length(),
                info = info,
                importedAtMs = file.lastModified(),
                path = file.path,
            )
        }
    } catch (error: IOException) {
        logger.warn(TAG, error) { "Offline map file unreadable, skipped" }
        null
    }

    private companion object {
        const val TAG = "OfflineMaps"
        const val MAP_DIRECTORY = "maps"
        const val TEMPORARY_SUFFIX = ".part"

        /** A pack download in progress; kept across app starts so it can resume (T11.12). */
        const val DOWNLOAD_SUFFIX = ".download"
        const val COPY_BUFFER_BYTES = 64 * 1024
    }
}

/** Reads up to [count] bytes (InputStream.readNBytes needs API 33). */
private fun InputStream.readNBytesCompat(count: Int): ByteArray {
    val bytes = ByteArray(count)
    var total = 0
    while (total < count) {
        val read = read(bytes, total, count - total)
        if (read < 0) break
        total += read
    }
    return bytes.copyOf(total)
}
