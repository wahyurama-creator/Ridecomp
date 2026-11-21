package com.ridecomp.data.repository

import com.ridecomp.core.engine.sensors.SavedSensor
import com.ridecomp.core.engine.sensors.SensorKind
import kotlin.test.Test
import kotlin.test.assertEquals

class SensorMapperTest {
    @Test
    fun `sensor round trips with services as stable csv`() {
        val sensor = SavedSensor("AA:BB", "Power", setOf(SensorKind.POWER, SensorKind.SPEED_CADENCE), 77, true, 5)

        val entity = sensor.toEntity(existingId = 3)

        assertEquals("cp,csc", entity.services)
        assertEquals(3L, entity.id)
        assertEquals(sensor, entity.toDomain())
    }

    @Test
    fun `a smart trainer keeps its flag next to its kinds`() {
        val trainer = SavedSensor("CC:DD", "KICKR", setOf(SensorKind.POWER, SensorKind.SPEED_CADENCE), isTrainer = true)

        val entity = trainer.toEntity()

        assertEquals("cp,csc,ftms", entity.services)
        assertEquals(trainer, entity.toDomain())
    }

    @Test
    fun `a GloryFit watch is stored as heart rate with its own flag, older versions read it as heart rate`() {
        val watch = SavedSensor("78:02", "Aolon GTR3", setOf(SensorKind.HEART_RATE), isGloryFitWatch = true)

        val entity = watch.toEntity()

        assertEquals("hr,gloryfit", entity.services)
        assertEquals(watch, entity.toDomain())
    }

    @Test
    fun `unknown service names are skipped`() {
        assertEquals(setOf(SensorKind.HEART_RATE), "hr, future,".toSensorKinds())
    }
}
