package com.ridecomp.ui.map

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.ridecomp.ui.theme.RideCompColors

/**
 * MapLibre style in RideComp's dark map colors for either the online OpenFreeMap tiles (OpenMapTiles
 * schema: water, landcover, park, building, transportation; D-90) or offline PMTiles regions (Protomaps
 * schema: earth, water, landuse, roads, buildings; D-64). No text layers: labels would need glyph files,
 * and the ride screen shows no street names anyway (12-FEATURE-SPECS §7.2). Route, ridden track and
 * position come from GeoJSON sources updated at runtime ([MapSourceIds]).
 */
internal object OfflineMapStyle {
    private const val PMTILES_SCHEME = "pmtiles://file://"
    private const val SOURCE_PREFIX = "region_"
    private const val ONLINE_SOURCE = "online"
    private const val BUILDINGS_MIN_ZOOM = 14
    private const val ONLINE_BUILDINGS_MIN_ZOOM = 13
    private const val KIND = "kind"
    private const val CLASS = "class"
    private val ONLINE_MINOR_ROADS = listOf("minor", "service", "track", "path", "busway")
    private val ONLINE_MAJOR_ROADS = listOf("motorway", "trunk", "primary", "secondary", "tertiary")
    private val ONLINE_GREEN = listOf("wood", "grass", "forest", "farmland", "wetland")
    private val MINOR_ROADS = listOf("minor_road", "path", "other")
    private val MAJOR_ROADS = listOf("highway", "major_road")
    private val GREEN_LANDUSE =
        listOf("park", "wood", "forest", "nature_reserve", "grass", "garden", "golf_course", "cemetery")

    /**
     * Online tiles when [isOnline]; offline, the online tiles from the cache when [isCached] (T11.11) under one
     * vector source per imported region file (absolute paths in internal storage). With none of them only the
     * ride layers on the background.
     */
    fun json(isOnline: Boolean, isCached: Boolean, regionPaths: List<String>, colors: RideCompColors): String {
        val map = colors.map
        val regions = if (isOnline) emptyList() else regionPaths
        val usesOnlineTiles = isOnline || isCached
        val sources = buildList {
            if (usesOnlineTiles) {
                add(
                    "\"$ONLINE_SOURCE\":{\"type\":\"vector\",\"url\":${quote(MapConfig.ONLINE_TILES_URL)}}",
                )
            }
            regions.forEachIndexed { index, path ->
                add("\"$SOURCE_PREFIX$index\":{\"type\":\"vector\",\"url\":${quote(PMTILES_SCHEME + path)}}")
            }
            addAll(geoJsonSources())
        }
        // Online tiles draw water on land; offline regions draw land (earth) on water.
        val background = if (usesOnlineTiles) map.land else map.water
        val layers = buildList {
            add("{\"id\":\"background\",\"type\":\"background\",\"paint\":{\"background-color\":${hex(background)}}}")
            if (usesOnlineTiles) addAll(onlineLayers(colors))
            regions.indices.forEach { index -> addAll(regionLayers("$SOURCE_PREFIX$index", colors)) }
            addAll(rideLayers(colors))
        }
        return "{\"version\":8,\"sources\":{${sources.joinToString(",")}},\"layers\":[${layers.joinToString(",")}]}"
    }

    private fun regionLayers(source: String, colors: RideCompColors): List<String> {
        val map = colors.map
        return listOf(
            fill("${source}_earth", source, "earth", map.land),
            fill("${source}_park", source, "landuse", map.park, inFilter(KIND, GREEN_LANDUSE)),
            fill("${source}_water", source, "water", map.water),
            fill("${source}_buildings", source, "buildings", map.building, minZoom = BUILDINGS_MIN_ZOOM),
            line("${source}_minor", source, "roads", map.roadMinor, MINOR_ROAD_WIDTH, inFilter(KIND, MINOR_ROADS)),
            line("${source}_major", source, "roads", map.roadMajor, MAJOR_ROAD_WIDTH, inFilter(KIND, MAJOR_ROADS)),
        )
    }

