package com.ridecomp.core.engine.map

import com.ridecomp.core.engine.geo.GeoMath

/** When the online map may load tiles (D-90). */
public enum class OnlineMapMode {
    /** Wi‑Fi and mobile data (default). */
    ALWAYS,

    /** Only on an unmetered network (Wi‑Fi). */
    WIFI_ONLY,

    /** Never: offline map files or the breadcrumb only. */
    OFF,
}

/** Most space the online map's tile cache may use; the least recently used tiles go first (T11.11, D-128). */
public enum class MapCacheLimit(public val bytes: Long) {
    SMALL(50L * BYTES_PER_MB),
    MEDIUM(150L * BYTES_PER_MB),
    LARGE(300L * BYTES_PER_MB),
    ;

    public companion object {
        public val DEFAULT: MapCacheLimit = MEDIUM
    }
}

/** Decimal megabytes, as Android shows file sizes (Formatter): the 150 MB choice reads "150 MB". */
private const val BYTES_PER_MB = 1_000_000L

/** The phone's default network, as far as the map cares. */
public data class NetworkStatus(val isConnected: Boolean, val isUnmetered: Boolean) {
    public companion object {
        public val OFFLINE: NetworkStatus = NetworkStatus(isConnected = false, isUnmetered = false)
    }
}

/** True when [mode] lets the map download tiles on [network]. */
public fun OnlineMapMode.allows(network: NetworkStatus): Boolean = when (this) {
    OnlineMapMode.ALWAYS -> network.isConnected
    OnlineMapMode.WIFI_ONLY -> network.isConnected && network.isUnmetered
    OnlineMapMode.OFF -> false
}

/**
 * What a map can show right now (D-90, T11.11): online tiles while there is internet and the setting allows
 * it; otherwise the tiles seen online before ([hasCache], read from the cache without any network) and the
 * imported offline regions. With none of them there is no map, only the line (breadcrumb).
 */
public data class MapTiles(val isOnline: Boolean, val regionPaths: List<String>, val hasCache: Boolean = false) {
    public val hasMap: Boolean get() = isOnline || hasCache || regionPaths.isNotEmpty()

    /** Online tiles drawn, from the network or from the cache only. */
    public val usesOnlineTiles: Boolean get() = isOnline || hasCache

    public companion object {
        public val NONE: MapTiles = MapTiles(isOnline = false, regionPaths = emptyList())
    }
}

/** A point of a drawn line. */
public data class TrailPoint(val latDeg: Double, val lonDeg: Double)

/** Limits of the ridden line drawn on a ride without a route (D-90). */
public object TrailConfig {
    /** A new point only after this much movement: ~1 point per 2 s at 18 km/h. */
    public const val MIN_SPACING_M: Double = 10.0

    /** Above this the line is thinned by half, so memory stays bounded on any ride length (MEM-03). */
    public const val MAX_POINTS: Int = 2_000

    /** Stored samples read per query when the line is rebuilt (recovery, screen reopened). */
    public const val PAGE_SIZE: Int = 1_000
}

/**
 * The ridden line of a ride without a route: points at least [TrailConfig.MIN_SPACING_M] apart, thinned
 * to half whenever it would exceed [TrailConfig.MAX_POINTS]. Not thread-safe: one owner.
 */
public class RideTrail {
    private val points = ArrayList<TrailPoint>()

    public val snapshot: List<TrailPoint> get() = points.toList()

    /** Adds a position; returns true when the line changed. */
    public fun add(latDeg: Double, lonDeg: Double): Boolean {
        val last = points.lastOrNull()
        if (last != null && GeoMath.distanceM(last.latDeg, last.lonDeg, latDeg, lonDeg) < TrailConfig.MIN_SPACING_M) {
            return false
        }
        points += TrailPoint(latDeg, lonDeg)
        if (points.size > TrailConfig.MAX_POINTS) thin()
        return true
    }

    /** Keeps every second point and always the newest one. */
    private fun thin() {
        val newest = points.last()
        val kept = points.filterIndexed { index, _ -> index % 2 == 0 }
        points.clear()
        points += kept
        if (points.last() != newest) points += newest
    }
}
