package com.ridecomp.core.engine.safety

import com.ridecomp.core.config.EstimatedPowerConfig
import com.ridecomp.core.config.Units
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Synthetic scenarios from 07-TEST-PLAN (Deteksi insiden): a fall while moving is detected; potholes, speed
 * bumps, hard braking and a phone dropped while stopped are not. Real recordings calibrate later (L17).
 */
class IncidentDetectorTest {
    private val detector = IncidentDetector()
    private var nowMs = 0L
    private val detections = mutableListOf<IncidentDetection>()

    /** One simulated second: 50 accelerometer samples, then the 1 Hz speed tick. */
    private fun second(speedKmh: Double, accel: (Int) -> Double) {
        repeat(SAMPLES_PER_SECOND) { i ->
            detector.onAcceleration(AccelSample(nowMs + i * SAMPLE_INTERVAL_MS, accel(i)))
        }
        nowMs += Units.MS_PER_SECOND
        detector.onSecond(nowMs, Units.kmhToMps(speedKmh))?.let(detections::add)
    }

    private fun riding(seconds: Int, speedKmh: Double = 22.0) =
        repeat(seconds) { second(speedKmh) { i -> G + RIDING_VIBRATION * sin(i * PHASE_STEP) } }

    private fun lyingStill(seconds: Int) = repeat(seconds) {
        second(0.0) { i -> G + STILL_NOISE * sin(i * PHASE_STEP) }
    }

    private fun impact(speedKmh: Double, peakMps2: Double) =
        second(speedKmh) { i -> if (i == IMPACT_SAMPLE) peakMps2 else G }

    @Test
    fun `fall while riding is detected once stillness is confirmed`() {
        riding(10)
        impact(speedKmh = 5.0, peakMps2 = 45.0)
        lyingStill(12)

        val detection = assertNotNull(detections.singleOrNull())
        assertTrue(detection.peakG >= IncidentConfig.IMPACT_G)
        assertTrue(detection.speedBeforeMps >= IncidentConfig.MIN_SPEED_BEFORE_MPS)
    }

    @Test
    fun `pothole or speed bump while riding on is not an incident`() {
        riding(10)
        impact(speedKmh = 20.0, peakMps2 = 40.0)
        riding(20)

        assertTrue(detections.isEmpty())
    }

    @Test
    fun `phone dropped while standing is not an incident`() {
        lyingStill(10)
        impact(speedKmh = 0.0, peakMps2 = 60.0)
        lyingStill(15)

        assertTrue(detections.isEmpty())
    }

    @Test
    fun `hard braking to a stop stays below the impact threshold`() {
        riding(10, speedKmh = 30.0)
        second(10.0) { i -> if (i < BRAKING_SAMPLES) BRAKING_MPS2 else G }
        lyingStill(15)

        assertTrue(detections.isEmpty())
    }

    @Test
    fun `stopping long after an impact does not trigger`() {
        riding(10)
        impact(speedKmh = 20.0, peakMps2 = 40.0)
        riding(16)
        lyingStill(10)

        assertTrue(detections.isEmpty())
    }

    @Test
    fun `reset clears a pending impact`() {
        riding(10)
        impact(speedKmh = 5.0, peakMps2 = 45.0)
        detector.reset()
        lyingStill(12)

        assertNull(detections.firstOrNull())
        assertEquals(0, detections.size)
    }

    private companion object {
        const val G = EstimatedPowerConfig.GRAVITY_MPS2
        const val SAMPLES_PER_SECOND = 50
        const val SAMPLE_INTERVAL_MS = 20L
        const val IMPACT_SAMPLE = 25
        const val PHASE_STEP = 0.7

        /** Pedalling on a rough road: ±3 m/s² around gravity, variance far above the still limit. */
        const val RIDING_VIBRATION = 3.0
        const val STILL_NOISE = 0.1

        /** ≈ 1.2 g of braking added to gravity for half a second. */
        const val BRAKING_MPS2 = 15.4
        const val BRAKING_SAMPLES = 25
    }
}
