package com.ridecomp.core.engine.route

import com.ridecomp.core.util.Outcome
import com.ridecomp.core.util.asFailure
import com.ridecomp.core.util.asSuccess
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.Reader

/** Limits for imported GPX files (04-INTEGRATIONS §C.1, SEC-IN-01/02). */
public object GpxLimits {
    public const val MAX_BYTES: Long = 20L * 1024 * 1024
    public const val MAX_POINTS: Int = 50_000
    public const val MAX_WAYPOINTS: Int = 1_000
    public const val MAX_NAME_LENGTH: Int = 100

    /** Lowest and highest plausible elevation on earth (Dead Sea shore, Everest, with margin). */
    public const val MIN_ELEVATION_M: Double = -500.0
    public const val MAX_ELEVATION_M: Double = 9_000.0
    public const val MAX_LATITUDE: Double = 90.0
    public const val MAX_LONGITUDE: Double = 180.0

    /** Bounded element nesting and text, so a crafted file cannot exhaust memory. */
    public const val MAX_DEPTH: Int = 64
    public const val MAX_TEXT_LENGTH: Int = 1_024
    public const val MAX_TAG_LENGTH: Int = 4_096
}

public enum class GpxError { TOO_LARGE, TOO_MANY_POINTS, DOCTYPE_NOT_ALLOWED, MALFORMED, NO_POINTS, INVALID_VALUE }

public data class GpxPoint(val latDeg: Double, val lonDeg: Double, val elevationM: Double?)

public data class GpxWaypoint(val latDeg: Double, val lonDeg: Double, val name: String?)

public data class ParsedGpx(val name: String?, val points: List<GpxPoint>, val waypoints: List<GpxWaypoint>)

/**
 * Minimal streaming GPX reader (decision D-43). It understands just enough XML for GPX: elements,
 * attributes, text, comments, CDATA and the XML declaration. A DOCTYPE is **rejected**, and the only
 * entities are the five predefined ones and numeric references, so XXE and entity expansion cannot happen
 * by construction (SEC-IN-01). Track points (`trkpt`) and route points (`rtept`) of all segments are joined;
 * every value is range-checked (SEC-IN-02). Never throws for bad input.
 */
public object GpxParser {
    public fun parse(input: InputStream): Outcome<ParsedGpx, GpxError> = try {
        val reader = InputStreamReader(LimitedInputStream(input, GpxLimits.MAX_BYTES), Charsets.UTF_8).buffered()
        GpxHandler().also { XmlTokenizer(reader, it).run() }.result()
    } catch (e: GpxException) {
        e.error.asFailure()
    } catch (_: IOException) {
        GpxError.MALFORMED.asFailure()
    }
}

private class GpxException(val error: GpxError) : IOException(error.name)

private fun gpxFail(error: GpxError): Nothing = throw GpxException(error)

/** Throws once more than [limit] bytes were read (SEC-IN-01: size limit while streaming). */
private class LimitedInputStream(private val input: InputStream, private val limit: Long) : InputStream() {
    private var count = 0L

    override fun read(): Int = input.read().also { if (it >= 0) count(1) }

    override fun read(b: ByteArray, off: Int, len: Int): Int = input.read(b, off, len).also { if (it > 0) count(it) }

    override fun close() = input.close()

    private fun count(bytes: Int) {
        count += bytes
        if (count > limit) gpxFail(GpxError.TOO_LARGE)
    }
}

/** Receives XML events. */
private interface XmlHandler {
    fun start(name: String, attributes: Map<String, String>)

    fun end(name: String)

    fun text(text: String)
}

/** Builds [ParsedGpx] from XML events. */
private class GpxHandler : XmlHandler {
    private val path = ArrayList<String>()
    private val points = ArrayList<GpxPoint>()
    private val waypoints = ArrayList<GpxWaypoint>()
    private var name: String? = null
    private var pointLat = 0.0
    private var pointLon = 0.0
    private var pointEle: Double? = null
    private var pointName: String? = null
    private val text = StringBuilder()

    override fun start(name: String, attributes: Map<String, String>) {
        if (path.size >= GpxLimits.MAX_DEPTH) gpxFail(GpxError.MALFORMED)
        path += name
        text.setLength(0)
        if (name in POINT_ELEMENTS) {
            pointLat = attributes[LAT]?.toCoordinate(GpxLimits.MAX_LATITUDE) ?: gpxFail(GpxError.INVALID_VALUE)
            pointLon = attributes[LON]?.toCoordinate(GpxLimits.MAX_LONGITUDE) ?: gpxFail(GpxError.INVALID_VALUE)
            pointEle = null
            pointName = null
        }
    }

