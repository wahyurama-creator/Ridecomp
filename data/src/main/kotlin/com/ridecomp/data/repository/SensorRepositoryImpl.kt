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

    /** Smart trainer (FTMS, T9.1): stored with its kinds, not a kind of its own. */
    const val TRAINER = "ftms"

    /** GloryFit watch (T12.5b): heart rate through the watch's own protocol, also not a kind of its own. */
    const val GLORYFIT_WATCH = "gloryfit"
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

internal fun SensorEntity.toDomain() = SavedSensor(
    address = remoteId,
    name = name,
    kinds = services.toSensorKinds(),
    lastBatteryPercent = lastBattery,
    isAutoConnect = isAutoConnect,
    createdAtMs = createdAt,
    isTrainer = services.hasServiceName(SensorServiceNames.TRAINER),
    isGloryFitWatch = services.hasServiceName(SensorServiceNames.GLORYFIT_WATCH),
)

private fun String.hasServiceName(name: String): Boolean = split(SensorServiceNames.SEPARATOR).any { it.trim() == name }

internal fun SavedSensor.toEntity(existingId: Long = 0) = SensorEntity(
    id = existingId,
    remoteId = address,
    name = name,
    services = (
        listOf(kinds.toServices()) +
            listOfNotNull(
                SensorServiceNames.TRAINER.takeIf { isTrainer },
                SensorServiceNames.GLORYFIT_WATCH.takeIf { isGloryFitWatch },
            )
        ).filter { it.isNotEmpty() }.joinToString(SensorServiceNames.SEPARATOR),
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
