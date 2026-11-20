package com.ridecomp.core.usecase

import app.cash.turbine.test
import com.ridecomp.core.engine.map.MapCacheLimit
import com.ridecomp.core.engine.map.MapTiles
import com.ridecomp.core.engine.map.NetworkStatus
import com.ridecomp.core.engine.map.OnlineMapMode
import com.ridecomp.core.engine.map.RideTrail
import com.ridecomp.core.engine.map.TrailConfig
import com.ridecomp.core.model.RideSample
import com.ridecomp.core.repository.NewActivity
import com.ridecomp.core.source.MapCacheSource
import com.ridecomp.core.testing.FakeActivityRepository
import com.ridecomp.core.testing.FakeNetworkStatusSource
import com.ridecomp.core.testing.FakeOfflineMapRepository
import com.ridecomp.core.testing.FakeSettingsRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class MapUseCasesTest {
    private val network = FakeNetworkStatusSource(NetworkStatus(isConnected = true, isUnmetered = false))
    private val settings = FakeSettingsRepository()
    private val offline = FakeOfflineMapRepository()
    private val tiles = ObserveMapTilesUseCase(network, settings, offline)

    @Test
    fun `online on mobile data by default, then the cache of what was seen when the network goes`() = runTest {
        tiles().test {
            assertEquals(MapTiles(isOnline = true, regionPaths = emptyList(), hasCache = true), awaitItem())
            assertTrue(settings.mapCacheFilled.value)
            network.status.value = NetworkStatus.OFFLINE
            val offline = awaitItem()
            assertFalse(offline.isOnline)
            assertTrue(offline.hasCache)
            assertTrue(offline.usesOnlineTiles)
        }
        assertEquals(0, network.activeCollectors)
    }

    @Test
    fun `without an online map ever shown there is no map offline`() = runTest {
        network.status.value = NetworkStatus.OFFLINE

        tiles().test {
            assertEquals(MapTiles.NONE, awaitItem())
            assertFalse(settings.mapCacheFilled.value)
        }
    }

    @Test
    fun `off still shows the cache, clearing it ends that`() = runTest {
        settings.saveRideSettings(settings.getRideSettings().copy(onlineMapMode = OnlineMapMode.OFF))
        settings.setMapCacheFilled(true)
        val cache = object : MapCacheSource {
            var cleared = 0

            override suspend fun sizeBytes(): Long = if (cleared > 0) 0L else CACHE_BYTES

            override suspend fun clear() {
                cleared++
            }
        }

        tiles().test {
            assertEquals(MapTiles(isOnline = false, regionPaths = emptyList(), hasCache = true), awaitItem())
            assertEquals(0, network.activeCollectors)
            assertEquals(
                MapCacheInfo(CACHE_BYTES, MapCacheLimit.DEFAULT, hasTiles = true),
                GetMapCacheInfoUseCase(cache, settings)(),
            )

            ClearMapCacheUseCase(cache, settings)()

            assertEquals(MapTiles.NONE, awaitItem())
            assertEquals(1, cache.cleared)
        }
        assertFalse(GetMapCacheInfoUseCase(cache, settings)().hasTiles)
        assertEquals(150_000_000L, MapCacheLimit.MEDIUM.bytes)
        SetMapCacheLimitUseCase(settings)(MapCacheLimit.LARGE)
        assertEquals(MapCacheLimit.LARGE, settings.getRideSettings().mapCacheLimit)
    }

    @Test
    fun `wifi only waits for an unmetered network`() = runTest {
        settings.saveRideSettings(settings.getRideSettings().copy(onlineMapMode = OnlineMapMode.WIFI_ONLY))

        tiles().test {
            assertFalse(awaitItem().isOnline)
            network.status.value = NetworkStatus(isConnected = true, isUnmetered = true)
            assertTrue(awaitItem().isOnline)
        }
    }

    @Test
    fun `off never listens to the network and keeps offline regions`() = runTest {
        settings.saveRideSettings(settings.getRideSettings().copy(onlineMapMode = OnlineMapMode.OFF))
        offline.import("content://jateng", "jateng", Long.MAX_VALUE)

        tiles().test {
            val current = awaitItem()
            assertFalse(current.isOnline)
            assertEquals(listOf("/maps/jateng"), current.regionPaths)
            assertTrue(current.hasMap)
            assertEquals(0, network.activeCollectors)
        }
    }

    @Test
    fun `the trail skips points closer than the spacing`() {
        val trail = RideTrail()

        assertTrue(trail.add(-7.0, 110.0))
        // ~1 m further: ignored.
        assertFalse(trail.add(-7.00001, 110.0))
        // ~110 m further: added.
        assertTrue(trail.add(-7.001, 110.0))
        assertEquals(2, trail.snapshot.size)
    }

    @Test
    fun `a long trail is thinned and keeps the newest point`() {
        val trail = RideTrail()
        val stepDeg = 0.001
        repeat(TrailConfig.MAX_POINTS * 2) { trail.add(-7.0 + it * stepDeg, 110.0) }

        val points = trail.snapshot
        assertTrue(points.size <= TrailConfig.MAX_POINTS)
        assertEquals(-7.0 + (TrailConfig.MAX_POINTS * 2 - 1) * stepDeg, points.last().latDeg, DELTA)
        assertEquals(-7.0, points.first().latDeg, DELTA)
    }

    @Test
    fun `the trail is rebuilt from stored samples page by page`() = runTest {
        val activities = FakeActivityRepository()
        val id = activities.create(NewActivity("u", "Ride", 0))
        val samples = (0 until TrailConfig.PAGE_SIZE + 5).map { second ->
            RideSample(timeMs = second * 1_000L, latDeg = -7.0 + second * 0.001, lonDeg = 110.0)
        } + RideSample(timeMs = 9_999_000L)
        activities.appendSamples(id, samples)

        val trail = LoadRideTrailUseCase(activities)(id)

        assertEquals(TrailConfig.PAGE_SIZE + 5, trail.snapshot.size)
    }

    private companion object {
        const val CACHE_BYTES = 42_000_000L
        const val DELTA = 1e-9
    }
}
