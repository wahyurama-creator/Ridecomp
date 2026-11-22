package com.ridecomp.ui.map

import android.annotation.SuppressLint
import android.view.MotionEvent
import android.view.View
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ridecomp.ui.R
import com.ridecomp.ui.component.MapAttribution
import com.ridecomp.ui.theme.RideCompTheme
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapLibreMapOptions
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

/** A position on the map (kept free of :core types, ARCH-02). */
@Immutable
data class MapPoint(val latDeg: Double, val lonDeg: Double)

/** Zooms of the offline map (T3.5). */
object OfflineMapZoom {
    /** Street level while following the rider. */
    const val FOLLOW = 15.5

    const val MIN = 5.0
    const val MAX = 17.0
}

/**
 * Vector map (T3.3–T3.5, D-90; 13-UI-SCREENS #7, #12): OpenFreeMap tiles over the internet when
 * [isOnline], else the tiles cached by earlier online maps when [isCached] (T11.11) and PMTiles regions from
 * internal storage; route in accent, ridden part in track blue, position dot, north-up (no rotation).
 * MapLibre may use the network only while [isOnline]. The MapView
 * follows the screen lifecycle and is destroyed with the composable (09 §1).
 */
@Composable
fun RouteMap(
    isOnline: Boolean,
    regionPaths: ImmutableList<String>,
    route: ImmutableList<MapPoint>,
    description: String,
    modifier: Modifier = Modifier,
    ridden: ImmutableList<MapPoint> = persistentListOf(),
    position: MapPoint? = null,
    isFollowing: Boolean = false,
    onUserMoved: () -> Unit = {},
    /** A green dot, e.g. the start to ride back to (T7.2). */
    marker: MapPoint? = null,
    isCached: Boolean = false,
    /** Lines of [route] drawn without GPS (index i = point i to i + 1), dashed (D-149). */
    gaps: ImmutableList<Int> = persistentListOf(),
    /**
     * Drags on the map pan the map and are not taken by a parent pager or list (the ride screen's pages).
     * Off on scrolling pages, where a drag over the map should still scroll the page.
     */
    isDragExclusive: Boolean = false,
) {
    // MapLibre is prepared off the main thread first (T11.2a); meanwhile an empty box of the same size.
    // The whole map then replaces the box in one composition: a MapView added later into an existing box
    // never started rendering on a cold start (RMX3830).
    val context = LocalContext.current
    val io = LocalMapIoContext.current
    val cacheLimitBytes = LocalMapCacheLimitBytes.current
    val options by produceState<MapLibreMapOptions?>(null, context, io) {
        value = MapEngine.prepareView(context, io)
    }
    val ready = options
    // MapLibre is ready once the options are: the tile cache limit from Settings (T11.11).
    LaunchedEffect(ready != null, cacheLimitBytes) {
        if (ready != null && cacheLimitBytes != null) MapTileCache.applyLimit(context, cacheLimitBytes)
    }
    if (ready == null) {
        Box(modifier = modifier.semantics { contentDescription = description })
        return
    }
    PreparedRouteMap(
        options = ready,
        isOnline = isOnline,
        isCached = isCached,
        regionPaths = regionPaths,
        route = route,
        description = description,
        modifier = modifier,
        ridden = ridden,
        position = position,
        isFollowing = isFollowing,
        onUserMoved = onUserMoved,
        marker = marker,
        gaps = gaps,
        isDragExclusive = isDragExclusive,
    )
}

@Composable
@Suppress("LongParameterList") // The inputs of RouteMap plus the prepared options.
private fun PreparedRouteMap(
    options: MapLibreMapOptions,
    isOnline: Boolean,
    isCached: Boolean,
    regionPaths: ImmutableList<String>,
    route: ImmutableList<MapPoint>,
    description: String,
    modifier: Modifier = Modifier,
    ridden: ImmutableList<MapPoint> = persistentListOf(),
    position: MapPoint? = null,
    isFollowing: Boolean = false,
    onUserMoved: () -> Unit = {},
    /** A green dot, e.g. the start to ride back to (T7.2). */
    marker: MapPoint? = null,
    gaps: ImmutableList<Int> = persistentListOf(),
    isDragExclusive: Boolean = false,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val colors = RideCompTheme.colors
    val styleJson = remember(isOnline, isCached, regionPaths, colors) {
        OfflineMapStyle.json(isOnline, isCached, regionPaths, colors)
    }
    val currentOnUserMoved by rememberUpdatedState(onUserMoved)
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var style by remember { mutableStateOf<Style?>(null) }
    val mapView = remember {
        MapView(context, options).apply {
            onCreate(null)
            if (isDragExclusive) keepDragsFromParent()
        }
    }
    DisposableEffect(lifecycle, mapView) {
        val observer = mapView.lifecycleObserver()
        lifecycle.addObserver(observer)
        mapView.getMapAsync { loaded ->
            loaded.configureForRide { currentOnUserMoved() }
            map = loaded
        }
        onDispose {
            lifecycle.removeObserver(observer)
            mapView.release(lifecycle.currentState)
            map = null
            style = null
        }
    }
    LaunchedEffect(map, styleJson) {
        val loaded = map ?: return@LaunchedEffect
        // Offline: cached tiles and regions are local, so no tile or telemetry request leaves the phone.
        MapLibre.setConnected(isOnline)
        style = null
        loaded.setStyle(Style.Builder().fromJson(styleJson)) { style = it }
    }
    LaunchedEffect(style, route, gaps) {
        val ready = style ?: return@LaunchedEffect
        val parts = route.splitAtGaps(gaps)
        ready.geoJson(MapSourceIds.ROUTE)?.setGeoJson(parts.withFix.toLines())
        ready.geoJson(MapSourceIds.ROUTE_GAPS)?.setGeoJson(parts.guessed.toLines())
        // Bounds need the view's size: in a lazy list the style can load before the first layout (T11.2).
        if (!isFollowing) map?.let { loaded -> mapView.afterLayout { loaded.fitTo(route) } }
    }
    LaunchedEffect(style, ridden) { style?.geoJson(MapSourceIds.RIDDEN)?.setGeoJson(ridden.toLine()) }
    LaunchedEffect(style, marker) { style?.geoJson(MapSourceIds.MARKER)?.setGeoJson(marker.toPointCollection()) }
    LaunchedEffect(style, position, isFollowing) {
        val ready = style ?: return@LaunchedEffect
        ready.geoJson(MapSourceIds.POSITION)?.setGeoJson(position.toPointCollection())
        if (isFollowing && position != null) {
            // Jump, not animate: the camera moves once a second while riding (02-DESIGN §6).
            map?.moveCamera(CameraUpdateFactory.newLatLngZoom(position.toLatLng(), OfflineMapZoom.FOLLOW))
        }
    }
    Box(modifier = modifier.semantics { contentDescription = description }) {
        AndroidView(factory = { mapView }, modifier = Modifier.matchParentSize())
        MapAttribution(
            text = stringResource(attributionRes(isOnline, isCached, regionPaths.isNotEmpty())),
            modifier = Modifier.align(Alignment.BottomStart),
        )
    }
}

