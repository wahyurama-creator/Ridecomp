package com.ridecomp.core.engine.export

import com.ridecomp.core.config.Units
import com.ridecomp.core.model.RideSample
import java.time.Instant
import java.util.Locale

/** Append-only byte output, implemented by the platform over a file stream (streaming, MEM-05). */
public interface ByteSink {
    public fun write(bytes: ByteArray)
}

/**
 * Streams a GPX 1.1 track (04-INTEGRATIONS §C.2): time, elevation, and heart rate / cadence through the
 * Garmin TrackPointExtension. Power is not standard in GPX, so FIT stays the main format.
 * Points are written one by one, never the whole track in memory.
 */
public class GpxWriter(private val sink: ByteSink) {
    public fun begin(name: String, startTimeMs: Long) {
        write(
            "$XML_DECLARATION\n" +
                "<gpx version=\"1.1\" creator=\"$CREATOR\" xmlns=\"$GPX_NS\" xmlns:gpxtpx=\"$TPX_NS\">\n" +
                "<metadata><time>${isoTime(startTimeMs)}</time></metadata>\n" +
                "<trk><name>${escape(name)}</name><type>$TRACK_TYPE</type><trkseg>\n",
        )
    }

    /** Adds a point; samples without a position (e.g. GPS lost) are skipped. */
    public fun addPoint(sample: RideSample) {
        val lat = sample.latDeg ?: return
        val lon = sample.lonDeg ?: return
        val point = buildString {
            append("<trkpt lat=\"").append(coordinate(lat)).append("\" lon=\"").append(coordinate(lon)).append("\">")
            sample.altitudeM?.let {
                append("<ele>").append(String.format(Locale.ROOT, ONE_DECIMAL, it)).append("</ele>")
            }
            append("<time>").append(isoTime(sample.timeMs)).append("</time>")
            val hr = sample.heartRateBpm
            val cadence = sample.cadenceRpm
            if (hr != null || cadence != null) {
                append("<extensions><gpxtpx:TrackPointExtension>")
                hr?.let { append("<gpxtpx:hr>").append(it).append("</gpxtpx:hr>") }
                cadence?.let { append("<gpxtpx:cad>").append(it).append("</gpxtpx:cad>") }
                append("</gpxtpx:TrackPointExtension></extensions>")
            }
            append("</trkpt>\n")
        }
        write(point)
    }

    public fun end() {
        write("</trkseg></trk>\n</gpx>\n")
    }

    private fun write(text: String) {
        sink.write(text.toByteArray(Charsets.UTF_8))
    }

    private fun coordinate(value: Double): String = String.format(Locale.ROOT, COORDINATE_FORMAT, value)

    private fun isoTime(timeMs: Long): String = Instant.ofEpochSecond(timeMs / Units.MS_PER_SECOND).toString()

    public companion object {
        private const val XML_DECLARATION = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
        private const val CREATOR = "RideComp"
        private const val GPX_NS = "http://www.topografix.com/GPX/1/1"
        private const val TPX_NS = "http://www.garmin.com/xmlschemas/TrackPointExtension/v1"
        private const val TRACK_TYPE = "cycling"
        private const val COORDINATE_FORMAT = "%.7f"
        private const val ONE_DECIMAL = "%.1f"

        /** Escapes XML special characters in user text (ride names). */
        public fun escape(text: String): String = text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }
}
