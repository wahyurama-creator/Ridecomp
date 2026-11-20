package com.ridecomp.ui.map

import android.content.Context
import androidx.compose.runtime.staticCompositionLocalOf
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.withContext
import org.maplibre.android.LibraryLoader
import org.maplibre.android.MapLibre
import org.maplibre.android.maps.MapLibreMapOptions

/**
 * Where maps are prepared off the main thread (CO-03): the app provides its IO dispatcher. The default runs
 * in place, for previews and tests only.
 */
val LocalMapIoContext = staticCompositionLocalOf<CoroutineContext> { EmptyCoroutineContext }

/**
 * Prepares MapLibre before a map is created (T11.2a). The first map of the process loads the native library
 * (±300 ms of disk reads on the RMX3830, StrictMode); MapLibre remembers it is loaded, so later calls return
 * at once. Done only when a map is about to show, so screens without a map never load it (pemilik).
 */
internal object MapEngine {
    suspend fun loadLibrary(io: CoroutineContext) {
        withContext(io) { LibraryLoader.load() }
    }

    /**
     * The library, MapLibre itself (main thread only, cheap once the library is loaded), then the MapView
     * options, which read the theme's resources from disk (±8 ms each map). Call from the main thread.
     */
    suspend fun prepareView(context: Context, io: CoroutineContext): MapLibreMapOptions {
        loadLibrary(io)
        MapLibre.getInstance(context.applicationContext)
        MapHttp.install()
        return withContext(io) { MapLibreMapOptions.createFromAttributes(context) }
    }
}
