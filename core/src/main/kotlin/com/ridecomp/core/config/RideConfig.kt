package com.ridecomp.core.config

/**
 * Every domain threshold and default of RideComp, in one place. Values come from the spec documents
 * cited in each KDoc; change them only with owner approval after field tests (12-FEATURE-SPECS intro).
 * All values are SI (m, s, m/s, ms) unless the name says otherwise.
 */
public object Units {
    public const val MS_PER_SECOND: Long = 1_000L
    public const val SECONDS_PER_MINUTE: Long = 60L
    public const val SECONDS_PER_HOUR: Long = 3_600L
    public const val MS_PER_MINUTE: Long = MS_PER_SECOND * SECONDS_PER_MINUTE
    public const val METERS_PER_KM: Double = 1_000.0
    public const val METERS_PER_MILE: Double = 1_609.344
    public const val METERS_PER_FOOT: Double = 0.3048
    public const val KMH_PER_MPS: Double = 3.6
    public const val MPH_PER_MPS: Double = 2.236_936_292_054_402
    public const val MM_PER_METER: Double = 1_000.0
    public const val PERCENT: Double = 100.0
    public const val DEGREES_PER_HALF_TURN: Double = 180.0

    /** Converts km/h to m/s. */
    public fun kmhToMps(kmh: Double): Double = kmh / KMH_PER_MPS
}

/** GPS acquisition and filtering (03-ARCHITECTURE §5). */
public object GpsConfig {
    /** Recording interval: 1 Hz. */
    public const val INTERVAL_MS: Long = 1_000L

    /** Reduced interval while paused > [LOW_POWER_AFTER_PAUSE_MS] or auto-paused for long (BAT-03). */
    public const val LOW_POWER_INTERVAL_MS: Long = 5_000L

    /** Manual pause longer than this lowers the GPS rate (BAT-03). */
    public const val LOW_POWER_AFTER_PAUSE_MS: Long = 10L * Units.MS_PER_MINUTE

    /** Fixes less accurate than this are dropped. */
    public const val MAX_ACCURACY_M: Double = 30.0

    /** A jump implying more than this speed between two fixes is a spike and is dropped. */
    public val MAX_PLAUSIBLE_SPEED_MPS: Double = Units.kmhToMps(120.0)

    /** EMA factor for displayed speed (05-DATA-MODEL §3.2). */
    public const val SPEED_EMA_ALPHA: Double = 0.3

    /** Below this device-reported speed GPS movement is treated as stationary jitter and adds no distance. */
    public val STATIONARY_SPEED_MPS: Double = Units.kmhToMps(2.0)

    /**
     * Without a device speed, distance is only counted once the position moved more than
     * max(fix accuracy, this) from the last counted point, so jitter around a point never accumulates.
     */
    public const val JITTER_FLOOR_M: Double = 5.0

    /**
     * Following a route, a fix that comes back after at least this long without one is joined along the
     * route instead of in a straight line (D-150), if that is at most [ROUTE_GAP_MAX_FACTOR] times longer.
     */
    public const val ROUTE_GAP_MIN_MS: Long = 5_000L
    public const val ROUTE_GAP_MAX_FACTOR: Double = 3.0

    /** Waiting for a first fix in pre-ride is bounded (CO-09). */
    public const val FIRST_FIX_TIMEOUT_MS: Long = 2L * Units.MS_PER_MINUTE
}

/** Recording pipeline (03-ARCHITECTURE §4). */
public object RecordingConfig {
    /** Track points are flushed to the database at least this often (≤ 5 s, 01-PRD §5). */
    public const val FLUSH_INTERVAL_MS: Long = 5_000L

    /** Hard cap of points kept in memory before a forced flush (MEM-05). */
    public const val MAX_BUFFERED_POINTS: Int = 30

    /**
     * Points kept while the database keeps failing (STB-08): 10 minutes at 1 Hz. Beyond that the oldest
     * pending points are dropped (and logged) so memory stays bounded (MEM-05).
     */
    public const val MAX_PENDING_POINTS: Int = 600

