package com.ridecomp.ui.map

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ridecomp.ui.component.MapAttribution
import com.ridecomp.ui.theme.RideCompTheme
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
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
 * Offline vector map (T3.3–T3.5, 13-UI-SCREENS #7, #12): PMTiles regions from internal storage, route
 * in accent, ridden part in track blue, position dot, north-up (no rotation). MapLibre runs with network
 * off (D-64). The MapView follows the screen lifecycle and is destroyed with the composable (09 §1).
 */
@Composable
fun OfflineRouteMap(
    regionPaths: ImmutableList<String>,
    route: ImmutableList<MapPoint>,
    attribution: String,
    description: String,
    modifier: Modifier = Modifier,
    ridden: ImmutableList<MapPoint> = persistentListOf(),
    position: MapPoint? = null,
    isFollowing: Boolean = false,
    onUserMoved: () -> Unit = {},
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val colors = RideCompTheme.colors
    val styleJson = remember(regionPaths, colors) { OfflineMapStyle.json(regionPaths, colors) }
    val currentOnUserMoved by rememberUpdatedState(onUserMoved)
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var style by remember { mutableStateOf<Style?>(null) }
    val mapView = remember {
        MapLibre.getInstance(context.applicationContext)
        // Regions are local files: no tile, glyph or telemetry request ever leaves the phone.
        MapLibre.setConnected(false)
        MapView(context).apply { onCreate(null) }
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
        style = null
        loaded.setStyle(Style.Builder().fromJson(styleJson)) { style = it }
    }
    LaunchedEffect(style, route) {
        val ready = style ?: return@LaunchedEffect
        ready.geoJson(MapSourceIds.ROUTE)?.setGeoJson(route.toLine())
        if (!isFollowing) map?.fitTo(route)
    }
    LaunchedEffect(style, ridden) { style?.geoJson(MapSourceIds.RIDDEN)?.setGeoJson(ridden.toLine()) }
    LaunchedEffect(style, position, isFollowing) {
        val ready = style ?: return@LaunchedEffect
        ready.geoJson(MapSourceIds.POSITION)?.setGeoJson(
            position?.let { FeatureCollection.fromFeature(Feature.fromGeometry(it.toPoint())) }
                ?: FeatureCollection.fromFeatures(emptyList()),
        )
        if (isFollowing && position != null) {
            // Jump, not animate: the camera moves once a second while riding (02-DESIGN §6).
            map?.moveCamera(CameraUpdateFactory.newLatLngZoom(position.toLatLng(), OfflineMapZoom.FOLLOW))
        }
    }
    Box(modifier = modifier.semantics { contentDescription = description }) {
        AndroidView(factory = { mapView }, modifier = Modifier.matchParentSize())
        MapAttribution(text = attribution, modifier = Modifier.align(Alignment.BottomStart))
    }
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

private fun List<MapPoint>.toLine(): FeatureCollection = if (size < 2) {
    FeatureCollection.fromFeatures(emptyList())
} else {
    FeatureCollection.fromFeature(Feature.fromGeometry(LineString.fromLngLats(map { it.toPoint() })))
}

private fun MapLibreMap.fitTo(route: List<MapPoint>) {
    if (route.size < 2) return
    val bounds = LatLngBounds.Builder().includes(route.map { it.toLatLng() }).build()
    moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, ROUTE_PADDING_PX))
}

private const val ROUTE_PADDING_PX = 48
