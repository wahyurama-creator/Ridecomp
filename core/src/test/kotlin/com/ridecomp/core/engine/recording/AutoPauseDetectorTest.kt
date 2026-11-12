package com.ridecomp.core.engine.recording

import com.ridecomp.core.config.Units
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private const val METER_IN_LAT_DEG = 1.0 / 111_195.0

private fun sample(tSec: Long, kmh: Double?, northM: Double = 0.0, wheel: Boolean = false, accuracyM: Double = 5.0) =
    MotionSample(
        timeMs = tSec * 1_000,
        speedMps = kmh?.let { Units.kmhToMps(it) },
        latDeg = -7.0 + northM * METER_IN_LAT_DEG,
        lonDeg = 110.0,
        accuracyM = accuracyM,
        isFromWheelSensor = wheel,
    )

/** Runs [samples] through the detector while recording; returns the second at which it paused, if any. */
private fun AutoPauseDetector.pauseSecond(samples: List<MotionSample>): Long? =
    samples.firstOrNull { onRecording(it) == AutoPauseDecision.PAUSE }?.let { it.timeMs / 1_000 }

class AutoPauseDetectorTest {
    @Test
    fun `stopping at a traffic light pauses after the default 5 seconds`() {
        val detector = AutoPauseDetector(AutoPauseSettings())
        val riding = (0L..9L).map { sample(it, 25.0, northM = it * 7.0) }
        val stopped = (10L..20L).map { sample(it, 0.0, northM = 70.0) }

        assertEquals(15L, detector.pauseSecond(riding + stopped))
    }

    @Test
    fun `each delay choice is honoured`() {
        listOf(3, 5, 10, 30).forEach { delay ->
            val detector = AutoPauseDetector(AutoPauseSettings(delayS = delay))
            val stopped = (0L..40L).map { sample(it, 0.0) }

            assertEquals(delay.toLong(), detector.pauseSecond(stopped), "delay $delay")
        }
    }

    @Test
    fun `very slow climb at 4 to 5 kmh is never paused`() {
        val detector = AutoPauseDetector(AutoPauseSettings())
        val climb = (0L..120L).map { sample(it, if (it % 2 == 0L) 4.0 else 5.0, northM = it * 1.25) }

        assertEquals(null, detector.pauseSecond(climb))
    }

    @Test
    fun `missing speed never pauses`() {
        val detector = AutoPauseDetector(AutoPauseSettings())

        assertEquals(null, detector.pauseSecond((0L..60L).map { sample(it, null) }))
    }

    @Test
    fun `disabled detector never pauses`() {
        val detector = AutoPauseDetector(AutoPauseSettings(isEnabled = false))

        assertEquals(null, detector.pauseSecond((0L..60L).map { sample(it, 0.0) }))
    }

    @Test
    fun `slow but real displacement restarts the stop timer`() {
        val detector = AutoPauseDetector(AutoPauseSettings())
        // GPS reports ~1 km/h while the rider walks the bike 3 m per second.
        val walking = (0L..30L).map { sample(it, 1.0, northM = it * 3.0) }

        assertEquals(null, detector.pauseSecond(walking))
    }

    @Test
    fun `gps drift while standing does not resume`() {
        val detector = AutoPauseDetector(AutoPauseSettings())
        detector.pauseSecond((0L..6L).map { sample(it, 0.0) })
        val drift = listOf(0.0, 4.0, -3.0, 5.0, -4.0, 3.0, -5.0, 4.0)

        val decisions = drift.mapIndexed { i, offset ->
            // Drift makes GPS speed jump to 4-6 km/h, but the position stays within ~5 m.
            detector.onAutoPaused(sample(10L + i, 6.0, northM = offset))
        }

        assertTrue(decisions.none { it == AutoPauseDecision.RESUME })
    }

    @Test
    fun `riding away resumes after the 2 second confirmation`() {
        val detector = AutoPauseDetector(AutoPauseSettings())
        detector.pauseSecond((0L..6L).map { sample(it, 0.0) })

        val decisions = (10L..14L).map { detector.onAutoPaused(sample(it, 15.0, northM = (it - 9) * 6.0)) }

        assertEquals(
            listOf(
                AutoPauseDecision.NONE,
                AutoPauseDecision.NONE,
                AutoPauseDecision.RESUME,
                AutoPauseDecision.NONE,
                AutoPauseDecision.NONE,
            ),
            decisions,
        )
    }

    @Test
    fun `speed just above the pause threshold is not enough to resume (hysteresis)`() {
        val detector = AutoPauseDetector(AutoPauseSettings())
        detector.pauseSecond((0L..6L).map { sample(it, 0.0) })

        val decisions = (10L..20L).map { detector.onAutoPaused(sample(it, 2.5, northM = (it - 9) * 20.0)) }

        assertTrue(decisions.none { it == AutoPauseDecision.RESUME })
    }

    @Test
    fun `wheel sensor resumes without a displacement check`() {
        val detector = AutoPauseDetector(AutoPauseSettings())
        detector.pauseSecond((0L..6L).map { sample(it, 0.0, wheel = true) })

        val decisions = (10L..12L).map { detector.onAutoPaused(sample(it, 12.0, wheel = true)) }

        assertEquals(AutoPauseDecision.RESUME, decisions.last())
    }

    @Test
    fun `a dip below the resume speed restarts the confirmation`() {
        val detector = AutoPauseDetector(AutoPauseSettings())
        detector.pauseSecond((0L..6L).map { sample(it, 0.0) })

        detector.onAutoPaused(sample(10, 15.0, northM = 20.0))
        detector.onAutoPaused(sample(11, 1.0, northM = 21.0))
        val atTwelve = detector.onAutoPaused(sample(12, 15.0, northM = 30.0))
        val atFourteen = detector.onAutoPaused(sample(14, 15.0, northM = 40.0))

        assertEquals(AutoPauseDecision.NONE, atTwelve)
        assertEquals(AutoPauseDecision.RESUME, atFourteen)
    }

    @Test
    fun `idle prompt appears after 15 minutes auto paused and never before`() {
        val detector = AutoPauseDetector(AutoPauseSettings())
        detector.pauseSecond((0L..6L).map { sample(it, 0.0) })

        assertFalse(detector.isIdlePromptDue(nowMs = (5L + 14 * 60) * 1_000))
        assertTrue(detector.isIdlePromptDue(nowMs = (5L + 15 * 60) * 1_000))
    }

    @Test
    fun `reset clears pause state`() {
        val detector = AutoPauseDetector(AutoPauseSettings())
        detector.pauseSecond((0L..6L).map { sample(it, 0.0) })

        detector.reset()

        assertFalse(detector.isIdlePromptDue(Long.MAX_VALUE))
    }
}
