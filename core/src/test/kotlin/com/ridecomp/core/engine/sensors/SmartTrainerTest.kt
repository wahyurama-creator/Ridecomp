package com.ridecomp.core.engine.sensors

import com.ridecomp.core.model.Intensity
import com.ridecomp.core.model.PlanSession
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SmartTrainerTest {
    private fun le16(value: Int) = byteArrayOf((value and 0xFF).toByte(), (value shr 8 and 0xFF).toByte())

    @Test
    fun `indoor bike data with speed, cadence, resistance, power and heart rate`() {
        // Flags: cadence (2), resistance (5), power (6), HR (9); speed present (bit 0 clear).
        val flags = (1 shl 2) or (1 shl 5) or (1 shl 6) or (1 shl 9)
        val packet = le16(flags) + le16(3_250) + le16(176) + le16(12) + le16(215) + byteArrayOf(142.toByte())

        assertEquals(IndoorBikeData(32.5, 88.0, 215, 142), IndoorBikeDataParser.parse(packet))
    }

    @Test
    fun `more-data packets carry no speed and short or absurd packets are dropped`() {
        val moreData = le16(1 or (1 shl 6)) + le16(180)
        assertEquals(IndoorBikeData(null, null, 180, null), IndoorBikeDataParser.parse(moreData))

        assertNull(IndoorBikeDataParser.parse(le16(1 shl 6)))
        assertNull(IndoorBikeDataParser.parse(byteArrayOf(0)))
        assertNull(IndoorBikeDataParser.parse(le16(1 or (1 shl 6)) + le16(9_000))?.powerW)
    }

    @Test
    fun `ERG commands follow the FTMS control point format`() {
        assertContentEquals(byteArrayOf(0x00), FtmsCommands.REQUEST_CONTROL)
        assertContentEquals(byteArrayOf(0x07), FtmsCommands.START)
        assertContentEquals(byteArrayOf(0x05, 0x2C, 0x01), FtmsCommands.setTargetPower(300))
    }

    @Test
    fun `steady sessions start at their zone, interval sessions in zone 2, nothing without FTP`() {
        assertEquals(130, ErgConfig.startWatts(PlanSession(Intensity.ENDURANCE, 60), 200))
        assertEquals(166, ErgConfig.startWatts(PlanSession(Intensity.TEMPO, 60), 200))
        assertEquals(130, ErgConfig.startWatts(PlanSession(Intensity.VO2MAX, 60), 200))
        assertEquals(130, ErgConfig.startWatts(null, 200))
        assertNull(ErgConfig.startWatts(PlanSession(Intensity.ENDURANCE, 60), null))
        assertEquals(196, ErgConfig.watts(Intensity.THRESHOLD, 200))
    }

    @Test
    fun `the target stays within limits and is released when the link drops`() {
        val control = TrainerControl()
        control.adjust(ErgConfig.STEP_W)
        assertEquals(ErgConfig.DEFAULT_W + ErgConfig.STEP_W, control.state.value.targetW)
        control.setTarget(5_000)
        assertEquals(ErgConfig.MAX_W, control.state.value.targetW)
        control.adjust(-10_000)
        assertEquals(ErgConfig.MIN_W, control.state.value.targetW)

        control.onLink(isConnected = true, isControlled = true)
        assertEquals(true, control.state.value.isControlled)
        control.onLink(isConnected = false, isControlled = true)
        assertEquals(
            TrainerState(isConnected = false, isControlled = false, targetW = ErgConfig.MIN_W),
            control.state.value,
        )
    }

    @Test
    fun `fusion uses the trainer for power, cadence and speed unless the bike has its own sensors`() {
        val fusion = SensorFusion(wheelCircumferenceM = 2.1)
        fusion.onReading(SensorReading.Trainer(IndoorBikeData(36.0, 90.0, 200, null), 1_000))

        val snapshot = fusion.snapshot(1_500)
        assertEquals(200, snapshot.powerW)
        assertEquals(90, snapshot.cadenceRpm)
        assertEquals(10.0, snapshot.wheelSpeedMps!!, 1e-9)

        fusion.onReading(SensorReading.Power(PowerMeasurement(185, null, null, null), 1_200))
        assertEquals(185, fusion.snapshot(1_500).powerW)
        assertNull(fusion.snapshot(10_000).powerW)
    }
}
