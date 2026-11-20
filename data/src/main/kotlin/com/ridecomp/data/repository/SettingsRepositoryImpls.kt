package com.ridecomp.data.repository

import com.ridecomp.core.model.AppTheme
import com.ridecomp.core.model.DataPagesLayout
import com.ridecomp.core.model.RideSettings
import com.ridecomp.core.model.RiderProfile
import com.ridecomp.core.repository.ProfileRepository
import com.ridecomp.core.repository.SettingsRepository
import com.ridecomp.data.db.dao.ProfileDao
import com.ridecomp.data.db.dao.SettingsDao
import com.ridecomp.data.db.entity.SettingEntity
import com.ridecomp.data.json.DataPagesJson
import com.ridecomp.data.mapper.SettingsKeys
import com.ridecomp.data.mapper.toDbValue
import com.ridecomp.data.mapper.toDomain
import com.ridecomp.data.mapper.toEntities
import com.ridecomp.data.mapper.toEntity
import com.ridecomp.data.mapper.toEnumOrNull
import com.ridecomp.data.mapper.toRideSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** Room implementation of [ProfileRepository]. */
class ProfileRepositoryImpl(private val dao: ProfileDao) : ProfileRepository {
    override fun observe(): Flow<RiderProfile> = dao.observe().map { it?.toDomain() ?: RiderProfile() }

    override suspend fun get(): RiderProfile = dao.get()?.toDomain() ?: RiderProfile()

    override suspend fun save(profile: RiderProfile) {
        dao.upsert(profile.toEntity())
    }

    override suspend fun hasProfile(): Boolean = dao.get() != null

    override fun observeHasProfile(): Flow<Boolean> = dao.observe().map { it != null }.distinctUntilChanged()
}

/** Room implementation of [SettingsRepository] on the key-value `settings` table (decision D-11). */
class SettingsRepositoryImpl(private val dao: SettingsDao) : SettingsRepository {
    override fun observeRideSettings(): Flow<RideSettings> =
        dao.observeAll().map { it.toRideSettings() }.distinctUntilChanged()

    override suspend fun getRideSettings(): RideSettings = dao.getAll().toRideSettings()

    override suspend fun saveRideSettings(settings: RideSettings) {
        dao.upsertAll(settings.toEntities())
    }

    override fun observeOnboardingDone(): Flow<Boolean> =
        dao.observe(SettingsKeys.ONBOARDING_DONE).map { it.toBoolean() }.distinctUntilChanged()

    override suspend fun setOnboardingDone() {
        dao.upsert(SettingEntity(SettingsKeys.ONBOARDING_DONE, true.toString()))
    }

    override fun observeDataPages(): Flow<DataPagesLayout?> =
        dao.observe(SettingsKeys.DATA_PAGES).map { json -> json?.let(DataPagesJson::decode) }.distinctUntilChanged()

    override fun observeTheme(): Flow<AppTheme> =
        dao.observe(SettingsKeys.THEME).map { it?.toEnumOrNull<AppTheme>() ?: AppTheme.DEFAULT }.distinctUntilChanged()

    override suspend fun setTheme(theme: AppTheme) {
        dao.upsert(SettingEntity(SettingsKeys.THEME, theme.toDbValue()))
    }

    override fun observeMapCacheFilled(): Flow<Boolean> =
        dao.observe(SettingsKeys.MAP_CACHE_FILLED).map { it.toBoolean() }.distinctUntilChanged()

    override suspend fun setMapCacheFilled(isFilled: Boolean) {
        dao.upsert(SettingEntity(SettingsKeys.MAP_CACHE_FILLED, isFilled.toString()))
    }

    override suspend fun saveDataPages(layout: DataPagesLayout?) {
        if (layout == null) {
            dao.delete(SettingsKeys.DATA_PAGES)
        } else {
            dao.upsert(SettingEntity(SettingsKeys.DATA_PAGES, DataPagesJson.encode(layout)))
        }
    }
}
