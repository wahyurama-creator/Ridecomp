package com.ridecomp.core.engine.map

import com.ridecomp.core.util.Outcome
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** PMTiles v3 header checks (T3.4, SEC-IN-01): only well-formed vector region files are accepted. */
class PmTilesHeaderTest {
    /** A header for Yogyakarta: lon 110.2..110.5, lat -7.95..-7.65, zoom 0..14. */
    private fun header(version: Int = 3, tileType: Int = 1, minLatE7: Int = -79_500_000): ByteArray {
        val buffer = ByteBuffer.allocate(PmTilesHeader.SIZE_BYTES).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put("PMTiles".toByteArray(Charsets.US_ASCII))
        buffer.put(version.toByte())
        buffer.put(99, tileType.toByte())
        buffer.put(100, 0)
        buffer.put(101, 14)
        buffer.putInt(102, 1_102_000_000)
        buffer.putInt(106, minLatE7)
        buffer.putInt(110, 1_105_000_000)
        buffer.putInt(114, -76_500_000)
        return buffer.array()
    }

    @Test
    fun `a vector v3 header gives its bounds and zooms`() {
        val info = (PmTilesHeader.parse(header()) as Outcome.Success).value

        assertEquals(MapBounds(110.2, -7.95, 110.5, -7.65), info.bounds)
        assertEquals(0, info.minZoom)
        assertEquals(14, info.maxZoom)
        assertTrue(info.bounds.contains(latDeg = -7.78, lonDeg = 110.37))
        assertFalse(info.bounds.contains(latDeg = -6.2, lonDeg = 106.8))
    }

    @Test
    fun `other files, versions, raster tiles and impossible boxes are refused`() {
        assertEquals(Outcome.Failure(MapImportError.NOT_PMTILES), PmTilesHeader.parse("<gpx>".toByteArray()))
        assertEquals(Outcome.Failure(MapImportError.UNSUPPORTED_VERSION), PmTilesHeader.parse(header(version = 2)))
        assertEquals(Outcome.Failure(MapImportError.NOT_VECTOR), PmTilesHeader.parse(header(tileType = 2)))
        // min lat above max lat
        assertEquals(Outcome.Failure(MapImportError.NOT_PMTILES), PmTilesHeader.parse(header(minLatE7 = -70_000_000)))
    }
}
