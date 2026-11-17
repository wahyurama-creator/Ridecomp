package com.ridecomp.data.repository

import com.ridecomp.core.model.BikeComponent
import com.ridecomp.core.model.ComponentType
import com.ridecomp.core.model.ComponentWear
import com.ridecomp.core.repository.BikeComponentRepository
import com.ridecomp.data.db.dao.BikeComponentDao
import com.ridecomp.data.db.entity.BikeComponentEntity
import com.ridecomp.data.mapper.toDbValue
import com.ridecomp.data.mapper.toEnumOrNull
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** Room implementation of [BikeComponentRepository]; wear is summed in SQL from the rides. */
class BikeComponentRepositoryImpl(private val dao: BikeComponentDao) : BikeComponentRepository {
    override fun observeWear(): Flow<List<ComponentWear>> = dao.observeWear()
        .map { rows -> rows.map { ComponentWear(it.component.toDomain(), it.component.initialDistanceM + it.riddenM) } }
        .distinctUntilChanged()

    override suspend fun get(id: Long): BikeComponent? = dao.get(id)?.toDomain()

    override suspend fun save(component: BikeComponent): Long = if (component.id == 0L) {
        dao.insert(component.toEntity())
    } else {
        dao.update(component.toEntity())
        component.id
    }

    override suspend fun delete(id: Long) {
        dao.delete(id)
    }

    override fun observeBikeDistanceM(bikeId: Long): Flow<Double> =
        dao.observeBikeDistanceM(bikeId).distinctUntilChanged()

    private fun BikeComponentEntity.toDomain() = BikeComponent(
        id = id,
        bikeId = bikeId,
        // An unknown type (newer app version) is shown as "other", never dropped.
        type = type.toEnumOrNull<ComponentType>() ?: ComponentType.OTHER,
        name = name,
        installedAtMs = installedAt,
        initialDistanceM = initialDistanceM,
        limitM = limitM,
    )

    private fun BikeComponent.toEntity() = BikeComponentEntity(
        id = id,
        bikeId = bikeId,
        type = type.toDbValue(),
        name = name,
        installedAt = installedAtMs,
        initialDistanceM = initialDistanceM,
        limitM = limitM,
    )
}
