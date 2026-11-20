package com.ridecomp.core.usecase

import com.ridecomp.core.engine.map.MapBounds
import com.ridecomp.core.engine.map.MapDownload
import com.ridecomp.core.engine.map.MapFileTransfer
import com.ridecomp.core.engine.map.MapPackError
import com.ridecomp.core.engine.map.MapPackFile
import com.ridecomp.core.engine.map.MapRegion
import com.ridecomp.core.engine.map.MapRegionIndex
import com.ridecomp.core.engine.map.MapRegionStatus
import com.ridecomp.core.engine.map.NetworkStatus
import com.ridecomp.core.engine.map.TrailPoint
import com.ridecomp.core.testing.FakeMapPackDownloader
import com.ridecomp.core.testing.FakeMapPackStateRepository
import com.ridecomp.core.testing.FakeMapRegionIndexSource
import com.ridecomp.core.testing.FakeNetworkStatusSource
import com.ridecomp.core.testing.FakeOfflineMapRepository
import com.ridecomp.core.testing.FakeRideController
import com.ridecomp.core.testing.FakeRidePositionRepository
import com.ridecomp.core.testing.FakeSettingsRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest

/** Province map packs (T11.12): index checks, the region here, the offer and downloads. */
@OptIn(ExperimentalCoroutinesApi::class)
class MapPackUseCasesTest {
    private val maps = FakeOfflineMapRepository()
    private val settings = FakeSettingsRepository()
    private val state = FakeMapPackStateRepository()
    private val network = FakeNetworkStatusSource(NetworkStatus(isConnected = true, isUnmetered = true))
    private val rides = FakeRideController()
    private val positions = FakeRidePositionRepository()
    private val downloader = FakeMapPackDownloader(maps)
    private val index = FakeMapRegionIndexSource(MapRegionIndex(1, VERSION, listOf(JATENG, DIY)))

    private fun TestScope.downloads() =
        MapPackDownloads(downloader, state, network, rides, MapLimitCheck(maps, settings), this)

    private fun TestScope.regions(downloads: MapPackDownloads = downloads()) =
        ObserveMapRegionsUseCase(LoadMapRegionIndexUseCase(index), maps, state, positions, downloads)

    @Test
    fun `the index keeps only valid files from our releases and a known schema`() {
        val bad = DIY.copy(id = "evil", files = listOf(DIY_FILE.copy(url = "https://example.com/x.pmtiles")))
        val wrongHash = DIY.copy(id = "hash", files = listOf(DIY_FILE.copy(sha256 = "abc")))
        val badName = DIY.copy(id = "name", files = listOf(DIY_FILE.copy(name = "../x.pmtiles")))

        val checked = MapRegionIndex(1, VERSION, listOf(DIY, bad, wrongHash, badName)).validated()

        assertEquals(listOf("di-yogyakarta"), checked.regions.map { it.id })
        assertTrue(MapRegionIndex(2, VERSION, listOf(DIY)).validated().regions.isEmpty())
    }

    @Test
    fun `the region here is the smallest box holding the last ride's end`() {
        // Yogyakarta lies inside the wide Central Java box too.
        assertEquals("di-yogyakarta", MapRegion.at(listOf(JATENG, DIY), -7.8, 110.37)?.id)
        assertEquals("jawa-tengah", MapRegion.at(listOf(JATENG, DIY), -7.0, 110.4)?.id)
        assertNull(MapRegion.at(listOf(JATENG, DIY), -6.2, 106.8))
    }

    @Test
    fun `the offer follows the last ride, waits for no ride and stops after later`() = runTest {
        val offers = ObserveMapRegionOfferUseCase(regions(), state, rides)
        assertNull(offers().first())

        positions.lastPosition.value = TrailPoint(-7.8, 110.37)
        val offer = offers().first()
        assertEquals("di-yogyakarta", offer?.state?.region?.id)
        assertEquals(MapRegionStatus.NOT_INSTALLED, offer?.state?.status)

        rides.activityId.value = 1
        assertNull(offers().first())
        rides.activityId.value = null

        DismissMapRegionOfferUseCase(state)(requireNotNull(offer))
        assertNull(offers().first())
        // Riding elsewhere offers that region.
        positions.lastPosition.value = TrailPoint(-7.0, 110.4)
        assertEquals("jawa-tengah", offers().first()?.state?.region?.id)
    }

    @Test
    fun `a download installs the region, a new index version offers an update`() = runTest {
        val downloads = downloads()
        downloads.start(DIY, VERSION)
        advanceUntilIdle()

        assertEquals(VERSION, state.installed.value["di-yogyakarta"])
        val installed = regions(downloads)().first().regions.first { it.region.id == "di-yogyakarta" }
        assertEquals(MapRegionStatus.INSTALLED, installed.status)
        assertNull(installed.download)

        index.index = MapRegionIndex(1, "maps-2027-02", listOf(DIY))
        val outdated = regions(downloads)().first().regions.single()
        assertEquals(MapRegionStatus.UPDATE_AVAILABLE, outdated.status)

        DeleteMapRegionUseCase(maps, state)(DIY)
        assertEquals(MapRegionStatus.NOT_INSTALLED, regions(downloads)().first().regions.single().status)
        assertTrue(state.installed.value.isEmpty())
    }