    /** Only `ele` and `name` text is kept, capped; descriptions of any length are ignored. */
    override fun text(text: String) {
        if (path.lastOrNull() !in TEXT_ELEMENTS) return
        val room = GpxLimits.MAX_TEXT_LENGTH - this.text.length
        if (room > 0) this.text.append(text, 0, minOf(room, text.length))
    }

    override fun end(name: String) {
        if (path.lastOrNull() != name) gpxFail(GpxError.MALFORMED)
        val parent = path.getOrNull(path.size - 2)
        val value = text.toString().trim()
        when {
            name == ELE && parent in POINT_ELEMENTS -> pointEle = value.toElevation()
            name == NAME && parent in POINT_ELEMENTS -> pointName = value.take(GpxLimits.MAX_NAME_LENGTH)
            name == NAME && parent in NAMED_ELEMENTS && this.name == null ->
                this.name = value.take(GpxLimits.MAX_NAME_LENGTH).ifBlank { null }
            name == TRKPT || name == RTEPT -> addPoint()
            name == WPT -> if (waypoints.size < GpxLimits.MAX_WAYPOINTS) {
                waypoints += GpxWaypoint(pointLat, pointLon, pointName?.ifBlank { null })
            }
        }
        path.removeAt(path.lastIndex)
        text.setLength(0)
    }

    fun result(): Outcome<ParsedGpx, GpxError> = when {
        path.isNotEmpty() -> GpxError.MALFORMED.asFailure()
        points.size < MIN_POINTS -> GpxError.NO_POINTS.asFailure()
        else -> ParsedGpx(name, points, waypoints).asSuccess()
    }

    private fun addPoint() {
        if (points.size >= GpxLimits.MAX_POINTS) gpxFail(GpxError.TOO_MANY_POINTS)
        points += GpxPoint(pointLat, pointLon, pointEle)
    }

    private fun String.toCoordinate(max: Double): Double? =
        trim().toDoubleOrNull()?.takeIf { it.isFinite() && it in -max..max }

    /** Missing or empty elevation is allowed (null); a present but invalid one is not. */
    private fun String.toElevation(): Double? {
        if (isEmpty()) return null
        return toDoubleOrNull()
            ?.takeIf { it.isFinite() && it in GpxLimits.MIN_ELEVATION_M..GpxLimits.MAX_ELEVATION_M }
            ?: gpxFail(GpxError.INVALID_VALUE)
    }

    private companion object {
        const val TRKPT = "trkpt"
        const val RTEPT = "rtept"
        const val WPT = "wpt"
        const val ELE = "ele"
        const val NAME = "name"
        const val LAT = "lat"
        const val LON = "lon"
        const val MIN_POINTS = 2
        val POINT_ELEMENTS = setOf(TRKPT, RTEPT, WPT)
        val NAMED_ELEMENTS = setOf("metadata", "trk", "rte")
        val TEXT_ELEMENTS = setOf(ELE, NAME)
    }
}

/** Tokenizer for the XML subset above; names lose their namespace prefix (`gpx:trkpt` → `trkpt`). */
private class XmlTokenizer(private val reader: Reader, private val handler: XmlHandler) {
    private var pushedBack = NONE

    fun run() {
        val text = StringBuilder()
        while (true) {
            val c = next()
            if (c == NONE) break
            if (c == '<'.code) {
                flushText(text)
                markup()
            } else {
                text.append(c.toChar())
                if (text.length > GpxLimits.MAX_TEXT_LENGTH) flushText(text)
            }
        }
        flushText(text)
    }

    private fun flushText(text: StringBuilder) {
        if (text.isEmpty()) return
        val decoded = decodeEntities(text.toString())
        text.setLength(0)
        if (decoded.isNotBlank()) handler.text(decoded)
    }

    private fun markup() {
        when (val c = next()) {
            '?'.code -> skipUntil("?>")
            '!'.code -> declaration()
            '/'.code -> handler.end(localName(readUntil('>').trim()))
            NONE -> gpxFail(GpxError.MALFORMED)
            else -> {
                pushedBack = c
                element(readTag())
            }
        }
    }

    private fun declaration() {
        val head = StringBuilder()
        while (head.length < DECLARATION_PREFIX) {
            val c = next()
            if (c == NONE) gpxFail(GpxError.MALFORMED)
            head.append(c.toChar())
            when (head.toString()) {
                COMMENT_START -> return skipUntil("-->")
                CDATA_START -> return handler.text(readUntilSequence("]]>"))
            }
        }
        // DOCTYPE, ENTITY and every other declaration are refused (SEC-IN-01).
        gpxFail(if (head.startsWith(DOCTYPE_START)) GpxError.DOCTYPE_NOT_ALLOWED else GpxError.MALFORMED)
    }

