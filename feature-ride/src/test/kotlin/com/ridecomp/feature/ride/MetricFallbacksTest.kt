package com.ridecomp.feature.ride

import com.ridecomp.core.engine.sensors.SensorKind
import com.ridecomp.feature.ride.model.RideMetric
import com.ridecomp.feature.ride.model.withFallbacks
import kotlin.test.Test
import kotlin.test.assertEquals

class MetricFallbacksTest {
    private val main = listOf(
        RideMetric.SPEED,
        RideMetric.DISTANCE,
        RideMetric.MOVING_TIME,
        RideMetric.HEART_RATE,
        RideMetric.CADENCE,
        RideMetric.POWER_3S,
        RideMetric.AVG_SPEED,
    )

    @Test
    fun `without sensors the sensor fields show phone metrics, power its estimate`() {
        assertEquals(
            listOf(
                RideMetric.SPEED,
                RideMetric.DISTANCE,
                RideMetric.MOVING_TIME,
                RideMetric.ASCENT,
                RideMetric.MAX_SPEED,
                RideMetric.ESTIMATED_POWER,
                RideMetric.AVG_SPEED,
            ),
            main.withFallbacks(emptySet()),
        )
    }

    @Test
    fun `paired sensors keep their fields, a power meter also gives cadence`() {
        assertEquals(main, main.withFallbacks(setOf(SensorKind.HEART_RATE, SensorKind.POWER)))
        assertEquals(
            listOf(RideMetric.ASCENT, RideMetric.CADENCE),
            listOf(RideMetric.HEART_RATE, RideMetric.CADENCE).withFallbacks(setOf(SensorKind.SPEED_CADENCE)),
        )
    }

    @Test
    fun `a substitute already on the page is skipped`() {
        assertEquals(
            listOf(RideMetric.ASCENT, RideMetric.MAX_SPEED),
            listOf(RideMetric.ASCENT, RideMetric.AVG_HEART_RATE).withFallbacks(emptySet()),
        )
    }
}
