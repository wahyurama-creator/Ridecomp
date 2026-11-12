package com.ridecomp.core.engine.metrics

import com.ridecomp.core.model.RidingPosition
import com.ridecomp.core.model.Sex
import com.ridecomp.core.model.Surface
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PowerMetricsTest {
    @Test
    fun `steady effort gives equal average and normalized power`() {
        val power = PowerMetrics()

        repeat(60) { power.addMovingSecond(200) }

        assertEquals(200, power.averageW())
        assertEquals(200, power.normalizedW())
        assertEquals(200, power.threeSecondW())
        assertEquals(12.0, power.workKj(), 1e-9)
    }

    @Test
    fun `normalized power needs 30 seconds of data`() {
        val power = PowerMetrics()

        repeat(29) { power.addMovingSecond(250) }

        assertNull(power.normalizedW())
    }

    @Test
    fun `variable effort has normalized power above average`() {
        val power = PowerMetrics()

        repeat(10) {
            repeat(30) { power.addMovingSecond(100) }
            repeat(30) { power.addMovingSecond(350) }
        }

        assertEquals(225, power.averageW())
        assertTrue(requireNotNull(power.normalizedW()) > 240)
        assertEquals(350, power.maxW)
    }

    @Test
    fun `coasting counts as zero in averages`() {
        val power = PowerMetrics()

        power.addMovingSecond(300)
        power.addMovingSecond(null)
        power.addMovingSecond(0)

        assertEquals(100, power.averageW())
        assertEquals(100, power.threeSecondW())
        assertEquals(300, power.maxW)
    }

    @Test
    fun `no data means no values`() {
        val power = PowerMetrics()

        assertNull(power.averageW())
        assertNull(power.threeSecondW())
        assertTrue(!power.hasData)
    }
}

class AverageMaxTest {
    @Test
    fun `invalid and zero values are ignored`() {
        val cadence = AverageMax()

        listOf(90, null, 0, 80).forEach(cadence::add)

        assertEquals(85, cadence.average())
        assertEquals(90, cadence.max)
    }
}

class ZonesTest {
    @Test
    fun `heart rate zones by percent of hr max`() {
        assertEquals(1, Zones.heartRate(110, 200))
        assertEquals(2, Zones.heartRate(120, 200))
        assertEquals(3, Zones.heartRate(150, 200))
        assertEquals(5, Zones.heartRate(185, 200))
        assertNull(Zones.heartRate(150, null))
    }

    @Test
    fun `power zones by percent of ftp with inclusive upper bounds`() {
        assertEquals(1, Zones.power(137, 250))
        assertEquals(5, Zones.power(300, 250))
        assertEquals(6, Zones.power(301, 250))
        assertEquals(7, Zones.power(400, 250))
        assertNull(Zones.power(200, null))
    }
}

class EstimatedPowerModelTest {
    private fun input(kmh: Double, grade: Double = 0.0, accel: Double = 0.0) = PowerModelInput(
        totalMassKg = 84.0,
        speedMps = kmh / 3.6,
        accelerationMps2 = accel,
        gradePercent = grade,
        surface = Surface.ROAD,
        position = RidingPosition.HOODS,
    )

    @Test
    fun `flat 30 kmh on the hoods is in a plausible range`() {
        // 07-TEST-PLAN §3: roughly 150–200 W for a 75 kg rider (here 75 + 9 kg bike).
        assertTrue(EstimatedPowerModel.watts(input(30.0)) in 140..200)
    }

    @Test
    fun `climbing needs more power and descending needs none`() {
        assertTrue(EstimatedPowerModel.watts(input(15.0, grade = 6.0)) > EstimatedPowerModel.watts(input(15.0)))
        assertEquals(0, EstimatedPowerModel.watts(input(40.0, grade = -6.0)))
    }

    @Test
    fun `gravel and drops change the estimate`() {
        val road = EstimatedPowerModel.watts(input(25.0))
        val gravel = EstimatedPowerModel.watts(input(25.0).copy(surface = Surface.GRAVEL))
        val drops = EstimatedPowerModel.watts(input(25.0).copy(position = RidingPosition.DROPS))

        assertTrue(gravel > road)
        assertTrue(drops < road)
    }
}

class KeytelCaloriesTest {
    @Test
    fun `male and female formulas`() {
        val male = KeytelCalories.kcalPerSecond(150, CalorieProfile(70.0, 30, Sex.MALE))
        val female = KeytelCalories.kcalPerSecond(150, CalorieProfile(60.0, 30, Sex.FEMALE))

        assertEquals(14.22 / 60, requireNotNull(male), 0.001)
        assertTrue(requireNotNull(female) > 0.0)
    }

    @Test
    fun `missing age or sex gives no estimate`() {
        assertNull(KeytelCalories.kcalPerSecond(150, CalorieProfile(70.0, null, Sex.MALE)))
        assertNull(KeytelCalories.kcalPerSecond(150, CalorieProfile(70.0, 30, null)))
    }
}