    @Test
    fun `leaving the app pauses, the partial file shows after a restart, cancel deletes it`() = runTest {
        downloader.gate = CompletableDeferred()
        val downloads = downloads()
        downloads.start(DIY, VERSION)
        advanceUntilIdle()
        assertIs<MapDownload.Running>(downloads.downloads.value["di-yogyakarta"])

        downloads.pause()
        advanceUntilIdle()
        assertEquals(MapDownload.Paused(HALF, SIZE), downloads.downloads.value["di-yogyakarta"])

        // A new process: the partial file is found again.
        val restarted = downloads()
        restarted.restorePaused(listOf(DIY, JATENG))
        assertEquals(MapDownload.Paused(HALF, SIZE), restarted.downloads.value["di-yogyakarta"])
        assertNull(restarted.downloads.value["jawa-tengah"])

        restarted.cancel(DIY)
        advanceUntilIdle()
        assertNull(restarted.downloads.value["di-yogyakarta"])
        assertTrue(downloader.partial.isEmpty())
    }

    @Test
    fun `a ride starting pauses the download`() = runTest {
        downloader.gate = CompletableDeferred()
        val downloads = downloads()
        downloads.start(DIY, VERSION)
        advanceUntilIdle()

        rides.activityId.value = 7
        advanceUntilIdle()

        assertIs<MapDownload.Paused>(downloads.downloads.value["di-yogyakarta"])
    }

    @Test
    fun `mobile data needs permission, a ride or no network blocks, a bad file fails`() = runTest {
        val downloads = downloads()
        network.status.value = NetworkStatus(isConnected = true, isUnmetered = false)
        downloads.start(DIY, VERSION)
        advanceUntilIdle()
        assertEquals(MapDownload.Failed(MapPackError.NEEDS_WIFI), downloads.downloads.value["di-yogyakarta"])

        state.mobileData.value = true
        downloader.failure = MapFileTransfer.Failed(MapPackError.CHECKSUM)
        downloads.start(DIY, VERSION)
        advanceUntilIdle()
        assertEquals(MapDownload.Failed(MapPackError.CHECKSUM), downloads.downloads.value["di-yogyakarta"])
        assertTrue(state.installed.value.isEmpty())

        network.status.value = NetworkStatus.OFFLINE
        downloads.start(DIY, VERSION)
        advanceUntilIdle()
        assertEquals(MapDownload.Failed(MapPackError.NO_NETWORK), downloads.downloads.value["di-yogyakarta"])

        network.status.value = NetworkStatus(isConnected = true, isUnmetered = true)
        rides.activityId.value = 3
        downloads.start(DIY, VERSION)
        advanceUntilIdle()
        assertEquals(MapDownload.Failed(MapPackError.RIDING), downloads.downloads.value["di-yogyakarta"])
    }

    @Test
    fun `a region that does not fit the total limit is refused, stale partial files are cleaned`() = runTest {
        maps.importSize = 990_000_000L
        maps.import("content://x", "jatim", Long.MAX_VALUE)
        val downloads = downloads()

        downloads.start(DIY, VERSION)
        advanceUntilIdle()
        assertEquals(MapDownload.Failed(MapPackError.TOTAL_LIMIT), downloads.downloads.value["di-yogyakarta"])

        regions(downloads)().first()
        assertEquals(setOf("jawa-tengah", "di-yogyakarta"), maps.cleanedKeep)
    }

    private companion object {
        const val VERSION = "maps-2026-11"
        const val SIZE = 21_295_529L
        const val HALF = SIZE / 2
        const val HASH = "6335245d4704321b434bc2043fa93be9f7085c53c091de46baf5f16a49e9774b"
        const val URL = "https://github.com/wahyurama-creator/Ridecomp/releases/download/maps-2026-11/"

        val DIY_FILE = MapPackFile("di-yogyakarta.pmtiles", URL + "di-yogyakarta.pmtiles", SIZE, HASH)
        val DIY = MapRegion("di-yogyakarta", "DI Yogyakarta", MapBounds(109.9, -8.42, 110.84, -7.54), listOf(DIY_FILE))
        val JATENG = MapRegion(
            "jawa-tengah",
            "Jawa Tengah",
            MapBounds(108.56, -8.44, 111.87, -4.04),
            listOf(MapPackFile("jawa-tengah.pmtiles", URL + "jawa-tengah.pmtiles", SIZE, HASH)),
        )
    }
}
