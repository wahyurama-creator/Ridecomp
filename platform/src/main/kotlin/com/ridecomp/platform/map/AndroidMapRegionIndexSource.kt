package com.ridecomp.platform.map

import android.content.Context
import com.ridecomp.core.engine.map.MapBounds
import com.ridecomp.core.engine.map.MapPackFile
import com.ridecomp.core.engine.map.MapRegion
import com.ridecomp.core.engine.map.MapRegionIndex
import com.ridecomp.core.source.DispatcherProvider
import com.ridecomp.core.source.Logger
import com.ridecomp.core.source.MapRegionIndexSource
import java.io.IOException
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject

/**
 * The region index in the APK (`assets/maps/regions.json`, written by `scripts/maps/build-regions`, T11.13).
 * Read once per process; a broken file gives an empty index (nothing offered) instead of a crash.
 */
class AndroidMapRegionIndexSource(
    private val context: Context,
    private val dispatchers: DispatcherProvider,
    private val logger: Logger,
) : MapRegionIndexSource {
    @Volatile
    private var cached: MapRegionIndex? = null

    override suspend fun load(): MapRegionIndex = cached ?: withContext(dispatchers.io) {
        val index = try {
            parse(context.assets.open(INDEX_ASSET).bufferedReader().use { it.readText() })
        } catch (error: IOException) {
            logger.warn(TAG, error) { "Map region index missing" }
            MapRegionIndex.EMPTY
        } catch (error: JSONException) {
            logger.warn(TAG, error) { "Map region index unreadable" }
            MapRegionIndex.EMPTY
        }
        index.also { cached = it }
    }

    private fun parse(json: String): MapRegionIndex {
        val root = JSONObject(json)
        val regions = root.getJSONArray(REGIONS)
        return MapRegionIndex(
            schema = root.getInt(SCHEMA),
            version = root.getString(VERSION),
            regions = (0 until regions.length()).map { region(regions.getJSONObject(it)) },
        )
    }

    private fun region(json: JSONObject): MapRegion {
        val bbox = json.getJSONArray(BBOX)
        val files = json.getJSONArray(FILES)
        return MapRegion(
            id = json.getString(ID),
            name = json.getString(NAME),
            bounds = MapBounds(
                bbox.getDouble(MIN_LON),
                bbox.getDouble(MIN_LAT),
                bbox.getDouble(MAX_LON),
                bbox.getDouble(MAX_LAT),
            ),
            files = (0 until files.length()).map { file(files.getJSONObject(it)) },
        )
    }

    private fun file(json: JSONObject) = MapPackFile(
        name = json.getString(NAME),
        url = json.getString(URL),
        sizeBytes = json.getLong(SIZE_BYTES),
        sha256 = json.getString(SHA256),
    )

    private companion object {
        const val TAG = "MapPacks"
        const val INDEX_ASSET = "maps/regions.json"
        const val SCHEMA = "schema"
        const val VERSION = "version"
        const val REGIONS = "regions"
        const val ID = "id"
        const val NAME = "name"
        const val BBOX = "bbox"
        const val FILES = "files"
        const val URL = "url"
        const val SIZE_BYTES = "sizeBytes"
        const val SHA256 = "sha256"

        // bbox order of the index: min_lon, min_lat, max_lon, max_lat.
        const val MIN_LON = 0
        const val MIN_LAT = 1
        const val MAX_LON = 2
        const val MAX_LAT = 3
    }
}
