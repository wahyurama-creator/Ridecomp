package com.ridecomp.core.engine.map

/** Province map packs offered for download (T11.12, D-129, D-130). */
public object MapPackConfig {
    /** The index format this app reads (`assets/maps/regions.json`). */
    public const val INDEX_SCHEMA: Int = 1

    /** Packs come only from the project's own GitHub releases (SEC-NET, D-112). */
    public const val ALLOWED_URL_PREFIX: String = "https://github.com/wahyurama-creator/Ridecomp/releases/download/"

    public const val SHA256_HEX_LENGTH: Int = 64
}

/** One PMTiles file of a region pack; [name] is the file name with its `.pmtiles` extension. */
public data class MapPackFile(val name: String, val url: String, val sizeBytes: Long, val sha256: String) {
    /** The offline map name it is stored under (file name without extension). */
    val mapName: String get() = name.removeSuffix(OfflineMapConfig.FILE_EXTENSION)

    /** Only well-formed entries are offered: our release URL, a plain file name, a sane size and checksum. */
    val isValid: Boolean
        get() = url.startsWith(MapPackConfig.ALLOWED_URL_PREFIX) &&
            name.endsWith(OfflineMapConfig.FILE_EXTENSION) &&
            mapName.isNotEmpty() &&
            mapName.all { it.isLetterOrDigit() || it == '-' } &&
            sizeBytes in 1..OfflineMapConfig.MAX_FILE_BYTES &&
            sha256.length == MapPackConfig.SHA256_HEX_LENGTH &&
            sha256.all { it in '0'..'9' || it in 'a'..'f' }
}

/** A province (or part of one) with its bounding box and files. */
public data class MapRegion(val id: String, val name: String, val bounds: MapBounds, val files: List<MapPackFile>) {
    val sizeBytes: Long get() = files.sumOf { it.sizeBytes }

    private val areaDeg2: Double get() = (bounds.maxLon - bounds.minLon) * (bounds.maxLat - bounds.minLat)

    public companion object {
        /** The smallest region whose box holds the point: boxes of neighbouring provinces overlap at sea. */
        public fun at(regions: List<MapRegion>, latDeg: Double, lonDeg: Double): MapRegion? =
            regions.filter { it.bounds.contains(latDeg, lonDeg) }.minByOrNull { it.areaDeg2 }
    }
}

/** The packs shipped in the APK; [version] is the release tag, e.g. `maps-2026-11`. */
public data class MapRegionIndex(val schema: Int, val version: String, val regions: List<MapRegion>) {
    /** Regions with only valid files; an unknown schema offers nothing (SEC-IN-01). */
    public fun validated(): MapRegionIndex = copy(
        regions = if (schema != MapPackConfig.INDEX_SCHEMA) {
            emptyList()
        } else {
            regions.filter { region -> region.files.isNotEmpty() && region.files.all { it.isValid } }
        },
    )

    public companion object {
        public val EMPTY: MapRegionIndex = MapRegionIndex(MapPackConfig.INDEX_SCHEMA, "", emptyList())
    }
}

/** Why a pack download stopped. */
public enum class MapPackError {
    NO_NETWORK,
    NEEDS_WIFI,
    RIDING,
    DOWNLOAD_FAILED,
    CHECKSUM,
    NOT_PMTILES,
    NO_SPACE,

    /** The offline maps would exceed the chosen total (T11.14). */
    TOTAL_LIMIT,
}

/** Progress of one region's download. */
public sealed interface MapDownload {
    public data class Running(val downloadedBytes: Long, val totalBytes: Long) : MapDownload

    /** Stopped part-way (app left, ride started, cancelled): resumes from [downloadedBytes]. */
    public data class Paused(val downloadedBytes: Long, val totalBytes: Long) : MapDownload

    public data class Failed(val error: MapPackError) : MapDownload
}

/** One file's transfer, as the downloader reports it. */
public sealed interface MapFileTransfer {
    public data class Progress(val downloadedBytes: Long) : MapFileTransfer

    public data object Done : MapFileTransfer

    public data class Failed(val error: MapPackError) : MapFileTransfer
}

/** Where a region stands on this phone. */
public enum class MapRegionStatus { NOT_INSTALLED, INSTALLED, UPDATE_AVAILABLE }

/** A region of the index with its state; [isHere] when it holds the last known position. */
public data class MapRegionState(
    val region: MapRegion,
    val status: MapRegionStatus,
    val download: MapDownload?,
    val isHere: Boolean,
)
