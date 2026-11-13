package com.ridecomp.data.repository

import com.ridecomp.core.model.ActivityProfile
import com.ridecomp.core.repository.ActivityProfileRepository
import com.ridecomp.data.db.dao.ActivityProfileDao
import com.ridecomp.data.db.dao.SettingsDao
import com.ridecomp.data.db.entity.SettingEntity
import com.ridecomp.data.mapper.SettingsKeys
import com.ridecomp.data.mapper.toDomain
import com.ridecomp.data.mapper.toEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** Room implementation of [ActivityProfileRepository]; the last used id lives in `settings`. */
class ActivityProfileRepositoryImpl(private val dao: ActivityProfileDao, private val settingsDao: SettingsDao) :
    ActivityProfileRepository {
    override fun observeAll(): Flow<List<ActivityProfile>> = dao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun get(id: Long): ActivityProfile? = dao.get(id)?.toDomain()

    override suspend fun save(profile: ActivityProfile): Long = if (profile.id == 0L) {
        dao.insert(profile.toEntity())
    } else {
        dao.update(profile.toEntity())
        profile.id
    }

    override suspend fun delete(id: Long) {
        dao.delete(id)
    }

    override fun observeLastUsedId(): Flow<Long?> =
        settingsDao.observe(SettingsKeys.LAST_PROFILE_ID).map { it?.toLongOrNull() }.distinctUntilChanged()

    override suspend fun setLastUsedId(id: Long) {
        settingsDao.upsert(SettingEntity(SettingsKeys.LAST_PROFILE_ID, id.toString()))
    }
}
