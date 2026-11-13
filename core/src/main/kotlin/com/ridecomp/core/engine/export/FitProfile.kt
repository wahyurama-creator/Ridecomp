package com.ridecomp.core.engine.export

/**
 * Message numbers, field numbers and enum values from the Garmin FIT SDK Profile used by RideComp, in one
 * place (04-INTEGRATIONS §B). Scales: distance ×100 (cm), speed ×1000 (mm/s), time ×1000 (ms),
 * enhanced_altitude ×5 with offset 500 m.
 */
public object FitProfile {
    /** Seconds between the Unix epoch and the FIT epoch (1989-12-31T00:00:00Z). */
    public const val FIT_EPOCH_OFFSET_S: Long = 631_065_600L

    /** Degrees → semicircles: deg × 2^31 / 180. */
    public const val SEMICIRCLES_PER_DEGREE: Double = 2_147_483_648.0 / 180.0
    public const val DISTANCE_SCALE: Double = 100.0
    public const val SPEED_SCALE: Double = 1_000.0
    public const val TIME_SCALE: Double = 1_000.0
    public const val ALTITUDE_SCALE: Double = 5.0
    public const val ALTITUDE_OFFSET_M: Double = 500.0

    public object Message {
        public const val FILE_ID: Int = 0
        public const val SESSION: Int = 18
        public const val LAP: Int = 19
        public const val RECORD: Int = 20
        public const val EVENT: Int = 21
        public const val ACTIVITY: Int = 34
    }

    /** Field 253 is the timestamp and 254 the message index in every message. */
    public object Common {
        public const val TIMESTAMP: Int = 253
        public const val MESSAGE_INDEX: Int = 254
    }

    public object FileId {
        public const val TYPE: Int = 0
        public const val MANUFACTURER: Int = 1
        public const val PRODUCT: Int = 2
        public const val SERIAL_NUMBER: Int = 3
        public const val TIME_CREATED: Int = 4
        public const val TYPE_ACTIVITY: Long = 4
        public const val MANUFACTURER_DEVELOPMENT: Long = 255
        public const val PRODUCT_RIDECOMP: Long = 1
    }

    public object Event {
        public const val FIELD_EVENT: Int = 0
        public const val FIELD_EVENT_TYPE: Int = 1
        public const val TIMER: Long = 0
        public const val SESSION: Long = 8
        public const val LAP: Long = 9
        public const val ACTIVITY: Long = 26
        public const val TYPE_START: Long = 0
        public const val TYPE_STOP: Long = 1
        public const val TYPE_STOP_ALL: Long = 4
    }

    public object Record {
        public const val POSITION_LAT: Int = 0
        public const val POSITION_LONG: Int = 1
        public const val HEART_RATE: Int = 3
        public const val CADENCE: Int = 4
        public const val DISTANCE: Int = 5
        public const val POWER: Int = 7
        public const val TEMPERATURE: Int = 13
        public const val ENHANCED_SPEED: Int = 73
        public const val ENHANCED_ALTITUDE: Int = 78
    }

    public object Lap {
        public const val START_TIME: Int = 2
        public const val TOTAL_ELAPSED_TIME: Int = 7
        public const val TOTAL_TIMER_TIME: Int = 8
        public const val TOTAL_DISTANCE: Int = 9
        public const val AVG_HEART_RATE: Int = 15
        public const val MAX_HEART_RATE: Int = 16
        public const val AVG_CADENCE: Int = 17
        public const val AVG_POWER: Int = 19
        public const val MAX_POWER: Int = 20
        public const val TOTAL_ASCENT: Int = 21
        public const val LAP_TRIGGER: Int = 24
        public const val ENHANCED_AVG_SPEED: Int = 110
        public const val ENHANCED_MAX_SPEED: Int = 111
        public const val TRIGGER_MANUAL: Long = 0
        public const val TRIGGER_DISTANCE: Long = 2
    }

    public object Session {
        public const val START_TIME: Int = 2
        public const val SPORT: Int = 5
        public const val SUB_SPORT: Int = 6
        public const val TOTAL_ELAPSED_TIME: Int = 7
        public const val TOTAL_TIMER_TIME: Int = 8
        public const val TOTAL_DISTANCE: Int = 9
        public const val TOTAL_CALORIES: Int = 11
        public const val AVG_HEART_RATE: Int = 16
        public const val MAX_HEART_RATE: Int = 17
        public const val AVG_CADENCE: Int = 18
        public const val AVG_POWER: Int = 20
        public const val MAX_POWER: Int = 21
        public const val TOTAL_ASCENT: Int = 22
        public const val TOTAL_DESCENT: Int = 23
        public const val FIRST_LAP_INDEX: Int = 25
        public const val NUM_LAPS: Int = 26
        public const val NORMALIZED_POWER: Int = 34
        public const val ENHANCED_AVG_SPEED: Int = 124
        public const val ENHANCED_MAX_SPEED: Int = 125
    }

    public object Activity {
        public const val TOTAL_TIMER_TIME: Int = 0
        public const val NUM_SESSIONS: Int = 1
        public const val TYPE: Int = 2
        public const val EVENT: Int = 3
        public const val EVENT_TYPE: Int = 4
        public const val LOCAL_TIMESTAMP: Int = 5
        public const val TYPE_MANUAL: Long = 0
    }
}
