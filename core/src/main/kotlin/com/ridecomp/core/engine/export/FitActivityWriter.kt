package com.ridecomp.core.engine.export

import com.ridecomp.core.config.Units
import com.ridecomp.core.engine.export.FitBaseType.ENUM
import com.ridecomp.core.engine.export.FitBaseType.SINT32
import com.ridecomp.core.engine.export.FitBaseType.SINT8
import com.ridecomp.core.engine.export.FitBaseType.UINT16
import com.ridecomp.core.engine.export.FitBaseType.UINT32
import com.ridecomp.core.engine.export.FitBaseType.UINT32Z
import com.ridecomp.core.engine.export.FitBaseType.UINT8
import com.ridecomp.core.model.FitSportValues
import com.ridecomp.core.model.Lap
import com.ridecomp.core.model.LapTrigger
import com.ridecomp.core.model.RideSample
import com.ridecomp.core.model.RideTotals
import kotlin.math.roundToLong

/**
 * Builds a FIT Activity file (04-INTEGRATIONS §B.1):
 * `file_id → event(timer start) → record ×N with timer events in between → lap ×M → session → activity`.
 *
 * Only real power-meter data goes to `power`; estimated power is never written (§B.3, CLAUDE.md).
 * Feed samples and timer events in time order, then call [finish].
 */
/** FIT session `sport` / `sub_sport` (from the activity profile, 12-FEATURE-SPECS §4). */
public data class FitSport(
    val sport: Int = FitSportValues.SPORT_CYCLING,
    val subSport: Int = FitSportValues.SUB_SPORT_GENERIC,
)