    /** Reads `name attr="v" …` up to `>`, honouring quotes. */
    private fun readTag(): String {
        val tag = StringBuilder()
        var quote = NONE
        while (true) {
            val c = next()
            if (c == NONE || tag.length > GpxLimits.MAX_TAG_LENGTH) gpxFail(GpxError.MALFORMED)
            if (quote == NONE && c == '>'.code) return tag.toString()
            if (c == '"'.code || c == '\''.code) {
                quote = if (quote == NONE) {
                    c
                } else if (quote == c) {
                    NONE
                } else {
                    quote
                }
            }
            tag.append(c.toChar())
        }
    }

    private fun element(tag: String) {
        val isEmpty = tag.endsWith('/')
        val body = if (isEmpty) tag.dropLast(1) else tag
        val nameEnd = body.indexOfFirst { it.isWhitespace() }.let { if (it < 0) body.length else it }
        val name = localName(body.substring(0, nameEnd))
        if (name.isEmpty()) gpxFail(GpxError.MALFORMED)
        handler.start(name, attributes(body.substring(nameEnd)))
        if (isEmpty) handler.end(name)
    }

    private fun attributes(source: String): Map<String, String> {
        val result = HashMap<String, String>()
        ATTRIBUTE.findAll(source).forEach { match ->
            val value = match.groupValues[VALUE_DOUBLE].ifEmpty { match.groupValues[VALUE_SINGLE] }
            result[localName(match.groupValues[NAME_GROUP])] = decodeEntities(value)
        }
        return result
    }

    private fun localName(qualified: String): String = qualified.substringAfter(':')

    private fun next(): Int {
        if (pushedBack != NONE) return pushedBack.also { pushedBack = NONE }
        return reader.read()
    }

    private fun readUntil(end: Char): String {
        val out = StringBuilder()
        while (true) {
            val c = next()
            if (c == NONE || out.length > GpxLimits.MAX_TAG_LENGTH) gpxFail(GpxError.MALFORMED)
            if (c == end.code) return out.toString()
            out.append(c.toChar())
        }
    }

    /** CDATA of any length is scanned; only its first [GpxLimits.MAX_TEXT_LENGTH] chars are kept. */
    private fun readUntilSequence(end: String): String {
        val kept = StringBuilder()
        scanUntil(end) { c -> if (kept.length < GpxLimits.MAX_TEXT_LENGTH) kept.append(c) }
        return kept.toString()
    }

    /** Comments and processing instructions may be long; they are skipped without being kept. */
    private fun skipUntil(end: String) = scanUntil(end) { }

    /**
     * Reads until [end] (exclusive), passing the content to [onChar]. A sliding window of the last chars
     * finds the terminator even after partial repeats such as `]]]>`.
     */
    private inline fun scanUntil(end: String, onChar: (Char) -> Unit) {
        val window = ArrayDeque<Char>(end.length)
        while (true) {
            val c = next()
            if (c == NONE) gpxFail(GpxError.MALFORMED)
            window.addLast(c.toChar())
            if (window.size > end.length) onChar(window.removeFirst())
            if (window.size == end.length && window.joinToString("") == end) return
        }
    }

    /** Only `&lt; &gt; &amp; &quot; &apos;` and numeric references; anything else is malformed. */
    private fun decodeEntities(text: String): String {
        if ('&' !in text) return text
        val out = StringBuilder(text.length)
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (c != '&') {
                out.append(c)
                i++
                continue
            }
            val end = text.indexOf(';', i)
            if (end < 0 || end - i > MAX_ENTITY_LENGTH) gpxFail(GpxError.MALFORMED)
            out.append(entity(text.substring(i + 1, end)))
            i = end + 1
        }
        return out.toString()
    }

    private fun entity(name: String): String = PREDEFINED[name] ?: when {
        name.startsWith("#x") -> name.drop(2).toIntOrNull(HEX)
        name.startsWith("#") -> name.drop(1).toIntOrNull()
        else -> null
    }?.takeIf { Character.isValidCodePoint(it) }?.let { String(Character.toChars(it)) } ?: gpxFail(GpxError.MALFORMED)

    private companion object {
        const val NONE = -1
        const val HEX = 16
        const val MAX_ENTITY_LENGTH = 10
        const val COMMENT_START = "--"
        const val CDATA_START = "[CDATA["
        const val DOCTYPE_START = "DOCTYPE"
        const val DECLARATION_PREFIX = 7
        const val NAME_GROUP = 1
        const val VALUE_DOUBLE = 2
        const val VALUE_SINGLE = 3
        val ATTRIBUTE = Regex("""([A-Za-z_][\w.:-]*)\s*=\s*(?:"([^"]*)"|'([^']*)')""")
        val PREDEFINED = mapOf("lt" to "<", "gt" to ">", "amp" to "&", "quot" to "\"", "apos" to "'")
    }
}