    /** Moving-time ticks longer than this are not counted (the device stalled); a `gap` event is logged. */
    public const val MAX_TICK_INTERVAL_MS: Long = 5_000L

    /** A sensor/GPS value older than this is considered missing in a record (03-ARCHITECTURE §4.3). */
    public const val SAMPLE_MAX_AGE_MS: Long = 3_000L

    /** Wakelock timeout, renewed while recording (BAT-02). */
    public const val WAKELOCK_TIMEOUT_MS: Long = 10L * Units.MS_PER_MINUTE

    /** Wakelock is renewed this long before it would expire. */
    public const val WAKELOCK_RENEW_MARGIN_MS: Long = Units.MS_PER_MINUTE

    /** A gap in records longer than this is logged as a `gap` event (recovery). */
    public const val GAP_THRESHOLD_MS: Long = 5_000L
}

/** Auto pause/resume defaults (12-FEATURE-SPECS §1). */
public object AutoPauseConfig {
    public const val IS_ENABLED_DEFAULT: Boolean = true
    public val SPEED_THRESHOLD_MPS: Double = Units.kmhToMps(2.0)

    /** Resume needs threshold + this hysteresis. */
    public val RESUME_HYSTERESIS_MPS: Double = Units.kmhToMps(1.0)
    public const val DELAY_DEFAULT_S: Int = 5
    public val DELAY_CHOICES_S: List<Int> = listOf(3, 5, 10, 30)

    /** Speed must stay above the resume threshold this long. */
    public const val RESUME_CONFIRM_MS: Long = 2_000L

    /** Without a wheel sensor the rider must also move at least this far from the pause point. */
    public const val MIN_RESUME_DISPLACEMENT_M: Double = 10.0

    /** "Finish ride?" prompt after this long auto-paused. */
    public const val IDLE_PROMPT_DEFAULT_MIN: Int = 15
}

/** Elevation, ascent and grade (05-DATA-MODEL §3.5). */
public object ElevationConfig {
    public const val SMOOTHING_WINDOW_S: Int = 10
    public const val HYSTERESIS_BAROMETER_M: Double = 2.0

    /**
     * GPS altitude without a barometer wanders tens of meters and spikes by hundreds (D-148): a 30 s
     * median, at most 0.5 m of change per 1 s sample (a steep climb on a bike is ~0.4 m/s) and a 10 m
     * hysteresis. It still over-counts on such phones, so the ascent is shown as an estimate.
     */
    public const val GPS_SMOOTHING_WINDOW_S: Int = 30
    public const val GPS_MAX_ALTITUDE_STEP_M: Double = 0.5
    public const val HYSTERESIS_GPS_M: Double = 10.0
    public const val GRADE_WINDOW_M: Double = 50.0
    public const val GRADE_MIN_DISTANCE_M: Double = 30.0
    public const val GRADE_LIMIT_PERCENT: Double = 30.0

    /** Number of distance/altitude pairs kept for the grade window (bounded, MEM-05). */
    public const val GRADE_BUFFER_SIZE: Int = 120

    /** Standard atmosphere constants for pressure → altitude. */
    public const val SEA_LEVEL_PRESSURE_HPA: Double = 1_013.25
    public const val BAROMETRIC_SCALE_M: Double = 44_330.0
    public const val BAROMETRIC_EXPONENT: Double = 1.0 / 5.255
}

/** Laps (01-PRD F-12). */
public object LapConfig {
    public val AUTO_LAP_CHOICES_M: List<Double> = listOf(1_000.0, 5_000.0, 10_000.0)
}

/** Drink/eat reminders (12-FEATURE-SPECS §2). */
public object ReminderConfig {
    public const val DRINK_INTERVAL_DEFAULT_MIN: Int = 20
    public const val EAT_INTERVAL_DEFAULT_MIN: Int = 45
    public const val SNOOZE_MIN: Int = 5
    public const val AUTO_DISMISS_MS: Long = 10_000L
    public const val MAX_REPEATS: Int = 2

