package com.ridecomp.core.testing

import com.ridecomp.core.engine.map.MapBounds
import com.ridecomp.core.engine.map.MapImportError
import com.ridecomp.core.engine.map.OfflineMap
import com.ridecomp.core.engine.map.PmTilesInfo
import com.ridecomp.core.repository.OfflineMapRepository
import com.ridecomp.core.util.Outcome
import com.ridecomp.core.util.asFailure
import com.ridecomp.core.util.asSuccess
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** In-memory maps; [failure] makes the next imports fail. */
public class FakeOfflineMapRepository(public var freeSpace: Long = Long.MAX_VALUE) : OfflineMapRepository {
    private val maps = MutableStateFlow<List<OfflineMap>>(emptyList())
    public val names: MutableMap<String, String> = mutableMapOf()
    public var failure: MapImportError? = null

    override fun observeAll(): Flow<List<OfflineMap>> = maps

    override suspend fun import(uri: String, name: String): Outcome<OfflineMap, MapImportError> {
        failure?.let { return it.asFailure() }
        val map = OfflineMap(name, SIZE, PmTilesInfo(MapBounds(0.0, 0.0, 1.0, 1.0), 0, MAX_ZOOM), 0, "/maps/$name")
        maps.value = maps.value.filterNot { it.name == name } + map
        return map.asSuccess()
    }

    override suspend fun delete(name: String) {
        maps.value = maps.value.filterNot { it.name == name }
    }

    override suspend fun freeBytes(): Long = freeSpace

    override fun displayName(uri: String): String? = names[uri]

    private companion object {
        const val SIZE = 1_000L
        const val MAX_ZOOM = 14
    }
}
