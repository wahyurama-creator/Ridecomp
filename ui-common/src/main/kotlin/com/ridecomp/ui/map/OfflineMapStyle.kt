package com.ridecomp.ui.map

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.ridecomp.ui.theme.RideCompColors

/**
 * MapLibre style for offline PMTiles regions in the Protomaps basemap schema (layers earth, water,
 * landuse, roads, buildings; D-64). No text layers: labels would need glyph files from a server, and the
 * ride screen shows no street names anyway (12-FEATURE-SPECS §7.2). Route, ridden track and position
 * come from GeoJSON sources updated at runtime ([MapSourceIds]).
 */
internal object OfflineMapStyle {
    private const val PMTILES_SCHEME = "pmtiles://file://"
    private const val SOURCE_PREFIX = "region_"
    private const val BUILDINGS_MIN_ZOOM = 14
    private val MINOR_ROADS = listOf("minor_road", "path", "other")
    private val MAJOR_ROADS = listOf("highway", "major_road")
    private val GREEN_LANDUSE =
        listOf("park", "wood", "forest", "nature_reserve", "grass", "garden", "golf_course", "cemetery")

    /** One vector source per imported region file (absolute paths in internal storage). */
    fun json(regionPaths: List<String>, colors: RideCompColors): String {
        val map = colors.map
        val sources = regionPaths.mapIndexed { index, path ->
            "\"$SOURCE_PREFIX$index\":{\"type\":\"vector\",\"url\":${quote(PMTILES_SCHEME + path)}}"
        } + geoJsonSources()
        val layers = buildList {
            add("{\"id\":\"background\",\"type\":\"background\",\"paint\":{\"background-color\":${hex(map.water)}}}")
            regionPaths.indices.forEach { index -> addAll(regionLayers("$SOURCE_PREFIX$index", colors)) }
            addAll(rideLayers(colors))
        }
        return "{\"version\":8,\"sources\":{${sources.joinToString(",")}},\"layers\":[${layers.joinToString(",")}]}"
    }

    private fun regionLayers(source: String, colors: RideCompColors): List<String> {
        val map = colors.map
        return listOf(
            fill("${source}_earth", source, "earth", map.land),
            fill("${source}_park", source, "landuse", map.park, kindFilter(GREEN_LANDUSE)),
            fill("${source}_water", source, "water", map.water),
            fill("${source}_buildings", source, "buildings", map.building, minZoom = BUILDINGS_MIN_ZOOM),
            line("${source}_minor", source, "roads", map.roadMinor, MINOR_ROAD_WIDTH, kindFilter(MINOR_ROADS)),
            line("${source}_major", source, "roads", map.roadMajor, MAJOR_ROAD_WIDTH, kindFilter(MAJOR_ROADS)),
        )
    }

    private fun geoJsonSources(): List<String> = MapSourceIds.ALL.map { id ->
        "\"$id\":{\"type\":\"geojson\",\"data\":{\"type\":\"FeatureCollection\",\"features\":[]}}"
    }

    private fun rideLayers(colors: RideCompColors): List<String> = listOf(
        "{\"id\":\"route\",\"type\":\"line\",\"source\":\"${MapSourceIds.ROUTE}\"," +
            "\"layout\":{\"line-cap\":\"round\",\"line-join\":\"round\"}," +
            "\"paint\":{\"line-color\":${hex(colors.accent)},\"line-width\":$ROUTE_WIDTH}}",
        "{\"id\":\"ridden\",\"type\":\"line\",\"source\":\"${MapSourceIds.RIDDEN}\"," +
            "\"layout\":{\"line-cap\":\"round\",\"line-join\":\"round\"}," +
            "\"paint\":{\"line-color\":${hex(colors.track)},\"line-width\":$ROUTE_WIDTH}}",
        "{\"id\":\"position\",\"type\":\"circle\",\"source\":\"${MapSourceIds.POSITION}\"," +
            "\"paint\":{\"circle-radius\":$POSITION_RADIUS,\"circle-color\":${hex(colors.textPrimary)}," +
            "\"circle-stroke-width\":$POSITION_STROKE,\"circle-stroke-color\":${hex(colors.bg)}}}",
    )

    private fun fill(
        id: String,
        source: String,
        layer: String,
        color: Color,
        filter: String? = null,
        minZoom: Int? = null,
    ) = "{\"id\":\"$id\",\"type\":\"fill\",\"source\":\"$source\",\"source-layer\":\"$layer\"" +
        (filter?.let { ",\"filter\":$it" } ?: "") + (minZoom?.let { ",\"minzoom\":$it" } ?: "") +
        ",\"paint\":{\"fill-color\":${hex(color)}}}"

    private fun line(id: String, source: String, layer: String, color: Color, width: Float, filter: String) =
        "{\"id\":\"$id\",\"type\":\"line\",\"source\":\"$source\",\"source-layer\":\"$layer\",\"filter\":$filter," +
            "\"layout\":{\"line-cap\":\"round\",\"line-join\":\"round\"}," +
            "\"paint\":{\"line-color\":${hex(color)},\"line-width\":$width}}"

    private fun kindFilter(kinds: List<String>): String =
        "[\"in\",[\"get\",\"kind\"],[\"literal\",[${kinds.joinToString(",") { quote(it) }}]]]"

    /** `#RRGGBB` of an opaque token. */
    fun hex(color: Color): String = quote("#%06X".format(color.toArgb() and RGB_MASK))

    /** JSON string literal; region paths come from our own storage but are escaped anyway. */
    fun quote(text: String): String = "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

    private const val RGB_MASK = 0xFFFFFF
    private const val ROUTE_WIDTH = 6f
    private const val MINOR_ROAD_WIDTH = 2f
    private const val MAJOR_ROAD_WIDTH = 4f
    private const val POSITION_RADIUS = 8f
    private const val POSITION_STROKE = 3f
}

/** GeoJSON sources the map composable updates while riding. */
internal object MapSourceIds {
    const val ROUTE = "route"
    const val RIDDEN = "ridden"
    const val POSITION = "position"
    val ALL = listOf(ROUTE, RIDDEN, POSITION)
}
