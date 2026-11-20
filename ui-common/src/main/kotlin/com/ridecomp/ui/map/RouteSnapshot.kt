package com.ridecomp.ui.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import com.ridecomp.ui.theme.DarkRideCompColors
import com.ridecomp.ui.theme.LightRideCompColors
import kotlinx.collections.immutable.ImmutableList
import org.maplibre.android.MapLibre
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.Style
import org.maplibre.android.snapshotter.MapSnapshotter

/**
 * A still map of a ride for a share card (T10.6): the image and where each point of the line falls on it,
 * from 0..1 across and down.
 */
@Immutable
class RouteSnapshot(val image: ImageBitmap, val line: List<Offset>)

/**
 * Renders the map around [trail] once, off screen, at [widthPx] × [heightPx] (09 §1: the MapSnapshotter is
 * owned by this composition, started here and cancelled when it leaves or the inputs change). Null until the
 * map is ready, and when there is no map or no line. Online tiles are fetched only when [isOnline], else the
 * offline regions in [regionPaths] are used; never the tile cache, which may hold only part of the area and
 * would leave holes in a shared image (T11.11).
 */
@Composable
@Suppress("LongParameterList") // The map inputs of RouteMap plus the image size and theme.
fun rememberRouteSnapshot(
    trail: ImmutableList<MapPoint>,
    isOnline: Boolean,
    regionPaths: ImmutableList<String>,
    widthPx: Int,
    heightPx: Int,
    isDark: Boolean,
): RouteSnapshot? {
    val context = LocalContext.current
    val request = SnapshotRequest(trail, isOnline, regionPaths, widthPx, heightPx, isDark)
    var snapshot by remember(request) { mutableStateOf<RouteSnapshot?>(null) }
    // The native library loads off the main thread first (T11.2a).
    val io = LocalMapIoContext.current
    val isLibraryReady by produceState(false, io) {
        MapEngine.loadLibrary(io)
        value = true
    }
    DisposableEffect(request, isLibraryReady) {
        val hasMap = trail.size >= 2 && (isOnline || regionPaths.isNotEmpty())
        if (!isLibraryReady || !hasMap) return@DisposableEffect onDispose { }
        MapLibre.getInstance(context.applicationContext)
        MapHttp.install()
        MapLibre.setConnected(isOnline)
        val colors = if (isDark) DarkRideCompColors else LightRideCompColors
        val points = trail.map { LatLng(it.latDeg, it.lonDeg) }
        val margin = (minOf(widthPx, heightPx) * SNAPSHOT_MARGIN).toInt()
        val options = MapSnapshotter.Options(widthPx, heightPx)
            .withStyleBuilder(
                Style.Builder().fromJson(OfflineMapStyle.json(isOnline, isCached = false, regionPaths, colors)),
            )
            .withRegion(LatLngBounds.Builder().includes(points).build())
            .withPadding(margin, margin, margin, margin)
            .withPixelRatio(1f)
            .withLogo(false)
            .withAttribution(false)
        val snapshotter = MapSnapshotter(context, options)
        snapshotter.start(
            { result ->
                snapshot = RouteSnapshot(
                    image = result.bitmap.asImageBitmap(),
                    line = points.map { p ->
                        result.pixelForLatLng(p).let { Offset(it.x / widthPx, it.y / heightPx) }
                    },
                )
            },
            // No tiles (offline region not covering the ride, server down): the card stays unavailable.
            { },
        )
        onDispose { snapshotter.cancel() }
    }
    return snapshot
}

/** Everything a snapshot depends on: a new one is made when any of it changes. */
private data class SnapshotRequest(
    val trail: List<MapPoint>,
    val isOnline: Boolean,
    val regionPaths: List<String>,
    val widthPx: Int,
    val heightPx: Int,
    val isDark: Boolean,
)

/** Padding on each side as a share of the shorter side, so the line is not cut at the edge. */
private const val SNAPSHOT_MARGIN = 0.12f
