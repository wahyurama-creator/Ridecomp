package com.ridecomp.core.engine.route

import com.ridecomp.core.util.Outcome
import java.io.ByteArrayInputStream
import java.io.InputStream
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GpxParserTest {
    private fun parse(xml: String) = GpxParser.parse(ByteArrayInputStream(xml.toByteArray()))

    private fun success(xml: String): ParsedGpx {
        val result = parse(xml)
        assertTrue(result is Outcome.Success, "expected success, got $result")
        return result.value
    }

    private fun failure(xml: String): GpxError? = (parse(xml) as? Outcome.Failure)?.error

    @Test
    fun `track segments are joined, elevation and names read, long descriptions ignored`() {
        val gpx = success(
            """<?xml version="1.0" encoding="UTF-8"?>
            <!-- exported by a planner -->
            <gpx xmlns="http://www.topografix.com/GPX/1/1" version="1.1">
              <metadata><name>Kaliurang &amp; back</name><desc><![CDATA[${"<b>long</b> ".repeat(
                500,
            )}]]></desc></metadata>
              <trk><name>ignored second name</name>
                <trkseg><trkpt lat="-7.78" lon="110.37"><ele>120.5</ele></trkpt><trkpt lat='-7.70' lon='110.40'/></trkseg>
                <trkseg><trkpt lat="-7.60" lon="110.42"><ele></ele><time>2026-01-01T00:00:00Z</time></trkpt></trkseg>
              </trk>
              <wpt lat="-7.65" lon="110.41"><name>Warung</name></wpt>
            </gpx>""",
        )

        assertEquals("Kaliurang & back", gpx.name)
        assertEquals(3, gpx.points.size)
        assertEquals(120.5, gpx.points.first().elevationM)
        assertNull(gpx.points.last().elevationM)
        assertEquals("Warung", gpx.waypoints.single().name)
    }

    @Test
    fun `route points and namespace prefixes are supported`() {
        val gpx = success(
            """<g:gpx xmlns:g="http://www.topografix.com/GPX/1/1"><g:rte><g:name>Loop</g:name>
            <g:rtept lat="1" lon="2"/><g:rtept lat="1.001" lon="2.001"/></g:rte></g:gpx>""",
        )

        assertEquals("Loop", gpx.name)
        assertEquals(2, gpx.points.size)
    }

    @Test
    fun `doctype, entity expansion and external entities are refused`() {
        val billionLaughs = """<?xml version="1.0"?><!DOCTYPE lolz [<!ENTITY lol "lol">
            <!ENTITY lol2 "&lol;&lol;&lol;&lol;&lol;">]><gpx><trk><name>&lol2;</name></trk></gpx>"""
        assertEquals(GpxError.DOCTYPE_NOT_ALLOWED, failure(billionLaughs))

        val xxe = """<!DOCTYPE gpx [<!ENTITY xxe SYSTEM "file:///data/data/com.ridecomp.app/databases/ridecomp.db">]>
            <gpx><metadata><name>&xxe;</name></metadata></gpx>"""
        assertEquals(GpxError.DOCTYPE_NOT_ALLOWED, failure(xxe))

        // Without a DOCTYPE an unknown entity is simply malformed, never resolved.
        assertEquals(GpxError.MALFORMED, failure("""<gpx><metadata><name>&xxe;</name></metadata></gpx>"""))
    }

    @Test
    fun `out of range, NaN and missing coordinates are rejected`() {
        val point = { lat: String, lon: String ->
            """<gpx><trk><trkseg><trkpt lat="$lat" lon="$lon"/><trkpt lat="1" lon="1"/></trkseg></trk></gpx>"""
        }

        assertEquals(GpxError.INVALID_VALUE, failure(point("91", "10")))
        assertEquals(GpxError.INVALID_VALUE, failure(point("NaN", "10")))
        assertEquals(GpxError.INVALID_VALUE, failure(point("1", "Infinity")))
        assertEquals(GpxError.INVALID_VALUE, failure("""<gpx><trk><trkseg><trkpt lon="1"/></trkseg></trk></gpx>"""))
        assertEquals(
            GpxError.INVALID_VALUE,
            failure("""<gpx><trk><trkseg><trkpt lat="1" lon="1"><ele>99999</ele></trkpt></trkseg></trk></gpx>"""),
        )
    }

    @Test
    fun `too few points, truncated xml and wrong nesting are reported`() {
        assertEquals(GpxError.NO_POINTS, failure("""<gpx><trk><trkseg><trkpt lat="1" lon="1"/></trkseg></trk></gpx>"""))
        assertEquals(GpxError.MALFORMED, failure("""<gpx><trk><trkseg><trkpt lat="1" lon="1"/>"""))
        assertEquals(GpxError.MALFORMED, failure("""<gpx><trk></gpx></trk>"""))
        assertEquals(GpxError.MALFORMED, failure("""<gpx><trk><trkseg><trkpt lat="1" lon="1"/><trkpt lat="1"""))
    }

    @Test
    fun `point and size limits stop the reader early`() {
        val tooMany = buildString {
            append("<gpx><trk><trkseg>")
            repeat(GpxLimits.MAX_POINTS + 1) { append("""<trkpt lat="1" lon="1"/>""") }
            append("</trkseg></trk></gpx>")
        }
        assertEquals(GpxError.TOO_MANY_POINTS, failure(tooMany))

        val endless = object : InputStream() {
            override fun read(): Int = ' '.code
        }
        assertEquals(GpxError.TOO_LARGE, (GpxParser.parse(endless) as? Outcome.Failure)?.error)
    }

    @Test
    fun `random and truncated input never crashes (SEC-IN-06)`() {
        val random = Random(SEED)
        val valid = "<gpx><trk><trkseg>" +
            """<trkpt lat="1" lon="2"><ele>3</ele></trkpt><trkpt lat="1.1" lon="2.1"/>""" +
            "</trkseg></trk></gpx>"
        repeat(FUZZ_RUNS) {
            val bytes = when (it % 3) {
                0 -> random.nextBytes(random.nextInt(1, 400))
                1 -> valid.toByteArray().copyOf(random.nextInt(valid.length))
                else -> valid.toByteArray().also { b -> b[random.nextInt(b.size)] = random.nextInt(32, 127).toByte() }
            }
            // Must return an Outcome, whatever the bytes are.
            GpxParser.parse(ByteArrayInputStream(bytes))
        }
    }

    private companion object {
        const val SEED = 7
        const val FUZZ_RUNS = 3_000
    }
}
