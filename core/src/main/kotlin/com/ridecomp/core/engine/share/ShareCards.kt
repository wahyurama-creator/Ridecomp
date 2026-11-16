package com.ridecomp.core.engine.share

import com.ridecomp.core.config.Units
import com.ridecomp.core.engine.efforts.BestEffortConfig
import com.ridecomp.core.engine.geo.GeoMath
import com.ridecomp.core.engine.track.RideTrack
import kotlin.math.cos

/** Share card rules (12-FEATURE-SPECS §11). */
public object ShareCardConfig {
    /** "Sembunyikan awal & akhir rute" cuts this much from both ends (default on). */
    public const val HIDDEN_ENDS_M: Double = 500.0

    /** Pixel sizes of the two formats. */
    public const val STORY_WIDTH_PX: Int = 1_080
    public const val STORY_HEIGHT_PX: Int = 1_920
    public const val SQUARE_SIZE_PX: Int = 1_080

    /** At most this many statistics on a card. */
    public const val MAX_STATS: Int = 4

    /** PNG files live in the app cache and are deleted at the next app start (§11). */
    public const val FILE_PREFIX: String = "RideComp_card_"
    public const val FILE_EXTENSION: String = ".png"
    public const val MIME_TYPE: String = "image/png"

    /** Points of the elevation strip on a card. */
    public const val PROFILE_POINTS: Int = 80

    /** Split length (one bar per kilometre). */
    public const val SPLIT_M: Double = 1_000.0

    /** At most this many split bars fit on a card; longer rides show the first ones. */
    public const val MAX_SPLITS: Int = 20
}

/** Look of a card (T4.5b). */
public enum class ShareTemplate {
    /** Plain dark/light card (T4.5). */
    CLASSIC,

    /** Big numbers on a colour gradient, glowing route line, elevation strip. */
    POSTER,

    /** The rider's own photo behind the route and numbers. */
    PHOTO,

    /** Route and numbers only, transparent PNG to put on a photo or video in an Instagram Story. */
    STICKER,
}

/** Colour pairs of the poster gradient (defined as colours in the design system). */
public enum class SharePalette { SUNSET, OCEAN, FOREST, NIGHT }

/** Extra training details a card can show (T4.5b "Detail latihan"). */
public enum class ShareDetail { ELEVATION, HEART_RATE_ZONES, SPLITS, BADGES }

/** Training details of a ride for a card; empty lists when the ride has no such data. */
public data class ShareDetails(
    /** Altitude every few hundred metres, oldest first; null = no altitude there. */
    val elevationM: List<Double?> = emptyList(),
    /** Seconds in heart-rate zones Z1..Z5. */
    val zonesS: List<Long> = emptyList(),
    /** Moving seconds of each full kilometre. */
    val splitsS: List<Double> = emptyList(),
    /** New personal records and segment records of the ride. */
    val records: Int = 0,
    val segmentRecords: Int = 0,
)

/** Splits and elevation strip of a ride for a card (moving time: pauses never count, like best efforts). */
public object ShareCardDetails {
    public fun splits(track: RideTrack): List<Double> {
        val splits = mutableListOf<Double>()
        var splitStartMoving = 0.0
        var moving = 0.0
        var nextMark = ShareCardConfig.SPLIT_M
        for (i in 1 until track.size) {
            val gapS = (track.timeMs(i) - track.timeMs(i - 1)).toDouble() / Units.MS_PER_SECOND
            moving += if (gapS <= BestEffortConfig.MAX_CONTINUOUS_GAP_S) gapS else BestEffortConfig.SAMPLE_INTERVAL_S
            if (track.distanceM(i) - track.distanceM(0) >= nextMark) {
                splits += moving - splitStartMoving
                splitStartMoving = moving
                nextMark += ShareCardConfig.SPLIT_M
                if (splits.size == ShareCardConfig.MAX_SPLITS) break
            }
        }
        return splits
    }

    public fun elevation(track: RideTrack): List<Double?> {
        if (track.size < 2) return emptyList()
        val step = (track.size - 1).toDouble() / (ShareCardConfig.PROFILE_POINTS - 1)
        return (0 until ShareCardConfig.PROFILE_POINTS).map { n ->
            track.altitudeM((n * step).toInt().coerceAtMost(track.size - 1)).takeUnless(Double::isNaN)
        }.takeIf { values -> values.any { it != null } }.orEmpty()
    }
}

public enum class ShareFormat(public val widthPx: Int, public val heightPx: Int) {
    STORY(ShareCardConfig.STORY_WIDTH_PX, ShareCardConfig.STORY_HEIGHT_PX),
    SQUARE(ShareCardConfig.SQUARE_SIZE_PX, ShareCardConfig.SQUARE_SIZE_PX),
}

/** Statistics a card can show (§11: up to 4). */
public enum class ShareStat { DISTANCE, TIME, ASCENT, AVG_SPEED, AVG_HEART_RATE, AVG_POWER }

/** A point of the card's line in 0..1 (x right, y down), keeping the ride's proportions. */
public data class SharePoint(val x: Float, val y: Float)

/** A ride position and its distance from the start, as read for the card. */
public data class LinePoint(val latDeg: Double, val lonDeg: Double, val distanceM: Double)

/**
 * The ride's shape for a card (§11 privacy): optionally without the first and last 500 m, projected to 0..1
 * so no coordinate ever reaches the image. Empty when nothing is left to draw.
 */
public object ShareCardLine {
    public fun of(points: List<LinePoint>, isEndsHidden: Boolean): List<SharePoint> {
        val located = points.filter { GeoMath.isValidCoordinate(it.latDeg, it.lonDeg) }
        val total = located.lastOrNull()?.distanceM ?: return emptyList()
        val hidden = if (isEndsHidden) ShareCardConfig.HIDDEN_ENDS_M else 0.0
        val kept = located.filter { it.distanceM >= hidden && it.distanceM <= total - hidden }
        return if (kept.size < 2) emptyList() else normalize(kept)
    }

    /** Equirectangular projection fitted into the unit square, centred, north up. */
    private fun normalize(points: List<LinePoint>): List<SharePoint> {
        val lonScale = cos(Math.toRadians(points.first().latDeg))
        val xs = points.map { it.lonDeg * lonScale }
        val ys = points.map { it.latDeg }
        val minX = xs.min()
        val maxY = ys.max()
        val width = xs.max() - minX
        val height = maxY - ys.min()
        val span = maxOf(width, height).takeIf { it > 0 } ?: return emptyList()
        val offsetX = (1 - width / span) / 2
        val offsetY = (1 - height / span) / 2
        return points.indices.map { i ->
            SharePoint(
                x = (offsetX + (xs[i] - minX) / span).toFloat(),
                y = (offsetY + (maxY - ys[i]) / span).toFloat(),
            )
        }
    }
}