    /** Interval choices offered in settings, minutes of moving time. */
    public val INTERVAL_CHOICES_MIN: List<Int> = listOf(15, 20, 30, 45, 60)
}

/** Automatic battery modes (12-FEATURE-SPECS §3). */
public object BatteryModeConfig {
    public const val SAVER_PERCENT: Int = 20
    public const val CRITICAL_PERCENT: Int = 10
    public const val EMERGENCY_PERCENT: Int = 5

    /** Battery level is read at most this often. */
    public const val READ_INTERVAL_MS: Long = 30_000L
}

/** Rider profile defaults (05-DATA-MODEL §1, 02-DESIGN §3.1). */
public object ProfileDefaults {
    public const val WEIGHT_KG: Double = 70.0
    public const val BIKE_WEIGHT_KG: Double = 9.0

    /** 700x25c. */
    public const val WHEEL_CIRCUMFERENCE_MM: Int = 2_105

    /** 20x1.75 (ISO 406), the most common folding bike wheel (D-102). */
    public const val FOLDING_WHEEL_CIRCUMFERENCE_MM: Int = 1_515
    public const val MIN_WEIGHT_KG: Double = 20.0
    public const val MAX_WEIGHT_KG: Double = 250.0
    public const val MIN_BIKE_WEIGHT_KG: Double = 3.0
    public const val MAX_BIKE_WEIGHT_KG: Double = 50.0
    public const val MIN_WHEEL_MM: Int = 1_000
    public const val MAX_WHEEL_MM: Int = 3_000
    public const val MIN_HR_MAX: Int = 100
    public const val MAX_HR_MAX: Int = 230
    public const val MIN_HR_REST: Int = 25
    public const val MAX_HR_REST: Int = 120
    public const val MIN_FTP_W: Int = 50
    public const val MAX_FTP_W: Int = 600
    public const val MIN_BIRTH_YEAR: Int = 1_920

    /** Age-predicted HR max = this − age (05-DATA-MODEL §3.8, shown as an estimate). */
    public const val HR_MAX_AGE_BASE: Int = 220

    /** Common tyre sizes and their circumference in mm (02-DESIGN §3.1). Labels are not translated. */
    public val COMMON_WHEELS_MM: List<Pair<String, Int>> = listOf(
        "700x23c" to 2_096,
        "700x25c" to 2_105,
        "700x28c" to 2_136,
        "700x32c" to 2_155,
        "700x35c" to 2_168,
        "700x40c" to 2_200,
        "650bx47" to 2_079,
        "26x2.0" to 2_055,
        "27.5x2.2" to 2_148,
        "29x2.2" to 2_273,
        // Folding bikes (D-102).
        "20x1.75 (406)" to 1_515,
        "20x1-3/8 (451)" to 1_615,
        "16x1.75 (305)" to 1_195,
        "16x1-3/8 (349)" to 1_290,
    )
}

/** Activity naming and storage. */
public object ActivityConfig {
    /** History page size (02-DESIGN §3.7). */
    public const val HISTORY_PAGE_SIZE: Int = 20

    /** Longest ride name kept (input from the user). */
    public const val MAX_NAME_LENGTH: Int = 80

    /** Track points read per chunk for export/charts (DATA-04). */
    public const val TRACK_CHUNK_SIZE: Int = 1_000

    /**
     * Charts are reduced to at most this many points (02-DESIGN §6; T11.1 performance budget,
     * 14-PHASE11-PLAN §8): each point is the average of a run of samples.
     */
    public const val CHART_MAX_POINTS: Int = 300

    /** Local hours where the default ride name changes: morning/noon/afternoon/evening. */
    public const val NOON_START_HOUR: Int = 11
    public const val AFTERNOON_START_HOUR: Int = 15
    public const val EVENING_START_HOUR: Int = 18
    public const val MORNING_START_HOUR: Int = 4
}
