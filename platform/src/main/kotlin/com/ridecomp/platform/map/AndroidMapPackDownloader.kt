package com.ridecomp.platform.map

import android.net.TrafficStats
import com.ridecomp.core.engine.map.MapFileTransfer
import com.ridecomp.core.engine.map.MapPackError
import com.ridecomp.core.engine.map.MapPackFile
import com.ridecomp.core.engine.map.OfflineMapConfig
import com.ridecomp.core.source.Clock
import com.ridecomp.core.source.DispatcherProvider
import com.ridecomp.core.source.Logger
import com.ridecomp.core.source.MapPackDownloader
import com.ridecomp.platform.system.TrafficTags
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

/**
 * Region pack download over HTTPS from the project's GitHub release (T11.12, D-130): resumes `<name>.download`
 * with an HTTP Range request, caps the size, verifies the SHA-256 and then the PMTiles header (through the
 * store) before the file becomes an offline map. A failed checksum deletes the file; a broken connection keeps
 * it for the next try. Progress at most once a second. The socket is tagged (StrictMode untagged sockets).
 */
class AndroidMapPackDownloader(
    private val store: AndroidOfflineMapStore,
    private val clock: Clock,
    private val dispatchers: DispatcherProvider,
    private val logger: Logger,
) : MapPackDownloader {
    override fun download(file: MapPackFile): Flow<MapFileTransfer> = flow {
        if (!file.isValid) {
            emit(MapFileTransfer.Failed(MapPackError.DOWNLOAD_FAILED))
            return@flow
        }
        val partial = store.downloadFile(file.mapName)
        if (partial.length() > file.sizeBytes) partial.delete()
        val needed = file.sizeBytes - partial.length()
        if (store.allocatableBytes() - needed < OfflineMapConfig.MIN_FREE_AFTER_IMPORT_BYTES) {
            emit(MapFileTransfer.Failed(MapPackError.NO_SPACE))
            return@flow
        }
        val error = if (partial.length() == file.sizeBytes) null else fetch(file, partial) { emit(it) }
        when {
            error != null -> emit(MapFileTransfer.Failed(error))
            sha256(partial) != file.sha256 -> {
                partial.delete()
                emit(MapFileTransfer.Failed(MapPackError.CHECKSUM))
            }
            store.adopt(partial, file.mapName) != null -> {
                partial.delete()
                emit(MapFileTransfer.Failed(MapPackError.NOT_PMTILES))
            }
            else -> emit(MapFileTransfer.Done)
        }
    }.flowOn(dispatchers.io)

    override suspend fun partialBytes(file: MapPackFile): Long =
        withContext(dispatchers.io) { store.downloadFile(file.mapName).length() }

    override suspend fun discardPartial(file: MapPackFile) {
        withContext(dispatchers.io) { store.downloadFile(file.mapName).delete() }
    }

    /** Appends the rest of the file to [partial]; null when it is complete. */
    private suspend fun fetch(
        file: MapPackFile,
        partial: File,
        onProgress: suspend (MapFileTransfer.Progress) -> Unit,
    ): MapPackError? {
        TrafficStats.setThreadStatsTag(TrafficTags.MAP_PACKS)
        val connection = URL(file.url).openConnection() as HttpURLConnection
        return try {
            val start = partial.length()
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            if (start > 0) connection.setRequestProperty(RANGE, "bytes=$start-")
            val code = connection.responseCode
            // GitHub redirects to its file host; never leave HTTPS (SEC-NET-01 also forbids cleartext).
            val isHttps = connection.url.toString().startsWith(HTTPS)
            val from = if (code == HttpURLConnection.HTTP_PARTIAL) start else 0L
            // The length the server announces must match the index before anything is written (T11.14).
            val length = connection.contentLengthLong
            val isExpectedLength = length < 0 || length == file.sizeBytes - from
            if (code !in ACCEPTED_CODES || !isHttps || !isExpectedLength) {
                logger.info(TAG) { "Map pack replied $code, length $length" }
                MapPackError.DOWNLOAD_FAILED
            } else {
                copy(connection, partial, from, file.sizeBytes, onProgress)
            }
        } catch (error: IOException) {
            logger.warn(TAG, error) { "Map pack download interrupted; it resumes next time" }
            MapPackError.DOWNLOAD_FAILED
        } finally {
            connection.disconnect()
            TrafficStats.clearThreadStatsTag()
        }
    }

    /** Writes the response after [from] bytes; fails when the server sends more than [size]. */
    private suspend fun copy(
        connection: HttpURLConnection,
        partial: File,
        from: Long,
        size: Long,
        onProgress: suspend (MapFileTransfer.Progress) -> Unit,
    ): MapPackError? {
        val written = FileOutputStream(partial, from > 0).use { output ->
            connection.inputStream.use { input -> stream(input, output, from, size, onProgress) }
        }
        onProgress(MapFileTransfer.Progress(written))
        return MapPackError.DOWNLOAD_FAILED.takeIf { written != size }
    }

    /** Copies until the end or until more than [size] bytes would be written; returns the bytes written. */
    private suspend fun stream(
        input: InputStream,
        output: OutputStream,
        from: Long,
        size: Long,
        onProgress: suspend (MapFileTransfer.Progress) -> Unit,
    ): Long {
        var written = from
        var reportedAtMs = clock.monotonicMs()
        val buffer = ByteArray(BUFFER_BYTES)
        var read = input.read(buffer)
        while (read >= 0) {
            currentCoroutineContext().ensureActive()
            // Larger than the index says: stop; the size check above fails the download.
            if (written + read > size) return written + read
            output.write(buffer, 0, read)
            written += read
            reportedAtMs = report(written, reportedAtMs, onProgress)
            read = input.read(buffer)
        }
        return written
    }

    /** Progress at most once a second (updates ≤ 1 Hz, CLAUDE.md); returns when it was last reported. */
    private suspend fun report(
        written: Long,
        reportedAtMs: Long,
        onProgress: suspend (MapFileTransfer.Progress) -> Unit,
    ): Long {
        val nowMs = clock.monotonicMs()
        if (nowMs - reportedAtMs < PROGRESS_INTERVAL_MS) return reportedAtMs
        onProgress(MapFileTransfer.Progress(written))
        return nowMs
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance(SHA_256)
        file.inputStream().use { input ->
            val buffer = ByteArray(BUFFER_BYTES)
            var read = input.read(buffer)
            while (read >= 0) {
                digest.update(buffer, 0, read)
                read = input.read(buffer)
            }
        }
        return digest.digest().joinToString("") { HEX_FORMAT.format(it) }
    }

    private companion object {
        const val TAG = "MapPacks"
        const val RANGE = "Range"
        const val HTTPS = "https://"
        const val SHA_256 = "SHA-256"
        const val HEX_FORMAT = "%02x"
        const val CONNECT_TIMEOUT_MS = 15_000
        const val READ_TIMEOUT_MS = 30_000
        const val BUFFER_BYTES = 64 * 1024
        const val PROGRESS_INTERVAL_MS = 1_000L
        val ACCEPTED_CODES = setOf(HttpURLConnection.HTTP_OK, HttpURLConnection.HTTP_PARTIAL)
    }
}
