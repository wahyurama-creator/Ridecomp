package com.ridecomp.core.usecase

import com.ridecomp.core.engine.map.MapDownload
import com.ridecomp.core.engine.map.MapFileTransfer
import com.ridecomp.core.engine.map.MapPackError
import com.ridecomp.core.engine.map.MapPackFile
import com.ridecomp.core.engine.map.MapRegion
import com.ridecomp.core.engine.map.MapRegionIndex
import com.ridecomp.core.engine.map.MapRegionState
import com.ridecomp.core.engine.map.MapRegionStatus
import com.ridecomp.core.engine.map.NetworkStatus
import com.ridecomp.core.repository.MapPackStateRepository
import com.ridecomp.core.repository.OfflineMapRepository
import com.ridecomp.core.repository.RidePositionRepository
import com.ridecomp.core.repository.SettingsRepository
import com.ridecomp.core.source.MapPackDownloader
import com.ridecomp.core.source.MapRegionIndexSource
import com.ridecomp.core.source.NetworkStatusSource
import com.ridecomp.core.source.RideController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Region pack downloads (T11.12, D-130), one region at a time, owned by the app (single instance): only while
 * the app is open (the app pauses them when it leaves, BAT-05) and never during a ride. Wi‑Fi only unless the
 * rider allows mobile data. A pause keeps the partial files; the next start resumes them.
 */
public class MapPackDownloads(
    private val downloader: MapPackDownloader,
    private val state: MapPackStateRepository,
    private val network: NetworkStatusSource,
    private val rides: RideController,
    private val limits: MapLimitCheck,
    private val scope: CoroutineScope,
) {
    private val progress = MutableStateFlow<Map<String, MapDownload>>(emptyMap())
    private val mutex = Mutex()
    private var job: Job? = null
    private var running: MapRegion? = null

    /** Region id → its download, for regions started or paused in this process. */
    public val downloads: StateFlow<Map<String, MapDownload>> = progress.asStateFlow()

    public fun start(region: MapRegion, version: String) {
        scope.launch {
            mutex.withLock {
                if (job?.isActive == true) return@withLock
                job = scope.launch { download(region, version) }
                running = region
            }
        }
    }

    /** The app left or a ride started: stop, keeping what is downloaded. */
    public fun pause() {
        val region = running ?: return
        job?.cancel()
        scope.launch { markPaused(region) }
    }

    /** "Cancel": stop and delete the partial files. */
    public fun cancel(region: MapRegion) {
        if (running?.id == region.id) job?.cancel()
        scope.launch {
            region.files.forEach { downloader.discardPartial(it) }
            progress.update { it - region.id }
        }
    }

    /** After a restart: regions with partial files show as paused, ready to resume. */
    public suspend fun restorePaused(regions: List<MapRegion>) {
        regions.filter { it.id !in progress.value }.forEach { markPaused(it) }
    }

    private suspend fun markPaused(region: MapRegion) {
        val done = region.files.sumOf { downloader.partialBytes(it) }
        progress.update { current ->
            if (done == 0L && current[region.id] !is MapDownload.Running) {
                current
            } else {
                current + (region.id to MapDownload.Paused(done, region.sizeBytes))
            }
        }
    }

    private suspend fun download(region: MapRegion, version: String) {
        val blocked = blockReason() ?: MapPackError.TOTAL_LIMIT.takeUnless { limits.fits(region) }
        if (blocked != null) {
            progress.update { it + (region.id to MapDownload.Failed(blocked)) }
            return
        }
        // A ride starting meanwhile pauses the download (never during a ride).
        val watchRide = scope.launch { rides.activeActivityId.filter { it != null }.first().let { pause() } }
        try {
            var finishedBytes = 0L
            for (file in region.files) {
                val error = transfer(region, file, finishedBytes)
                if (error != null) {
                    progress.update { it + (region.id to MapDownload.Failed(error)) }
                    return
                }
                finishedBytes += file.sizeBytes
            }
            state.setInstalled(region.id, version)
            progress.update { it - region.id }
        } finally {
            watchRide.cancel()
            running = null
        }
    }

    /** Copies one file; returns the error that stopped it, null when it was added as an offline map. */
    private suspend fun transfer(region: MapRegion, file: MapPackFile, before: Long): MapPackError? {
        var error: MapPackError? = null
        downloader.download(file).collect { step ->
            when (step) {
                is MapFileTransfer.Progress -> progress.update {
                    it + (region.id to MapDownload.Running(before + step.downloadedBytes, region.sizeBytes))
                }
                MapFileTransfer.Done -> Unit
                is MapFileTransfer.Failed -> error = step.error
            }
        }
        return error
    }

    private suspend fun blockReason(): MapPackError? {
        val status = network.observe().first()
        return when {
            rides.activeActivityId.value != null -> MapPackError.RIDING
            !status.isConnected -> MapPackError.NO_NETWORK
            !status.isUnmetered && !state.observeMobileDataAllowed().first() -> MapPackError.NEEDS_WIFI
            else -> null
        }
    }
}

