package com.ridecomp.core.usecase

import com.ridecomp.core.engine.map.MapTiles
import com.ridecomp.core.engine.map.OnlineMapMode
import com.ridecomp.core.engine.map.RideTrail
import com.ridecomp.core.engine.map.TrailConfig
import com.ridecomp.core.engine.map.allows
import com.ridecomp.core.repository.ActivityRepository
import com.ridecomp.core.repository.OfflineMapRepository
import com.ridecomp.core.repository.SettingsRepository
import com.ridecomp.core.source.NetworkStatusSource
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * Which map to draw (D-90): online tiles while there is internet and the setting allows it, else the
 * imported offline regions, else none. The network is listened to only while collected, and not at all
 * when the online map is off.
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
        return combine(isOnline, offlineMaps.observeAll()) { online, maps -> MapTiles(online, maps.map { it.path }) }
            .distinctUntilChanged()
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