public class FitActivityWriter(
    private val startTimeMs: Long,
    serialNumber: Long,
    private val sport: FitSport = FitSport(),
) {
    private val encoder = FitEncoder()
    private var lapCount = 0

    init {
        DEFINITIONS.forEach(encoder::define)
        encoder.write(
            FILE_ID,
            listOf(
                FitProfile.FileId.TYPE_ACTIVITY,
                FitProfile.FileId.MANUFACTURER_DEVELOPMENT,
                FitProfile.FileId.PRODUCT_RIDECOMP,
                serialNumber.coerceAtLeast(1),
                fitTime(startTimeMs),
            ),
        )
    }

    /** Timer start (start/resume) or stop_all (pause/auto pause/finish). */
    public fun addTimerEvent(timeMs: Long, isStart: Boolean) {
        val type = if (isStart) FitProfile.Event.TYPE_START else FitProfile.Event.TYPE_STOP_ALL
        encoder.write(EVENT, listOf(fitTime(timeMs), FitProfile.Event.TIMER, type))
    }

    public fun addRecord(sample: RideSample) {
        encoder.write(
            RECORD,
            listOf(
                fitTime(sample.timeMs),
                sample.latDeg?.let(::semicircles),
                sample.lonDeg?.let(::semicircles),
                sample.heartRateBpm?.toLong(),
                sample.cadenceRpm?.toLong(),
                scaled(sample.distanceM, FitProfile.DISTANCE_SCALE),
                sample.powerW?.toLong(),
                sample.temperatureC?.roundToLong(),
                sample.speedMps?.let { scaled(it, FitProfile.SPEED_SCALE) },
                sample.altitudeM?.let { scaled(it + FitProfile.ALTITUDE_OFFSET_M, FitProfile.ALTITUDE_SCALE) },
            ),
        )
    }

    public fun addLap(lap: Lap) {
        encoder.write(
            LAP,
            listOf(
                fitTime(lap.endTimeMs),
                lap.index.toLong(),
                FitProfile.Event.LAP,
                FitProfile.Event.TYPE_STOP,
                fitTime(lap.startTimeMs),
                scaled((lap.endTimeMs - lap.startTimeMs).toDouble() / Units.MS_PER_SECOND, FitProfile.TIME_SCALE),
                scaled(lap.movingS.toDouble(), FitProfile.TIME_SCALE),
                scaled(lap.distanceM, FitProfile.DISTANCE_SCALE),
                lap.avgHeartRateBpm?.toLong(),
                lap.maxHeartRateBpm?.toLong(),
                lap.avgCadenceRpm?.toLong(),
                lap.avgPowerW?.toLong(),
                lap.maxPowerW?.toLong(),
                lap.ascentM.roundToLong(),
                if (lap.trigger ==
                    LapTrigger.DISTANCE
                ) {
                    FitProfile.Lap.TRIGGER_DISTANCE
                } else {
                    FitProfile.Lap.TRIGGER_MANUAL
                },
                scaled(lap.avgSpeedMps, FitProfile.SPEED_SCALE),
                scaled(lap.maxSpeedMps, FitProfile.SPEED_SCALE),
            ),
        )
        lapCount++
    }

    /** Writes session and activity and returns the complete file. [utcOffsetS] = local time − UTC. */
    public fun finish(totals: RideTotals, endTimeMs: Long, utcOffsetS: Long): ByteArray {
        writeSession(totals, endTimeMs)
        encoder.write(
            ACTIVITY,
            listOf(
                fitTime(endTimeMs),
                scaled(totals.movingS.toDouble(), FitProfile.TIME_SCALE),
                1L,
                FitProfile.Activity.TYPE_MANUAL,
                FitProfile.Event.ACTIVITY,
                FitProfile.Event.TYPE_STOP,
                fitTime(endTimeMs) + utcOffsetS,
            ),
        )
        return encoder.toFile()
    }

    private fun writeSession(totals: RideTotals, endTimeMs: Long) {
        encoder.write(
            SESSION,
            listOf(
                fitTime(endTimeMs),
                0L,
                FitProfile.Event.SESSION,
                FitProfile.Event.TYPE_STOP,
                fitTime(startTimeMs),
                sport.sport.toLong(),
                sport.subSport.toLong(),
                scaled(totals.elapsedS.toDouble(), FitProfile.TIME_SCALE),
                scaled(totals.movingS.toDouble(), FitProfile.TIME_SCALE),
                scaled(totals.distanceM, FitProfile.DISTANCE_SCALE),
                totals.calories?.toLong(),
                totals.avgHeartRateBpm?.toLong(),
                totals.maxHeartRateBpm?.toLong(),
                totals.avgCadenceRpm?.toLong(),
                totals.avgPowerW?.toLong(),
                totals.maxPowerW?.toLong(),
                totals.ascentM.roundToLong(),
                totals.descentM.roundToLong(),
                0L,
                lapCount.toLong(),
                totals.normalizedPowerW?.toLong(),
                scaled(totals.avgSpeedMps, FitProfile.SPEED_SCALE),
                scaled(totals.maxSpeedMps, FitProfile.SPEED_SCALE),
            ),
        )
    }

    public companion object {
        /** Unix ms → FIT timestamp (seconds since 1989-12-31). */
        public fun fitTime(unixMs: Long): Long = unixMs / Units.MS_PER_SECOND - FitProfile.FIT_EPOCH_OFFSET_S

        /** Degrees → FIT semicircles. */
        public fun semicircles(degrees: Double): Long = (degrees * FitProfile.SEMICIRCLES_PER_DEGREE).roundToLong()
            .coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong())

        private fun scaled(value: Double, scale: Double): Long = (value * scale).roundToLong().coerceAtLeast(0)

        private val TS = FitField(FitProfile.Common.TIMESTAMP, UINT32)
        private val INDEX = FitField(FitProfile.Common.MESSAGE_INDEX, UINT16)
        private val EVENT_FIELD = FitField(FitProfile.Event.FIELD_EVENT, ENUM)
        private val EVENT_TYPE_FIELD = FitField(FitProfile.Event.FIELD_EVENT_TYPE, ENUM)

        internal val FILE_ID = FitMessageDefinition(
            0,
            FitProfile.Message.FILE_ID,
            listOf(
                FitField(FitProfile.FileId.TYPE, ENUM),
                FitField(FitProfile.FileId.MANUFACTURER, UINT16),
                FitField(FitProfile.FileId.PRODUCT, UINT16),
                FitField(FitProfile.FileId.SERIAL_NUMBER, UINT32Z),
                FitField(FitProfile.FileId.TIME_CREATED, UINT32),
            ),
        )
        internal val EVENT =
            FitMessageDefinition(1, FitProfile.Message.EVENT, listOf(TS, EVENT_FIELD, EVENT_TYPE_FIELD))
        internal val RECORD = FitMessageDefinition(
            2,
            FitProfile.Message.RECORD,
            listOf(
                TS,
                FitField(FitProfile.Record.POSITION_LAT, SINT32),
                FitField(FitProfile.Record.POSITION_LONG, SINT32),
                FitField(FitProfile.Record.HEART_RATE, UINT8),
                FitField(FitProfile.Record.CADENCE, UINT8),
                FitField(FitProfile.Record.DISTANCE, UINT32),
                FitField(FitProfile.Record.POWER, UINT16),
                FitField(FitProfile.Record.TEMPERATURE, SINT8),
                FitField(FitProfile.Record.ENHANCED_SPEED, UINT32),
                FitField(FitProfile.Record.ENHANCED_ALTITUDE, UINT32),
            ),
        )
        internal val LAP = FitMessageDefinition(
            3,
            FitProfile.Message.LAP,
            listOf(
                TS,
                INDEX,
                EVENT_FIELD,
                EVENT_TYPE_FIELD,
                FitField(FitProfile.Lap.START_TIME, UINT32),
                FitField(FitProfile.Lap.TOTAL_ELAPSED_TIME, UINT32),
                FitField(FitProfile.Lap.TOTAL_TIMER_TIME, UINT32),
                FitField(FitProfile.Lap.TOTAL_DISTANCE, UINT32),
                FitField(FitProfile.Lap.AVG_HEART_RATE, UINT8),
                FitField(FitProfile.Lap.MAX_HEART_RATE, UINT8),
                FitField(FitProfile.Lap.AVG_CADENCE, UINT8),
                FitField(FitProfile.Lap.AVG_POWER, UINT16),
                FitField(FitProfile.Lap.MAX_POWER, UINT16),
                FitField(FitProfile.Lap.TOTAL_ASCENT, UINT16),
                FitField(FitProfile.Lap.LAP_TRIGGER, ENUM),
                FitField(FitProfile.Lap.ENHANCED_AVG_SPEED, UINT32),
                FitField(FitProfile.Lap.ENHANCED_MAX_SPEED, UINT32),
            ),
        )
        internal val SESSION = FitMessageDefinition(
            4,
            FitProfile.Message.SESSION,
            listOf(
                TS,
                INDEX,
                EVENT_FIELD,
                EVENT_TYPE_FIELD,
                FitField(FitProfile.Session.START_TIME, UINT32),
                FitField(FitProfile.Session.SPORT, ENUM),
                FitField(FitProfile.Session.SUB_SPORT, ENUM),
                FitField(FitProfile.Session.TOTAL_ELAPSED_TIME, UINT32),
                FitField(FitProfile.Session.TOTAL_TIMER_TIME, UINT32),
                FitField(FitProfile.Session.TOTAL_DISTANCE, UINT32),
                FitField(FitProfile.Session.TOTAL_CALORIES, UINT16),
                FitField(FitProfile.Session.AVG_HEART_RATE, UINT8),
                FitField(FitProfile.Session.MAX_HEART_RATE, UINT8),
                FitField(FitProfile.Session.AVG_CADENCE, UINT8),
                FitField(FitProfile.Session.AVG_POWER, UINT16),
                FitField(FitProfile.Session.MAX_POWER, UINT16),
                FitField(FitProfile.Session.TOTAL_ASCENT, UINT16),
                FitField(FitProfile.Session.TOTAL_DESCENT, UINT16),
                FitField(FitProfile.Session.FIRST_LAP_INDEX, UINT16),
                FitField(FitProfile.Session.NUM_LAPS, UINT16),
                FitField(FitProfile.Session.NORMALIZED_POWER, UINT16),
                FitField(FitProfile.Session.ENHANCED_AVG_SPEED, UINT32),
                FitField(FitProfile.Session.ENHANCED_MAX_SPEED, UINT32),
            ),
        )
        internal val ACTIVITY = FitMessageDefinition(
            5,
            FitProfile.Message.ACTIVITY,
            listOf(
                TS,
                FitField(FitProfile.Activity.TOTAL_TIMER_TIME, UINT32),
                FitField(FitProfile.Activity.NUM_SESSIONS, UINT16),
                FitField(FitProfile.Activity.TYPE, ENUM),
                FitField(FitProfile.Activity.EVENT, ENUM),
                FitField(FitProfile.Activity.EVENT_TYPE, ENUM),
                FitField(FitProfile.Activity.LOCAL_TIMESTAMP, UINT32),
            ),
        )
        private val DEFINITIONS = listOf(FILE_ID, EVENT, RECORD, LAP, SESSION, ACTIVITY)
    }
}
