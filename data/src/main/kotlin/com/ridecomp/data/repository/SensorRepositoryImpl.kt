package com.ridecomp.data.repository

import com.ridecomp.core.engine.sensors.SavedSensor
import com.ridecomp.core.engine.sensors.SensorKind
import com.ridecomp.core.engine.sensors.SensorRepository
import com.ridecomp.data.db.dao.SensorDao
import com.ridecomp.data.db.entity.SensorEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Stored service names in `sensors.services` (05-DATA-MODEL: CSV `hr,csc,cp`). Stable strings. */
internal object SensorServiceNames {
    const val HEART_RATE = "hr"
    const val SPEED_CADENCE = "csc"
    const val POWER = "cp"
    const val SEPARATOR = ","
}

internal fun Set<SensorKind>.toServices(): String = joinToString(SensorServiceNames.SEPARATOR) { kind ->
    when (kind) {
        SensorKind.HEART_RATE -> SensorServiceNames.HEART_RATE
        SensorKind.SPEED_CADENCE -> SensorServiceNames.SPEED_CADENCE
        SensorKind.POWER -> SensorServiceNames.POWER
    }
}

/** Unknown names are skipped, never crash (ERR-04). */
internal fun String.toSensorKinds(): Set<SensorKind> = split(SensorServiceNames.SEPARATOR).mapNotNull { name ->
    when (name.trim()) {
        SensorServiceNames.HEART_RATE -> SensorKind.HEART_RATE
        SensorServiceNames.SPEED_CADENCE -> SensorKind.SPEED_CADENCE
        SensorServiceNames.POWER -> SensorKind.POWER
        else -> null
    }
}.toSet()

internal fun SensorEntity.toDomain() =
    SavedSensor(remoteId, name, services.toSensorKinds(), lastBattery, isAutoConnect, createdAt)

internal fun SavedSensor.toEntity(existingId: Long = 0) = SensorEntity(
    id = existingId,
    remoteId = address,
    name = name,
    services = kinds.toServices(),
    lastBattery = lastBatteryPercent,
    isAutoConnect = isAutoConnect,
    createdAt = createdAtMs,
)

/** Room implementation of [SensorRepository]. */
class SensorRepositoryImpl(private val dao: SensorDao) : SensorRepository {
    override fun observeAll(): Flow<List<SavedSensor>> = dao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun getAutoConnect(): List<SavedSensor> = dao.getAutoConnect().map { it.toDomain() }

    override suspend fun save(sensor: SavedSensor) {
        dao.upsert(sensor.toEntity(existingId = dao.getByAddress(sensor.address)?.id ?: 0))
    }

    override suspend fun updateBattery(address: String, percent: Int) {
        dao.updateBattery(address, percent)
    }

    override suspend fun forget(address: String) {
        dao.delete(address)
    }
}