    /** OpenFreeMap layers (OpenMapTiles schema, maxzoom 14, overzoomed above). */
    private fun onlineLayers(colors: RideCompColors): List<String> {
        val map = colors.map
        val source = ONLINE_SOURCE
        return listOf(
            fill("${source}_green", source, "landcover", map.park, inFilter(CLASS, ONLINE_GREEN)),
            fill("${source}_park", source, "park", map.park),
            fill("${source}_water", source, "water", map.water),
            fill("${source}_buildings", source, "building", map.building, minZoom = ONLINE_BUILDINGS_MIN_ZOOM),
            line(
                "${source}_minor",
                source,
                "transportation",
                map.roadMinor,
                MINOR_ROAD_WIDTH,
                inFilter(CLASS, ONLINE_MINOR_ROADS),
            ),
            line(
                "${source}_major",
                source,
                "transportation",
                map.roadMajor,
                MAJOR_ROAD_WIDTH,
                inFilter(CLASS, ONLINE_MAJOR_ROADS),
            ),
        )
    }

    private fun geoJsonSources(): List<String> = MapSourceIds.ALL.map { id ->
        "\"$id\":{\"type\":\"geojson\",\"data\":{\"type\":\"FeatureCollection\",\"features\":[]}}"
    }

    private fun rideLayers(colors: RideCompColors): List<String> = listOf(
        "{\"id\":\"route\",\"type\":\"line\",\"source\":\"${MapSourceIds.ROUTE}\"," +
            "\"layout\":{\"line-cap\":\"round\",\"line-join\":\"round\"}," +
            "\"paint\":{\"line-color\":${hex(colors.accent)},\"line-width\":$ROUTE_WIDTH}}",
        "{\"id\":\"route_gaps\",\"type\":\"line\",\"source\":\"${MapSourceIds.ROUTE_GAPS}\"," +
            "\"paint\":{\"line-color\":${hex(colors.accent)},\"line-width\":$ROUTE_WIDTH," +
            "\"line-dasharray\":[$GAP_DASH,$GAP_SPACE]}}",
        "{\"id\":\"ridden\",\"type\":\"line\",\"source\":\"${MapSourceIds.RIDDEN}\"," +
            "\"layout\":{\"line-cap\":\"round\",\"line-join\":\"round\"}," +
            "\"paint\":{\"line-color\":${hex(colors.track)},\"line-width\":$ROUTE_WIDTH}}",
        "{\"id\":\"marker\",\"type\":\"circle\",\"source\":\"${MapSourceIds.MARKER}\"," +
            "\"paint\":{\"circle-radius\":$POSITION_RADIUS,\"circle-color\":${hex(colors.ok)}," +
            "\"circle-stroke-width\":$POSITION_STROKE,\"circle-stroke-color\":${hex(colors.bg)}}}",
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

    private fun inFilter(attribute: String, values: List<String>): String =
        "[\"in\",[\"get\",\"$attribute\"],[\"literal\",[${values.joinToString(",") { quote(it) }}]]]"

    /** `#RRGGBB` of an opaque token. */
    fun hex(color: Color): String = quote("#%06X".format(color.toArgb() and RGB_MASK))

    /** JSON string literal; region paths come from our own storage but are escaped anyway. */
    fun quote(text: String): String = "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

    private const val RGB_MASK = 0xFFFFFF
    private const val ROUTE_WIDTH = 6f

    /** Dash and space of a line without GPS, in line widths. */
    private const val GAP_DASH = 1f
    private const val GAP_SPACE = 1.5f
    private const val MINOR_ROAD_WIDTH = 2f
    private const val MAJOR_ROAD_WIDTH = 4f
    private const val POSITION_RADIUS = 8f
    private const val POSITION_STROKE = 3f
}

/** GeoJSON sources the map composable updates while riding. */
internal object MapSourceIds {
    const val ROUTE = "route"

    /** Straight lines across stretches without GPS, dashed (D-149). */
    const val ROUTE_GAPS = "route_gaps"
    const val RIDDEN = "ridden"
    const val POSITION = "position"

    /** A place to go back to, e.g. the ride start (T7.2). */
    const val MARKER = "marker"
    val ALL = listOf(ROUTE, ROUTE_GAPS, RIDDEN, POSITION, MARKER)
}

/** The online map (D-90). */
object MapConfig {
    /** OpenFreeMap TileJSON (OpenMapTiles schema, no key; the dated tile path comes from it). */
    const val ONLINE_TILES_URL = "https://tiles.openfreemap.org/planet"
}
