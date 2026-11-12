package com.ridecomp.core.config

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Guards the relationships between centralized constants, so a future edit of one value cannot silently
 * break another rule (e.g. resume threshold below pause threshold).
 */
class ConfigInvariantsTest {
    @Test
    fun `unit conversions are consistent`() {
        assertEquals(10.0, Units.kmhToMps(36.0), absoluteTolerance = 1e-9)
        assertEquals(60_000L, Units.MS_PER_MINUTE)
        // 1 m/s = 3.6 km/h = 3600 s / 1000 m
        assertEquals(Units.SECONDS_PER_HOUR / Units.METERS_PER_KM, Units.KMH_PER_MPS, 1e-9)
        // 1 m/s in mph = 3600 / 1609.344
        assertEquals(Units.SECONDS_PER_HOUR / Units.METERS_PER_MILE, Units.MPH_PER_MPS, 1e-9)
        assertEquals(1_000.0, Units.MM_PER_METER)
        assertEquals(0.3048, Units.METERS_PER_FOOT)
    }

    @Test
    fun `gps limits are sane`() {
        assertTrue(GpsConfig.LOW_POWER_INTERVAL_MS > GpsConfig.INTERVAL_MS)
        assertTrue(GpsConfig.MAX_PLAUSIBLE_SPEED_MPS > GpsConfig.STATIONARY_SPEED_MPS)
        assertTrue(GpsConfig.SPEED_EMA_ALPHA in 0.0..1.0)
        assertTrue(GpsConfig.JITTER_FLOOR_M > 0.0)
        assertTrue(GpsConfig.FIRST_FIX_TIMEOUT_MS > GpsConfig.INTERVAL_MS)
        assertTrue(GpsConfig.LOW_POWER_AFTER_PAUSE_MS > 0)
        assertTrue(GpsConfig.MAX_ACCURACY_M > 0)
    }

    @Test
    fun `recording flushes at most every five seconds with a bounded buffer`() {
        assertTrue(RecordingConfig.FLUSH_INTERVAL_MS <= 5_000L)
        assertTrue(RecordingConfig.MAX_BUFFERED_POINTS * Units.MS_PER_SECOND >= RecordingConfig.FLUSH_INTERVAL_MS)
        assertTrue(RecordingConfig.WAKELOCK_RENEW_MARGIN_MS < RecordingConfig.WAKELOCK_TIMEOUT_MS)
        assertTrue(RecordingConfig.SAMPLE_MAX_AGE_MS > 0)
        assertTrue(RecordingConfig.GAP_THRESHOLD_MS > GpsConfig.INTERVAL_MS)
    }

    @Test
    fun `auto pause resumes above the pause threshold and default delay is selectable`() {
        assertTrue(AutoPauseConfig.RESUME_HYSTERESIS_MPS > 0.0)
        assertTrue(AutoPauseConfig.DELAY_DEFAULT_S in AutoPauseConfig.DELAY_CHOICES_S)
        assertTrue(AutoPauseConfig.RESUME_CONFIRM_MS > 0)
        assertTrue(AutoPauseConfig.MIN_RESUME_DISPLACEMENT_M > 0)
        assertTrue(AutoPauseConfig.IDLE_PROMPT_DEFAULT_MIN > 0)
        assertTrue(AutoPauseConfig.IS_ENABLED_DEFAULT)
        assertTrue(AutoPauseConfig.SPEED_THRESHOLD_MPS > 0)
    }

    @Test
    fun `elevation thresholds follow the spec`() {
        assertTrue(ElevationConfig.HYSTERESIS_GPS_M > ElevationConfig.HYSTERESIS_BAROMETER_M)
        assertTrue(ElevationConfig.GRADE_MIN_DISTANCE_M < ElevationConfig.GRADE_WINDOW_M)
        assertTrue(
            ElevationConfig.GRADE_BUFFER_SIZE > ElevationConfig.GRADE_WINDOW_M / GpsConfig.MAX_PLAUSIBLE_SPEED_MPS,
        )
        assertTrue(ElevationConfig.SMOOTHING_WINDOW_S > 0)
        assertTrue(ElevationConfig.GRADE_LIMIT_PERCENT > 0)
        assertTrue(ElevationConfig.SEA_LEVEL_PRESSURE_HPA > 0)
        assertTrue(ElevationConfig.BAROMETRIC_SCALE_M > 0)
        assertTrue(ElevationConfig.BAROMETRIC_EXPONENT in 0.0..1.0)
    }

    @Test
    fun `battery modes are ordered from saver to emergency`() {
        assertTrue(BatteryModeConfig.SAVER_PERCENT > BatteryModeConfig.CRITICAL_PERCENT)
        assertTrue(BatteryModeConfig.CRITICAL_PERCENT > BatteryModeConfig.EMERGENCY_PERCENT)
        assertTrue(BatteryModeConfig.EMERGENCY_PERCENT > 0)
        assertTrue(BatteryModeConfig.READ_INTERVAL_MS > 0)
    }

    @Test
    fun `reminders and laps have positive defaults`() {
        assertTrue(ReminderConfig.EAT_INTERVAL_DEFAULT_MIN > ReminderConfig.DRINK_INTERVAL_DEFAULT_MIN)
        assertTrue(ReminderConfig.SNOOZE_MIN > 0)
        assertTrue(ReminderConfig.AUTO_DISMISS_MS > 0)
        assertTrue(ReminderConfig.MAX_REPEATS > 0)
        assertTrue(LapConfig.AUTO_LAP_CHOICES_M.all { it > 0 })
    }

    @Test
    fun `profile defaults are inside their valid ranges`() {
        assertTrue(ProfileDefaults.WEIGHT_KG in ProfileDefaults.MIN_WEIGHT_KG..ProfileDefaults.MAX_WEIGHT_KG)
        assertTrue(
            ProfileDefaults.BIKE_WEIGHT_KG in ProfileDefaults.MIN_BIKE_WEIGHT_KG..ProfileDefaults.MAX_BIKE_WEIGHT_KG,
        )
        assertTrue(ProfileDefaults.WHEEL_CIRCUMFERENCE_MM in ProfileDefaults.MIN_WHEEL_MM..ProfileDefaults.MAX_WHEEL_MM)
        assertTrue(ProfileDefaults.MIN_HR_MAX < ProfileDefaults.MAX_HR_MAX)
        assertTrue(ProfileDefaults.MIN_FTP_W < ProfileDefaults.MAX_FTP_W)
        assertTrue(ProfileDefaults.HR_MAX_AGE_BASE > ProfileDefaults.MAX_HR_MAX - ProfileDefaults.MIN_HR_MAX)
        assertTrue(ProfileDefaults.MIN_BIRTH_YEAR > 0)
    }

    @Test
    fun `activity paging and naming hours are consistent`() {
        assertTrue(ActivityConfig.HISTORY_PAGE_SIZE > 0)
        assertTrue(ActivityConfig.TRACK_CHUNK_SIZE >= ActivityConfig.HISTORY_PAGE_SIZE)
        assertTrue(ActivityConfig.CHART_MAX_POINTS > 0)
        assertTrue(ActivityConfig.MORNING_START_HOUR < ActivityConfig.NOON_START_HOUR)
        assertTrue(ActivityConfig.NOON_START_HOUR < ActivityConfig.AFTERNOON_START_HOUR)
        assertTrue(ActivityConfig.AFTERNOON_START_HOUR < ActivityConfig.EVENING_START_HOUR)
    }
}
