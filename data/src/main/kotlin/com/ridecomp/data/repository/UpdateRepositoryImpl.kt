package com.ridecomp.data.repository

import com.ridecomp.core.engine.update.AppRelease
import com.ridecomp.core.engine.update.UpdatePreferences
import com.ridecomp.core.usecase.UpdateRepository
import com.ridecomp.data.db.dao.SettingsDao
import com.ridecomp.data.db.entity.SettingEntity
import com.ridecomp.data.mapper.SettingsKeys
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** Update check state as settings rows (T10.9); a missing or unreadable row falls back to the default. */
class UpdateRepositoryImpl(private val dao: SettingsDao) : UpdateRepository {
    override fun observe(): Flow<UpdatePreferences> =
        dao.observeAll().map { rows -> parse(rows.associate { it.key to it.value }) }.distinctUntilChanged()

    override suspend fun get(): UpdatePreferences = parse(dao.getAll().associate { it.key to it.value })

    override suspend fun setAutoCheck(isEnabled: Boolean) {
        dao.upsert(SettingEntity(SettingsKeys.UPDATE_AUTO_CHECK, isEnabled.toString()))
    }

    override suspend fun saveCheck(atMs: Long, latest: AppRelease) {
        dao.upsertAll(
            listOf(
                SettingEntity(SettingsKeys.UPDATE_LAST_CHECK_MS, atMs.toString()),
                SettingEntity(SettingsKeys.UPDATE_LATEST_VERSION, latest.version),
                SettingEntity(SettingsKeys.UPDATE_LATEST_URL, latest.pageUrl),
            ),
        )
    }

    override suspend fun dismiss(version: String) {
        dao.upsert(SettingEntity(SettingsKeys.UPDATE_DISMISSED_VERSION, version))
    }

    private fun parse(rows: Map<String, String?>): UpdatePreferences {
        val version = rows[SettingsKeys.UPDATE_LATEST_VERSION]
        val url = rows[SettingsKeys.UPDATE_LATEST_URL]
        return UpdatePreferences(
            isAutoCheckEnabled = rows[SettingsKeys.UPDATE_AUTO_CHECK]?.toBooleanStrictOrNull() ?: true,
            lastCheckMs = rows[SettingsKeys.UPDATE_LAST_CHECK_MS]?.toLongOrNull(),
            latest = if (version != null && url != null) AppRelease(version, url) else null,
            dismissedVersion = rows[SettingsKeys.UPDATE_DISMISSED_VERSION],
        )
    }
}
