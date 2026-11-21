package com.ridecomp.core.engine.sensors

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WatchNamesTest {
    @Test
    fun `watch and band names are recognised`() {
        listOf(
            "HUAWEI WATCH HR-E57",
            "Forerunner 265",
            "fēnix 7",
            "Venu 3",
            "Amazfit Balance",
            "Xiaomi Smart Band 10",
            "Aolon GTR3(ID-A05B)",
        ).forEach { assertTrue(WatchNames.looksLikeWatch(it), it) }
    }

    @Test
    fun `straps and other sensors stay sensors`() {
        listOf("HRM-Pro", "Polar H10", "TICKR A1B2", "Amazfit Helio Strap", "KICKR CORE", null)
            .forEach { assertFalse(WatchNames.looksLikeWatch(it), it.toString()) }
    }

    @Test
    fun `only heart rate devices and GloryFit watches count as watches`() {
        assertTrue(SavedSensor("A", "Venu 3", setOf(SensorKind.HEART_RATE)).isWatch)
        assertTrue(SavedSensor("B", "Device", setOf(SensorKind.HEART_RATE), isGloryFitWatch = true).isWatch)
        assertFalse(SavedSensor("C", "Speed Band", setOf(SensorKind.SPEED_CADENCE)).isWatch)
        assertFalse(SavedSensor("D", "HRM-Dual", setOf(SensorKind.HEART_RATE)).isWatch)
    }
}
