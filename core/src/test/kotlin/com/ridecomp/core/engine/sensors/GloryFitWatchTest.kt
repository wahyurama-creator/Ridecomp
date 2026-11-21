package com.ridecomp.core.engine.sensors

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Packets recorded from the owner's Aolon GTR 3 (T12.5a, 04-INTEGRATIONS §A.5a). */
class GloryFitWatchTest {
    private fun hex(text: String): ByteArray = text.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    @Test
    fun `heart rate notifications are read, the warm-up zero and other packets are dropped`() {
        assertEquals(74, GloryFitParser.parseHeartRate(hex("fd024a0000000000000000000000"))?.bpm)
        assertEquals(96, GloryFitParser.parseHeartRate(hex("fd02600000000000000000000000"))?.bpm)
        // Warm-up value, command echo, workout summary, hourly history.
        assertNull(GloryFitParser.parseHeartRate(hex("fd02000000000000000000000000")))
        assertNull(GloryFitParser.parseHeartRate(hex("fd110201")))
        assertNull(GloryFitParser.parseHeartRate(hex("e500004b")))
        assertNull(GloryFitParser.parseHeartRate(hex("f70307ea0b150c004b")))
    }

    @Test
    fun `short and implausible packets never crash`() {
        assertNull(GloryFitParser.parseHeartRate(ByteArray(0)))
        assertNull(GloryFitParser.parseHeartRate(hex("fd02")))
        assertNull(GloryFitParser.parseHeartRate(hex("fd02ff")))
        assertNull(GloryFitParser.parseHeartRate(hex("fd021d")))
        assertEquals(230, GloryFitParser.parseHeartRate(hex("fd02e6"))?.bpm)
        assertEquals(30, GloryFitParser.parseHeartRate(hex("fd021e"))?.bpm)
    }

    @Test
    fun `commands match what the GloryFit app sends`() {
        assertContentEquals(hex("fd110201"), GloryFitCommands.START_WORKOUT)
        assertContentEquals(hex("fd000201"), GloryFitCommands.STOP_WORKOUT)
        assertContentEquals(hex("fd440201000001000000000000"), GloryFitCommands.workoutClock(1_000L))
        assertContentEquals(hex("fd440201000102000000000000"), GloryFitCommands.workoutClock(62_500L))
        assertContentEquals(
            hex("fd440201021e05000000000000"),
            GloryFitCommands.workoutClock(
                (2 * 3_600 + 30 * 60 + 5) * 1_000L,
            ),
        )
    }

    @Test
    fun `the last value is held for 30 seconds, then the watch counts as silent`() {
        val held = HeldHeartRate()
        assertNull(held.current(nowMs = 0))

        held.update(HeartRateMeasurement(88, emptyList()), nowMs = 1_000)
        assertEquals(88, held.current(nowMs = 20_800)?.bpm)
        assertEquals(88, held.current(nowMs = 31_000)?.bpm)
        assertNull(held.current(nowMs = 31_001))

        held.update(HeartRateMeasurement(90, emptyList()), nowMs = 40_000)
        assertEquals(90, held.current(nowMs = 41_000)?.bpm)
    }
}
