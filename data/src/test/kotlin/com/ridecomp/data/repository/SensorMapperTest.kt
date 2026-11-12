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
    fun `unknown service names are skipped`() {
        assertEquals(setOf(SensorKind.HEART_RATE), "hr, future,".toSensorKinds())
    }
}
