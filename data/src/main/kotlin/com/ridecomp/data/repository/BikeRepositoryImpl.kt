package com.ridecomp.data.repository

import com.ridecomp.core.model.Bike
import com.ridecomp.core.repository.BikeRepository
import com.ridecomp.data.db.dao.BikeDao
import com.ridecomp.data.db.dao.SettingsDao
import com.ridecomp.data.db.entity.SettingEntity
import com.ridecomp.data.mapper.SettingsKeys
import com.ridecomp.data.mapper.toDomain
import com.ridecomp.data.mapper.toEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** Room implementation of [BikeRepository]; the last used id lives in `settings`. */
class BikeRepositoryImpl(private val dao: BikeDao, private val settingsDao: SettingsDao) : BikeRepository {
    override fun observeAll(): Flow<List<Bike>> = dao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun get(id: Long): Bike? = dao.get(id)?.toDomain()

    override suspend fun save(bike: Bike): Long = if (bike.id == 0L) {
        dao.insert(bike.toEntity())
    } else {
        dao.update(bike.toEntity())
        bike.id
    }

    override suspend fun delete(id: Long) {
        dao.delete(id)
    }

    override fun observeLastUsedId(): Flow<Long?> =
        settingsDao.observe(SettingsKeys.LAST_BIKE_ID).map { it?.toLongOrNull() }.distinctUntilChanged()

    override suspend fun setLastUsedId(id: Long) {
        settingsDao.upsert(SettingEntity(SettingsKeys.LAST_BIKE_ID, id.toString()))
    }
}
