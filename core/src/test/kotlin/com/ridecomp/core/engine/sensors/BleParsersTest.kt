package com.ridecomp.core.engine.sensors

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }

class HeartRateParserTest {
    @Test
    fun `uint8 heart rate`() {
        assertEquals(HeartRateMeasurement(72, emptyList()), HeartRateParser.parse(bytes(0x00, 72)))
    }

    @Test
    fun `uint16 heart rate with rr intervals`() {
        // flags: uint16 + RR present; HR 0x00A0 = 160; RR 1024 ticks = 1000 ms, 512 = 500 ms.
        val parsed = HeartRateParser.parse(bytes(0x11, 0xA0, 0x00, 0x00, 0x04, 0x00, 0x02))

        assertEquals(HeartRateMeasurement(160, listOf(1_000, 500)), parsed)
    }

    @Test
    fun `energy expended is skipped`() {
        assertEquals(150, HeartRateParser.parse(bytes(0x08, 150, 0x10, 0x00))?.bpm)
    }

    @Test
    fun `zero and implausible heart rates are ignored`() {
        assertNull(HeartRateParser.parse(bytes(0x00, 0)))
        assertNull(HeartRateParser.parse(bytes(0x01, 0xF5, 0x00)))
    }

    @Test
    fun `short packets are rejected`() {
        assertNull(HeartRateParser.parse(bytes()))
        assertNull(HeartRateParser.parse(bytes(0x01, 0x50)))
        assertNull(HeartRateParser.parse(bytes(0x08, 120, 0x01)))
    }
}

class CscParserTest {
    @Test
    fun `wheel only`() {
        val parsed = CscParser.parse(bytes(0x01, 0x10, 0x00, 0x00, 0x00, 0x00, 0x04))

        assertEquals(RevolutionData(16, 1_024, 1_024), parsed?.wheel)
        assertNull(parsed?.crank)
    }

    @Test
    fun `crank only`() {
        val parsed = CscParser.parse(bytes(0x02, 0x05, 0x00, 0x00, 0x08))

        assertNull(parsed?.wheel)
        assertEquals(RevolutionData(5, 2_048, 1_024), parsed?.crank)
    }

    @Test
    fun `wheel and crank`() {
        val parsed = assertNotNull(CscParser.parse(bytes(0x03, 1, 0, 0, 0, 2, 0, 3, 0, 4, 0)))

        assertEquals(1L, parsed.wheel?.revolutions)
        assertEquals(3L, parsed.crank?.revolutions)
    }

    @Test
    fun `truncated packet is rejected`() {
        assertNull(CscParser.parse(bytes(0x03, 1, 0, 0, 0, 2, 0, 3)))
    }
}

class CyclingPowerParserTest {
    @Test
    fun `power only`() {
        assertEquals(PowerMeasurement(250, null, null, null), CyclingPowerParser.parse(bytes(0x00, 0x00, 0xFA, 0x00)))
    }

    @Test
    fun `pedal balance and crank data after optional fields`() {
        // flags: balance (bit0) + torque (bit2) + crank (bit5) = 0x25.
        val parsed = CyclingPowerParser.parse(bytes(0x25, 0x00, 0xC8, 0x00, 100, 0x10, 0x00, 0x0A, 0x00, 0x00, 0x04))

        assertEquals(200, parsed?.powerW)
        assertEquals(50.0, parsed?.pedalBalancePercent)
        assertEquals(RevolutionData(10, 1_024, 1_024), parsed?.crank)
    }

    @Test
    fun `wheel data uses 1 over 2048 s and crank follows`() {
        // flags: wheel (bit4) + crank (bit5) = 0x30.
        val parsed =
            assertNotNull(CyclingPowerParser.parse(bytes(0x30, 0x00, 0x64, 0x00, 7, 0, 0, 0, 0, 0x08, 2, 0, 0, 0x04)))

        assertEquals(RevolutionData(7, 2_048, 2_048), parsed.wheel)
        assertEquals(RevolutionData(2, 1_024, 1_024), parsed.crank)
    }

