package com.ridecomp.core.util

import com.ridecomp.core.model.UnitSystem
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals

class UnitFormatterTest {
    private val metric = UnitFormatter(UnitSystem.METRIC, Locale.US)
    private val imperial = UnitFormatter(UnitSystem.IMPERIAL, Locale.US)

    @Test
    fun `speed converts from m per s`() {
        assertEquals("36.0", metric.speed(10.0))
        assertEquals("22.4", imperial.speed(10.0))
        assertEquals("km/h", metric.speedUnit)
        assertEquals("mph", imperial.speedUnit)
    }

    @Test
    fun `missing values show the placeholder, never zero`() {
        assertEquals("--", metric.speed(null))
        assertEquals("--", metric.distance(null))
        assertEquals("--", metric.elevation(Double.NaN))
        assertEquals("--", metric.grade(null))
        assertEquals("--", metric.integer(null))
        assertEquals("--", metric.duration(null))
    }

    @Test
    fun `tiny negative speed is shown as zero`() {
        assertEquals("0.0", metric.speed(-0.001))
    }

    @Test
    fun `distance uses km or miles with two decimals`() {
        assertEquals("12.35", metric.distance(12_345.0))
        assertEquals("1.00", imperial.distance(1_609.344))
        assertEquals("mi", imperial.distanceUnit)
    }

    @Test
    fun `elevation in meters or feet`() {
        assertEquals("100", metric.elevation(100.4))
        assertEquals("328", imperial.elevation(100.0))
        assertEquals("ft", imperial.elevationUnit)
        assertEquals("m", metric.elevationUnit)
    }

    @Test
    fun `grade has no decimals`() {
        assertEquals("6", metric.grade(5.6))
        assertEquals("-3", metric.grade(-3.2))
    }

    @Test
    fun `durations below and above one hour`() {
        assertEquals("0:59", metric.duration(59_999))
        assertEquals("12:05", metric.duration(725_000))
        assertEquals("1:02:03", metric.duration(3_723_000))
        assertEquals("0:00", metric.duration(-5))
    }

    @Test
    fun `decimal separator follows the locale`() {
        assertEquals("36,0", UnitFormatter(UnitSystem.METRIC, Locale.forLanguageTag("id-ID")).speed(10.0))
    }

    @Test
    fun `integers are printed as is`() {
        assertEquals("145", metric.integer(145))
    }
}
