package com.ridecomp.core.engine.export

import com.ridecomp.core.model.FitSportValues
import com.ridecomp.core.model.Lap
import com.ridecomp.core.model.LapTrigger
import com.ridecomp.core.model.RideSample
import com.ridecomp.core.model.RideTotals
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private const val START_MS = 1_767_225_600_000L

class FitWriterTest {
    private fun ride(samples: List<RideSample>, laps: List<Lap> = emptyList()): List<FitMessage> {
        val writer = FitActivityWriter(START_MS, serialNumber = 42)
        writer.addTimerEvent(START_MS, isStart = true)
        samples.forEach(writer::addRecord)
        writer.addTimerEvent(samples.last().timeMs, isStart = false)
        laps.forEach(writer::addLap)
        val bytes = writer.finish(
            RideTotals(
                elapsedS = 60,
                movingS = 55,
                distanceM = 480.0,
                ascentM = 3.4,
                avgSpeedMps = 8.7,
                maxSpeedMps = 11.2,
            ),
            endTimeMs = samples.last().timeMs,
            utcOffsetS = 7 * 3_600,
        )
        return FitDecoder.decode(bytes)
    }

    private fun sample(second: Long, power: Int? = null, estPower: Int? = null, hr: Int? = null) = RideSample(
        timeMs = START_MS + second * 1_000,
        latDeg = -7.7956,
        lonDeg = 110.3695,
        altitudeM = 113.6,
        gpsAccuracyM = 4.0,
        distanceM = second * 8.0,
        speedMps = 8.0,
        heartRateBpm = hr,
        powerW = power,
        estPowerW = estPower,
    )

    @Test
    fun `fit epoch and semicircles conversions`() {
        assertEquals(0L, FitActivityWriter.fitTime(FitProfile.FIT_EPOCH_OFFSET_S * 1_000))
        assertEquals(1_073_741_824L, FitActivityWriter.semicircles(90.0))
        assertEquals(-1_073_741_824L, FitActivityWriter.semicircles(-90.0))
        assertEquals(Int.MAX_VALUE.toLong(), FitActivityWriter.semicircles(180.0))
    }

    @Test
    fun `file has a valid header, CRCs and the activity message order`() {
        val messages = ride((0L..10L).map { sample(it) })

        val order = messages.map { it.global }.distinct()
        assertEquals(
            listOf(
                FitProfile.Message.FILE_ID,
                FitProfile.Message.EVENT,
                FitProfile.Message.RECORD,
                FitProfile.Message.SESSION,
                FitProfile.Message.ACTIVITY,
            ),
            order,
        )
        assertEquals(11, messages.count { it.global == FitProfile.Message.RECORD })
    }

    @Test
    fun `record fields use FIT units`() {
        val record = ride(listOf(sample(0), sample(1))).last { it.global == FitProfile.Message.RECORD }.fields

        assertEquals(FitActivityWriter.fitTime(START_MS + 1_000), record[FitProfile.Common.TIMESTAMP])
        assertEquals(FitActivityWriter.semicircles(-7.7956), FitDecoder.signed32(requireNotNull(record[0])).toLong())
        assertEquals(800L, record[FitProfile.Record.DISTANCE])
        assertEquals(8_000L, record[FitProfile.Record.ENHANCED_SPEED])
        assertEquals(((113.6 + 500) * 5).toLong(), record[FitProfile.Record.ENHANCED_ALTITUDE])
    }

    @Test
    fun `missing values are written as invalid, not zero`() {
        val record = ride(listOf(sample(0))).first { it.global == FitProfile.Message.RECORD }.fields

        assertEquals(FitBaseType.UINT8.invalid, record[FitProfile.Record.HEART_RATE])
        assertEquals(FitBaseType.UINT16.invalid, record[FitProfile.Record.POWER])
        assertEquals(FitBaseType.SINT8.invalid, record[FitProfile.Record.TEMPERATURE])
    }

    @Test
    fun `estimated power never goes to the power field`() {
        val record = ride(listOf(sample(0, estPower = 180))).first { it.global == FitProfile.Message.RECORD }.fields

        assertEquals(FitBaseType.UINT16.invalid, record[FitProfile.Record.POWER])
    }

    @Test
    fun `real power meter data is written`() {
        val record = ride(listOf(sample(0, power = 245, hr = 150))).first {
            it.global == FitProfile.Message.RECORD
        }.fields

        assertEquals(245L, record[FitProfile.Record.POWER])
        assertEquals(150L, record[FitProfile.Record.HEART_RATE])
    }

    @Test
    fun `timer events start and stop the timer`() {
        val events = ride((0L..3L).map { sample(it) }).filter { it.global == FitProfile.Message.EVENT }

        assertEquals(
            listOf(FitProfile.Event.TYPE_START, FitProfile.Event.TYPE_STOP_ALL),
            events.map { it.fields[FitProfile.Event.FIELD_EVENT_TYPE] },
        )
        assertTrue(events.all { it.fields[FitProfile.Event.FIELD_EVENT] == FitProfile.Event.TIMER })
    }

    @Test
    fun `session and activity carry the totals`() {
        val lap = Lap(0, START_MS, START_MS + 60_000, 480.0, 55, 8.7, 11.2, 3.0, LapTrigger.DISTANCE)
        val messages = ride((0L..60L).map { sample(it) }, laps = listOf(lap))

        val session = messages.single { it.global == FitProfile.Message.SESSION }.fields
        assertEquals(FitSportValues.SPORT_CYCLING.toLong(), session[FitProfile.Session.SPORT])
        assertEquals(48_000L, session[FitProfile.Session.TOTAL_DISTANCE])
        assertEquals(55_000L, session[FitProfile.Session.TOTAL_TIMER_TIME])
        assertEquals(60_000L, session[FitProfile.Session.TOTAL_ELAPSED_TIME])
        assertEquals(1L, session[FitProfile.Session.NUM_LAPS])
        assertEquals(FitBaseType.UINT16.invalid, session[FitProfile.Session.NORMALIZED_POWER])
        val fitLap = messages.single { it.global == FitProfile.Message.LAP }.fields
        assertEquals(FitProfile.Lap.TRIGGER_DISTANCE, fitLap[FitProfile.Lap.LAP_TRIGGER])
        val activity = messages.single { it.global == FitProfile.Message.ACTIVITY }.fields
        assertEquals(1L, activity[FitProfile.Activity.NUM_SESSIONS])
        assertEquals(
            requireNotNull(activity[FitProfile.Common.TIMESTAMP]) + 7 * 3_600,
            activity[FitProfile.Activity.LOCAL_TIMESTAMP],
        )
    }

    @Test
    fun `crc over a file including its crc is zero`() {
        val bytes = FitEncoder().toFile()

        assertEquals(0, FitCrc.of(bytes))
        assertFalse(bytes.isEmpty())
    }
}