/** Whether a region fits the total offline map limit, counting its own files as replaced (T11.14). */
public class MapLimitCheck(private val maps: OfflineMapRepository, private val settings: SettingsRepository) {
    public suspend fun fits(region: MapRegion): Boolean {
        val limit = settings.getRideSettings().offlineMapLimit
        val stored = maps.observeAll().first()
        val own = region.files.map { it.mapName }.toSet()
        val others = stored.filterNot { it.name in own }.sumOf { it.sizeBytes }
        return others + region.sizeBytes <= limit.bytes
    }
}

/** The index, read once per collection and validated. */
public class LoadMapRegionIndexUseCase(private val source: MapRegionIndexSource) {
    public suspend operator fun invoke(): MapRegionIndex = source.load().validated()
}

/**
 * Every region of the index with its state, the one holding the last ride's end marked [MapRegionState.isHere]
 * (no GPS request, D-113). Installed = all its files are offline maps and the version was recorded.
 */
/** The index version and every region's state. */
public data class MapRegions(val version: String, val regions: List<MapRegionState>) {
    val here: MapRegionState? get() = regions.firstOrNull { it.isHere }

    public companion object {
        public val EMPTY: MapRegions = MapRegions("", emptyList())
    }
}

public class ObserveMapRegionsUseCase(
    private val loadIndex: LoadMapRegionIndexUseCase,
    private val offlineMaps: OfflineMapRepository,
    private val state: MapPackStateRepository,
    private val positions: RidePositionRepository,
    private val downloads: MapPackDownloads,
) {
    public operator fun invoke(): Flow<MapRegions> = flow {
        val index = loadIndex()
        // Partial files of regions no longer offered and imports cut off by the app being killed (T11.14).
        offlineMaps.cleanTemporary(index.regions.flatMap { region -> region.files.map { it.mapName } }.toSet())
        downloads.restorePaused(index.regions)
        emit(index)
    }.let { indexFlow ->
        combine(
            indexFlow,
            offlineMaps.observeAll(),
            state.observeInstalled(),
            positions.observeLastPosition(),
            downloads.downloads,
        ) { index, maps, installed, position, running ->
            val names = maps.map { it.name }.toSet()
            val here = position?.let { MapRegion.at(index.regions, it.latDeg, it.lonDeg) }
            val states = index.regions.map { region ->
                val hasFiles = region.files.all { it.mapName in names }
                val status = when {
                    !hasFiles -> MapRegionStatus.NOT_INSTALLED
                    installed[region.id] == index.version -> MapRegionStatus.INSTALLED
                    else -> MapRegionStatus.UPDATE_AVAILABLE
                }
                MapRegionState(region, status, running[region.id], isHere = region.id == here?.id)
            }
            MapRegions(index.version, states)
        }.distinctUntilChanged()
    }
}

/** What the home card offers (T11.12): the region here when not installed or outdated and not declined. */
public data class MapRegionOffer(val state: MapRegionState, val version: String)

public class ObserveMapRegionOfferUseCase(
    private val regions: ObserveMapRegionsUseCase,
    private val state: MapPackStateRepository,
    private val rides: RideController,
) {
    public operator fun invoke(): Flow<MapRegionOffer?> =
        combine(regions(), state.observeDismissed(), rides.activeActivityId) { all, dismissed, ride ->
            all.here
                ?.takeIf { ride == null }
                ?.takeIf { it.status != MapRegionStatus.INSTALLED || it.download != null }
                ?.takeIf { offerKey(it.region, all.version) !in dismissed || it.download != null }
                ?.let { MapRegionOffer(it, all.version) }
        }.distinctUntilChanged()
}

/** "Later": not offered again for this region and version; another region is offered when the rider is there. */
public class DismissMapRegionOfferUseCase(private val state: MapPackStateRepository) {
    public suspend operator fun invoke(offer: MapRegionOffer) {
        state.dismiss(offerKey(offer.state.region, offer.version))
    }
}

/** Deletes a region's files and forgets it. */
public class DeleteMapRegionUseCase(
    private val offlineMaps: OfflineMapRepository,
    private val state: MapPackStateRepository,
) {
    public suspend operator fun invoke(region: MapRegion) {
        region.files.forEach { offlineMaps.delete(it.mapName) }
        state.setInstalled(region.id, null)
    }
}

public class ObserveMapMobileDataUseCase(private val state: MapPackStateRepository) {
    public operator fun invoke(): Flow<Boolean> = state.observeMobileDataAllowed()
}

public class SetMapMobileDataUseCase(private val state: MapPackStateRepository) {
    public suspend operator fun invoke(isAllowed: Boolean) {
        state.setMobileDataAllowed(isAllowed)
    }
}

/** Whether the network allows a pack download now (for the button state). */
public fun NetworkStatus.allowsPackDownload(isMobileDataAllowed: Boolean): Boolean =
    isConnected && (isUnmetered || isMobileDataAllowed)

private fun offerKey(region: MapRegion, version: String): String = "${region.id}@$version"
