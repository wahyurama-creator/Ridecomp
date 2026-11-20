package com.ridecomp.core.testing

import com.ridecomp.core.engine.map.MapFileTransfer
import com.ridecomp.core.engine.map.MapPackFile
import com.ridecomp.core.engine.map.MapRegionIndex
import com.ridecomp.core.engine.map.TrailPoint
import com.ridecomp.core.repository.MapPackStateRepository
import com.ridecomp.core.repository.RidePositionRepository
import com.ridecomp.core.source.MapPackDownloader
import com.ridecomp.core.source.MapRegionIndexSource
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow

public class FakeRidePositionRepository : RidePositionRepository {
    public val lastPosition: MutableStateFlow<TrailPoint?> = MutableStateFlow(null)

    override fun observeLastPosition(): Flow<TrailPoint?> = lastPosition
}

public class FakeMapRegionIndexSource(public var index: MapRegionIndex = MapRegionIndex.EMPTY) : MapRegionIndexSource {
    override suspend fun load(): MapRegionIndex = index
}

/** In-memory pack state (T11.12). */
public class FakeMapPackStateRepository : MapPackStateRepository {
    public val installed: MutableStateFlow<Map<String, String>> = MutableStateFlow(emptyMap())
    public val dismissed: MutableStateFlow<Set<String>> = MutableStateFlow(emptySet())
    public val mobileData: MutableStateFlow<Boolean> = MutableStateFlow(false)

    override fun observeInstalled(): Flow<Map<String, String>> = installed

    override suspend fun setInstalled(regionId: String, version: String?) {
        installed.value = if (version == null) installed.value - regionId else installed.value + (regionId to version)
    }

    override fun observeDismissed(): Flow<Set<String>> = dismissed

    override suspend fun dismiss(offerKey: String) {
        dismissed.value = dismissed.value + offerKey
    }

    override fun observeMobileDataAllowed(): Flow<Boolean> = mobileData

    override suspend fun setMobileDataAllowed(isAllowed: Boolean) {
        mobileData.value = isAllowed
    }
}

/**
 * Downloads [MapPackFile]s into [maps] (named by [MapPackFile.mapName]) in two progress steps; [gate] holds a
 * download half-way so a test can pause it, [failure] makes it fail.
 */
public class FakeMapPackDownloader(private val maps: FakeOfflineMapRepository) : MapPackDownloader {
    public val partial: MutableMap<String, Long> = mutableMapOf()
    public var gate: CompletableDeferred<Unit>? = null
    public var failure: MapFileTransfer.Failed? = null

    override fun download(file: MapPackFile): Flow<MapFileTransfer> = flow {
        val half = file.sizeBytes / 2
        partial[file.name] = half
        emit(MapFileTransfer.Progress(half))
        gate?.await()
        failure?.let {
            emit(it)
            return@flow
        }
        partial.remove(file.name)
        maps.add(file.mapName, file.sizeBytes)
        emit(MapFileTransfer.Progress(file.sizeBytes))
        emit(MapFileTransfer.Done)
    }

    override suspend fun partialBytes(file: MapPackFile): Long = partial[file.name] ?: 0L

    override suspend fun discardPartial(file: MapPackFile) {
        partial.remove(file.name)
    }
}