    @Test
    fun `negative power is rejected`() {
        assertNull(CyclingPowerParser.parse(bytes(0x00, 0x00, 0xF6, 0xFF)))
    }

    @Test
    fun `battery level within range`() {
        assertEquals(87, BatteryLevelParser.parse(bytes(87)))
        assertNull(BatteryLevelParser.parse(bytes(150)))
        assertNull(BatteryLevelParser.parse(bytes()))
    }
}

class RevolutionCalculatorTest {
    @Test
    fun `cadence from crank events`() {
        val cadence = CadenceCalculator()
        assertNull(cadence.add(RevolutionData(10, 0, 1_024), 0))

        // 1 revolution in 0.75 s = 80 rpm.
        assertEquals(80.0, cadence.add(RevolutionData(11, 768, 1_024), 750))
    }

    @Test
    fun `crank time rollover produces the correct cadence`() {
        val cadence = CadenceCalculator()
        cadence.add(RevolutionData(65_535, 65_000, 1_024), 0)

        // revs 65535 -> 0 (+1), time 65000 -> 488 (+1024 ticks = 1 s) => 60 rpm.
        assertEquals(60.0, cadence.add(RevolutionData(0, 488, 1_024), 1_000))
    }

    @Test
    fun `wheel revolutions rollover uint32`() {
        val speed = WheelSpeedCalculator(circumferenceM = 2.105)
        speed.add(RevolutionData(0xFFFF_FFFFL, 0, 1_024), 0)

        // +2 revolutions in 0.5 s => 4 rev/s * 2.105 m.
        assertEquals(8.42, requireNotNull(speed.add(RevolutionData(1, 512, 1_024), 500)), 1e-9)
    }

    @Test
    fun `no new event for more than 3 s means zero`() {
        val cadence = CadenceCalculator()
        cadence.add(RevolutionData(1, 0, 1_024), 0)
        cadence.add(RevolutionData(2, 1_024, 1_024), 1_000)

        assertEquals(60.0, cadence.add(RevolutionData(2, 1_024, 1_024), 2_000))
        assertEquals(0.0, cadence.add(RevolutionData(2, 1_024, 1_024), 4_100))
        assertEquals(0.0, cadence.current(10_000))
    }

    @Test
    fun `implausible spikes are dropped`() {
        val cadence = CadenceCalculator()
        cadence.add(RevolutionData(1, 0, 1_024), 0)
        // 10 revolutions in 1 s = 600 rpm.
        assertNull(cadence.add(RevolutionData(11, 1_024, 1_024), 1_000))

        val speed = WheelSpeedCalculator(circumferenceM = 2.105)
        speed.add(RevolutionData(0, 0, 1_024), 0)
        // 30 revolutions in 1 s ≈ 227 km/h.
        assertNull(speed.add(RevolutionData(30, 1_024, 1_024), 1_000))
    }

    @Test
    fun `reset forgets the previous counter`() {
        val cadence = CadenceCalculator()
        cadence.add(RevolutionData(100, 0, 1_024), 0)
        cadence.reset()

        assertNull(cadence.add(RevolutionData(3, 500, 1_024), 1_000))
    }
}

/** S1: random and truncated packets never throw (fixed seed, ≥ 10 000 iterations). */
class BleParserFuzzTest {
    @Test
    fun `parsers survive random bytes`() {
        val random = Random(SEED)
        repeat(ITERATIONS) {
            val packet = random.nextBytes(random.nextInt(MAX_PACKET + 1))
            HeartRateParser.parse(packet)
            CscParser.parse(packet)
            CyclingPowerParser.parse(packet)
            BatteryLevelParser.parse(packet)
        }
    }

    private companion object {
        const val SEED = 20_261_113
        const val ITERATIONS = 10_000
        const val MAX_PACKET = 24
    }
}
