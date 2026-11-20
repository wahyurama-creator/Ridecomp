package com.ridecomp.core.usecase

import com.ridecomp.core.engine.map.MapCacheLimit
import com.ridecomp.core.engine.map.MapTiles
import com.ridecomp.core.engine.map.OnlineMapMode
import com.ridecomp.core.engine.map.RideTrail
import com.ridecomp.core.engine.map.TrailConfig
import com.ridecomp.core.engine.map.allows
import com.ridecomp.core.repository.ActivityRepository
import com.ridecomp.core.repository.OfflineMapRepository
import com.ridecomp.core.repository.SettingsRepository
import com.ridecomp.core.source.MapCacheSource
import com.ridecomp.core.source.NetworkStatusSource
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * Which map to draw (D-90, T11.11): online tiles while there is internet and the setting allows it, else the
 * tiles cached by earlier online maps (also when the online map is off: no data is used) and the imported
 * offline regions, else none. The network is listened to only while collected, and not at all when the
 * online map is off. Online tiles mark the cache as filled.
 */
public class ObserveMapTilesUseCase(
    private val network: NetworkStatusSource,
    private val settings: SettingsRepository,
    private val offlineMaps: OfflineMapRepository,
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    public operator fun invoke(): Flow<MapTiles> {
        val isOnline = settings.observeRideSettings()
            .map { it.onlineMapMode }
            .distinctUntilChanged()
            .flatMapLatest { mode ->
                if (mode == OnlineMapMode.OFF) flowOf(false) else network.observe().map { mode.allows(it) }
            }
            .distinctUntilChanged()
        return combine(isOnline, offlineMaps.observeAll(), settings.observeMapCacheFilled()) { online, maps, filled ->
            if (online && !filled) settings.setMapCacheFilled(true)
            MapTiles(online, maps.map { it.path }, hasCache = filled || online)
        }.distinctUntilChanged()
    }
}

/**
 * Settings → Map (T11.11): the space the tile cache uses now, its limit, and whether it holds tiles (an empty
 * MapLibre database still takes some kB, so the size alone cannot tell).
 */
public data class MapCacheInfo(val sizeBytes: Long, val limit: MapCacheLimit, val hasTiles: Boolean)

public class GetMapCacheInfoUseCase(private val cache: MapCacheSource, private val settings: SettingsRepository) {
    public suspend operator fun invoke(): MapCacheInfo = MapCacheInfo(
        sizeBytes = cache.sizeBytes(),
        limit = settings.getRideSettings().mapCacheLimit,
        hasTiles = settings.observeMapCacheFilled().first(),
    )
}

/** Settings → Map: the new limit applies the next time a map is shown (old tiles go first). */
public class SetMapCacheLimitUseCase(private val settings: SettingsRepository) {
    public suspend operator fun invoke(limit: MapCacheLimit) {
        settings.saveRideSettings(settings.getRideSettings().copy(mapCacheLimit = limit))
    }
}

/** Settings → Map → "Clear map cache": no map from the cache until an online map is shown again. */
public class ClearMapCacheUseCase(private val cache: MapCacheSource, private val settings: SettingsRepository) {
    public suspend operator fun invoke() {
        cache.clear()
        settings.setMapCacheFilled(false)
    }
}

/** The ridden line so far of a ride without a route, read from its stored samples (recovery, reopened screen). */
public class LoadRideTrailUseCase(private val repository: ActivityRepository) {
    public suspend operator fun invoke(activityId: Long): RideTrail {
        val trail = RideTrail()
        var afterMs = -1L
        var hasMore = true
        while (hasMore) {
            val page = repository.getSamples(activityId, afterMs, TrailConfig.PAGE_SIZE)
            page.forEach { sample ->
                val lat = sample.latDeg
                val lon = sample.lonDeg
                if (lat != null && lon != null) trail.add(lat, lon)
            }
            hasMore = page.size == TrailConfig.PAGE_SIZE
            afterMs = page.lastOrNull()?.timeMs ?: afterMs
        }
        return trail
    }
}
