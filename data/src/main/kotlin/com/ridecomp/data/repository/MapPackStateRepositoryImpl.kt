package com.ridecomp.data.repository

import com.ridecomp.core.repository.MapPackStateRepository
import com.ridecomp.data.db.dao.SettingsDao
import com.ridecomp.data.db.entity.SettingEntity
import com.ridecomp.data.mapper.SettingsKeys
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** Region pack state in the `settings` table as small JSON values (T11.12, no migration). */
class MapPackStateRepositoryImpl(private val dao: SettingsDao) : MapPackStateRepository {
    override fun observeInstalled(): Flow<Map<String, String>> =
        dao.observe(SettingsKeys.MAP_PACKS_INSTALLED).map(::decodeMap).distinctUntilChanged()

    override suspend fun setInstalled(regionId: String, version: String?) {
        val current = decodeMap(dao.get(SettingsKeys.MAP_PACKS_INSTALLED)).toMutableMap()
        if (version == null) current.remove(regionId) else current[regionId] = version
        dao.upsert(SettingEntity(SettingsKeys.MAP_PACKS_INSTALLED, JSONObject(current.toMap()).toString()))
    }

    override fun observeDismissed(): Flow<Set<String>> =
        dao.observe(SettingsKeys.MAP_PACKS_DISMISSED).map(::decodeSet).distinctUntilChanged()

    override suspend fun dismiss(offerKey: String) {
        val current = decodeSet(dao.get(SettingsKeys.MAP_PACKS_DISMISSED)) + offerKey
        dao.upsert(SettingEntity(SettingsKeys.MAP_PACKS_DISMISSED, JSONArray(current.toList()).toString()))
    }

    override fun observeMobileDataAllowed(): Flow<Boolean> =
        dao.observe(SettingsKeys.MAP_PACKS_MOBILE_DATA).map { it.toBoolean() }.distinctUntilChanged()

    override suspend fun setMobileDataAllowed(isAllowed: Boolean) {
        dao.upsert(SettingEntity(SettingsKeys.MAP_PACKS_MOBILE_DATA, isAllowed.toString()))
    }

    /** An unreadable value counts as empty: at worst a region is offered again. */
    private fun decodeMap(json: String?): Map<String, String> = try {
        json?.let { text ->
            val obj = JSONObject(text)
            obj.keys().asSequence().associateWith { obj.getString(it) }
        }.orEmpty()
    } catch (_: JSONException) {
        emptyMap()
    }

    private fun decodeSet(json: String?): Set<String> = try {
        json?.let { text ->
            val array = JSONArray(text)
            (0 until array.length()).map(array::getString).toSet()
        }.orEmpty()
    } catch (_: JSONException) {
        emptySet()
    }
}
