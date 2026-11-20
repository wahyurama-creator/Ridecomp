package com.ridecomp.app.map

import android.content.Context
import com.ridecomp.core.source.DispatcherProvider
import com.ridecomp.core.source.MapCacheSource
import com.ridecomp.ui.map.MapTileCache
import kotlinx.coroutines.withContext

/**
 * The tile cache through MapLibre (T11.11). In `:app` because MapLibre is a dependency of `:ui-common`
 * only (no new module edge, D-128). Clearing prepares MapLibre on the main thread, its library on IO.
 */
class AndroidMapCacheSource(private val context: Context, private val dispatchers: DispatcherProvider) :
    MapCacheSource {
    override suspend fun sizeBytes(): Long = MapTileCache.sizeBytes(context, dispatchers.io)

    override suspend fun clear() {
        withContext(dispatchers.main) { MapTileCache.clear(context, dispatchers.io) }
    }
}
