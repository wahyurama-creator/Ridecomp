package com.ridecomp.core.source

import com.ridecomp.core.engine.map.MapFileTransfer
import com.ridecomp.core.engine.map.MapPackFile
import com.ridecomp.core.engine.map.MapRegionIndex
import kotlinx.coroutines.flow.Flow

/** The region index shipped in the APK (T11.12); [MapRegionIndex.EMPTY] when it cannot be read. */
public interface MapRegionIndexSource {
    public suspend fun load(): MapRegionIndex
}

/**
 * Downloads one pack file over HTTPS into the offline maps (T11.12, D-130): resumes a partial file, checks
 * the SHA-256 and the PMTiles header, and only then adds it as an offline map. Runs while collected.
 */
public interface MapPackDownloader {
    public fun download(file: MapPackFile): Flow<MapFileTransfer>

    /** Bytes of [file] already downloaded (a paused download), 0 when none. */
    public suspend fun partialBytes(file: MapPackFile): Long

    public suspend fun discardPartial(file: MapPackFile)
}
