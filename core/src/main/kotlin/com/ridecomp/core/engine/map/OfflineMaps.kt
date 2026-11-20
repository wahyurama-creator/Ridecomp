package com.ridecomp.core.engine.map

import com.ridecomp.core.util.Outcome
import com.ridecomp.core.util.asFailure
import com.ridecomp.core.util.asSuccess
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Offline map limits (T3.4, 04-INTEGRATIONS §D, D-64). */
public object OfflineMapConfig {
    /** A province-sized region is a few hundred MB; anything larger is refused before copying. */
    public const val MAX_FILE_BYTES: Long = 2L * 1024 * 1024 * 1024

    /** Space left free on the phone after an import, so rides can still be recorded. */
    public const val MIN_FREE_AFTER_IMPORT_BYTES: Long = 200L * 1024 * 1024

    public const val FILE_EXTENSION: String = ".pmtiles"
    public const val MAX_NAME_LENGTH: Int = 60

    /** Below this free space Settings → Map warns (T11.14). */
    public const val LOW_SPACE_WARNING_BYTES: Long = 500L * BYTES_PER_MB
}

/** Most space all offline maps together may use: imports and province maps (T11.14, D-131). */
public enum class OfflineMapLimit(public val bytes: Long) {
    SMALL(500L * BYTES_PER_MB),
    MEDIUM(1_000L * BYTES_PER_MB),
    LARGE(2_000L * BYTES_PER_MB),
    ;

    /** Space left for a map named [name] when [maps] are stored (a map of the same name is replaced). */
    public fun roomFor(name: String, maps: List<OfflineMap>): Long =
        (bytes - maps.filterNot { it.name == name }.sumOf { it.sizeBytes }).coerceAtLeast(0)

    public companion object {
        public val DEFAULT: OfflineMapLimit = MEDIUM
    }
}

/** Decimal megabytes, as Android shows file sizes. */
private const val BYTES_PER_MB = 1_000_000L

/** Geographic box in degrees. */
public data class MapBounds(val minLon: Double, val minLat: Double, val maxLon: Double, val maxLat: Double) {
    public fun contains(latDeg: Double, lonDeg: Double): Boolean = latDeg in minLat..maxLat && lonDeg in minLon..maxLon
}

/** What the PMTiles header says about a region file. */
public data class PmTilesInfo(val bounds: MapBounds, val minZoom: Int, val maxZoom: Int)

/** An imported offline map region (one PMTiles file in internal storage, SEC-STO-01). */
public data class OfflineMap(
    /** File name without extension; also the id. */
    val name: String,
    val sizeBytes: Long,
    val info: PmTilesInfo,
    val importedAtMs: Long,
    /** Absolute path of the file in internal storage, for the map renderer. */
    val path: String,
)

/** Why a file could not be imported. */
public enum class MapImportError {
    NOT_PMTILES,
    UNSUPPORTED_VERSION,
    NOT_VECTOR,
    TOO_LARGE,
    NO_SPACE,
    READ_FAILED,

    /** The offline maps would exceed the chosen total (T11.14). */
    TOTAL_LIMIT,
}

/** The maps that cover a point, e.g. the rider's position (first match wins in the style). */
public fun List<OfflineMap>.covering(latDeg: Double, lonDeg: Double): List<OfflineMap> =
    filter { it.info.bounds.contains(latDeg, lonDeg) }

/**
 * Validates the fixed 127-byte PMTiles v3 header (github.com/protomaps/PMTiles spec v3, little endian).
 * Only vector tiles are accepted: the built-in style draws the Protomaps basemap layers (D-64). An
 * untrusted file is never trusted beyond these checks (SEC-IN-01); the renderer reads the rest.
 */
public object PmTilesHeader {
    public const val SIZE_BYTES: Int = 127
    private const val MAGIC = "PMTiles"
    private const val SUPPORTED_VERSION = 3
    private const val TILE_TYPE_MVT = 1
    private const val VERSION_OFFSET = 7
    private const val TILE_TYPE_OFFSET = 99
    private const val MIN_ZOOM_OFFSET = 100
    private const val MAX_ZOOM_OFFSET = 101
    private const val BOUNDS_OFFSET = 102
    private const val E7 = 10_000_000.0
    private const val MAX_LAT = 90.0
    private const val MAX_LON = 180.0
    private const val UNSIGNED_BYTE = 0xFF

    /** Order of the four bound fields (min lon, min lat, max lon, max lat). */
    private const val MIN_LON_FIELD = 0
    private const val MIN_LAT_FIELD = 1
    private const val MAX_LON_FIELD = 2
    private const val MAX_LAT_FIELD = 3

    public fun parse(bytes: ByteArray): Outcome<PmTilesInfo, MapImportError> {
        val error = headerError(bytes)
        if (error != null) return error.asFailure()
        val info = info(bytes)
        return if (info.isPlausible()) info.asSuccess() else MapImportError.NOT_PMTILES.asFailure()
    }

    private fun headerError(bytes: ByteArray): MapImportError? = when {
        bytes.size < SIZE_BYTES -> MapImportError.NOT_PMTILES
        String(bytes, 0, MAGIC.length, Charsets.US_ASCII) != MAGIC -> MapImportError.NOT_PMTILES
        bytes[VERSION_OFFSET].toInt() != SUPPORTED_VERSION -> MapImportError.UNSUPPORTED_VERSION
        bytes[TILE_TYPE_OFFSET].toInt() != TILE_TYPE_MVT -> MapImportError.NOT_VECTOR
        else -> null
    }

    private fun info(bytes: ByteArray): PmTilesInfo {
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        fun degrees(field: Int) = buffer.getInt(BOUNDS_OFFSET + field * Int.SIZE_BYTES) / E7
        return PmTilesInfo(
            bounds = MapBounds(
                minLon = degrees(MIN_LON_FIELD),
                minLat = degrees(MIN_LAT_FIELD),
                maxLon = degrees(MAX_LON_FIELD),
                maxLat = degrees(MAX_LAT_FIELD),
            ),
            minZoom = bytes[MIN_ZOOM_OFFSET].toInt() and UNSIGNED_BYTE,
            maxZoom = bytes[MAX_ZOOM_OFFSET].toInt() and UNSIGNED_BYTE,
        )
    }

    private fun PmTilesInfo.isPlausible(): Boolean = bounds.minLat >= -MAX_LAT &&
        bounds.maxLat <= MAX_LAT &&
        bounds.minLon >= -MAX_LON &&
        bounds.maxLon <= MAX_LON &&
        bounds.minLat < bounds.maxLat &&
        bounds.minLon < bounds.maxLon &&
        minZoom <= maxZoom
}