/** Credits of what is drawn (T11.14): online/cached OpenFreeMap tiles, offline Protomaps regions, or both. */
@StringRes
private fun attributionRes(isOnline: Boolean, isCached: Boolean, hasRegions: Boolean): Int = when {
    isOnline -> R.string.map_attribution_online
    isCached && hasRegions -> R.string.map_attribution_both
    isCached -> R.string.map_attribution_online
    else -> R.string.map_attribution
}

/** Forwards the screen lifecycle to the MapView (09 §1). */
private fun MapView.lifecycleObserver() = LifecycleEventObserver { _, event ->
    when (event) {
        Lifecycle.Event.ON_START -> onStart()
        Lifecycle.Event.ON_RESUME -> onResume()
        Lifecycle.Event.ON_PAUSE -> onPause()
        Lifecycle.Event.ON_STOP -> onStop()
        else -> Unit
    }
}

/** Brings the MapView down from [state] and destroys it (leaving the composition). */
private fun MapView.release(state: Lifecycle.State) {
    if (state.isAtLeast(Lifecycle.State.RESUMED)) onPause()
    if (state.isAtLeast(Lifecycle.State.STARTED)) onStop()
    onDestroy()
}

/**
 * From the first touch the parent may not take the gesture over: in a pager, Compose honours this and the
 * drag pans the map instead of turning the page. The map still handles the touch itself.
 */
@SuppressLint("ClickableViewAccessibility") // Not a click: only claims the drag; the map's own handling runs.
private fun View.keepDragsFromParent() {
    setOnTouchListener { view, event ->
        if (event.actionMasked == MotionEvent.ACTION_DOWN) view.parent?.requestDisallowInterceptTouchEvent(true)
        false
    }
}

/** North-up, no tilt, our own attribution; a user pan reports [onUserMoved] (stops following). */
private fun MapLibreMap.configureForRide(onUserMoved: () -> Unit) {
    uiSettings.apply {
        isRotateGesturesEnabled = false
        isTiltGesturesEnabled = false
        isCompassEnabled = false
        isLogoEnabled = false
        isAttributionEnabled = false
    }
    setMinZoomPreference(OfflineMapZoom.MIN)
    setMaxZoomPreference(OfflineMapZoom.MAX)
    addOnCameraMoveStartedListener { reason ->
        if (reason == MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE) onUserMoved()
    }
}

private fun Style.geoJson(id: String): GeoJsonSource? = getSource(id) as? GeoJsonSource

private fun MapPoint.toPoint(): Point = Point.fromLngLat(lonDeg, latDeg)

private fun MapPoint.toLatLng(): LatLng = LatLng(latDeg, lonDeg)

private fun MapPoint?.toPointCollection(): FeatureCollection =
    this?.let { FeatureCollection.fromFeature(Feature.fromGeometry(it.toPoint())) }
        ?: FeatureCollection.fromFeatures(emptyList())

private fun List<MapPoint>.toLine(): FeatureCollection = if (size < 2) {
    FeatureCollection.fromFeatures(emptyList())
} else {
    FeatureCollection.fromFeature(Feature.fromGeometry(LineString.fromLngLats(map { it.toPoint() })))
}

private fun List<List<MapPoint>>.toLines(): FeatureCollection = FeatureCollection.fromFeatures(
    filter { it.size >= 2 }.map { part -> Feature.fromGeometry(LineString.fromLngLats(part.map { it.toPoint() })) },
)

/** Runs [action] now when the view has a size, else once after its first layout. */
private fun View.afterLayout(action: () -> Unit) {
    if (width > 0 && height > 0) {
        action()
        return
    }
    addOnLayoutChangeListener(
        object : View.OnLayoutChangeListener {
            override fun onLayoutChange(
                view: View,
                left: Int,
                top: Int,
                right: Int,
                bottom: Int,
                oldLeft: Int,
                oldTop: Int,
                oldRight: Int,
                oldBottom: Int,
            ) {
                view.removeOnLayoutChangeListener(this)
                action()
            }
        },
    )
}

private fun MapLibreMap.fitTo(route: List<MapPoint>) {
    if (route.size < 2) return
    val bounds = LatLngBounds.Builder().includes(route.map { it.toLatLng() }).build()
    moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, ROUTE_PADDING_PX))
}

private const val ROUTE_PADDING_PX = 48
