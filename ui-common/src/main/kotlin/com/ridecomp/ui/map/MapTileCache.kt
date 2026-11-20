package com.ridecomp.ui.map

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import java.io.File
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.maplibre.android.MapLibre
import org.maplibre.android.offline.OfflineManager

/** Where a map's online tiles come from (T11.11): the network, or offline the cache of maps seen before. */
@Immutable
data class MapSource(val isOnline: Boolean = false, val isCached: Boolean = false) {
    val hasTiles: Boolean get() = isOnline || isCached
}

/**
 * Space the online map's tile cache may use (T11.11), provided by the app from Settings. MapLibre keeps only
 * this much: the least recently used tiles go first. Its own default (50 MB) holds until the app provides one.
 */
val LocalMapCacheLimitBytes = staticCompositionLocalOf<Long?> { null }

/**
 * MapLibre's tile cache (T11.11, D-128): the tiles of online maps already seen, read without the network
 * when offline. Not a bulk download: only what the rider looked at is kept.
 */
object MapTileCache {
    /** MapLibre's database in internal storage (its default place; RideComp never moves it). */
    private const val DATABASE = "mbgl-offline.db"

    /** The limit last given to MapLibre in this process; it is not stored, so it is set again per process. */
    @Volatile
    private var appliedLimitBytes: Long? = null

    /** Space used by the database and its journal files, in bytes (IO). */
    suspend fun sizeBytes(context: Context, io: CoroutineContext): Long = withContext(io) {
        context.filesDir.listFiles { file: File -> file.name.startsWith(DATABASE) }?.sumOf { it.length() } ?: 0L
    }

    /** Deletes every cached tile (RideComp keeps its offline regions as PMTiles files, not in this database). */
    suspend fun clear(context: Context, io: CoroutineContext) {
        MapEngine.loadLibrary(io)
        MapLibre.getInstance(context.applicationContext)
        appliedLimitBytes = null
        suspendCancellableCoroutine { continuation ->
            OfflineManager.getInstance(context.applicationContext).resetDatabase(resumeOnce(continuation))
        }
    }

    /** Gives MapLibre the limit once per process and when it changes; MapLibre must be ready (main thread). */
    internal suspend fun applyLimit(context: Context, limitBytes: Long) {
        if (appliedLimitBytes == limitBytes) return
        suspendCancellableCoroutine { continuation ->
            OfflineManager.getInstance(context.applicationContext)
                .setMaximumAmbientCacheSize(limitBytes, resumeOnce(continuation))
        }
        appliedLimitBytes = limitBytes
    }

    /** A failure leaves the cache as it was; the map still works, so it is not an error for the rider. */
    private fun resumeOnce(continuation: CancellableContinuation<Unit>) = object : OfflineManager.FileSourceCallback {
        override fun onSuccess() {
            if (continuation.isActive) continuation.resume(Unit)
        }

        override fun onError(message: String) {
            if (continuation.isActive) continuation.resume(Unit)
        }
    }
}
