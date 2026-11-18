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

    /** At most this many statistics on a card (the list and grid layouts show six, T10.6). */
    public const val MAX_STATS: Int = 6

    /** The classic, poster, photo and sticker cards show the first four in a 2 × 2 grid. */
    public const val GRID_STATS: Int = 4

    /** The one-row layouts show the first three. */
    public const val ROW_STATS: Int = 3

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

/** How a card is drawn behind its numbers. */
public enum class ShareBackdrop {
    /** A colour, gradient or the rider's photo (or transparent when the rider turns the background off). */
    FILLED,

    /** Always a transparent PNG, to put on a photo or video in an Instagram Story. */
    TRANSPARENT,

    /** A real map under the route: shows where the ride was, needs map tiles (T10.6). */
    MAP,
}

/** Look of a card (T4.5b; the transparent and map layouts after the owner's references, T10.6). */
public enum class ShareTemplate(public val backdrop: ShareBackdrop) {
    /** Plain dark/light card (T4.5). */
    CLASSIC(ShareBackdrop.FILLED),

    /** Big numbers on a colour gradient, glowing route line, elevation strip. */
    POSTER(ShareBackdrop.FILLED),

    /** The rider's own photo behind the route and numbers. */
    PHOTO(ShareBackdrop.FILLED),

    /** Route and numbers only, transparent PNG to put on a photo or video in an Instagram Story. */
    STICKER(ShareBackdrop.TRANSPARENT),

    /** Route on the left, the statistics written down the right edge. */
    ROUTE_SIDE_STATS(ShareBackdrop.TRANSPARENT),

    /** Logo and the statistics written down the right edge, no route. */
    SIDE_STATS(ShareBackdrop.TRANSPARENT),

    /** Logo and a dotted list of statistics at the bottom. */
    DOTTED_LIST(ShareBackdrop.TRANSPARENT),

    /** Route on top, dotted list below. */
    ROUTE_DOTTED_LIST(ShareBackdrop.TRANSPARENT),

    /** Logo and the statistics stacked in the middle. */
    CENTER_STACK(ShareBackdrop.TRANSPARENT),

    /** Stacked statistics drawn over the route. */
    ROUTE_UNDER_STACK(ShareBackdrop.TRANSPARENT),

    /** Logo, date and statistics as outlined pills. */
    PILLS(ShareBackdrop.TRANSPARENT),

    /** Ride icon and logo, three statistics in a row. */
    STAT_ROW(ShareBackdrop.TRANSPARENT),

    /** Ride icon and logo, statistics in rows of three. */
    STAT_GRID(ShareBackdrop.TRANSPARENT),

    /** Large route on the left, logo and three statistics on the right. */
    ROUTE_BESIDE_STATS(ShareBackdrop.TRANSPARENT),

    /** Route on top, logo, three statistics in a row and the ride icon. */
    ROUTE_OVER_ROW(ShareBackdrop.TRANSPARENT),

    /** Three statistics in very large type, nothing else. */
    BIG_STATS(ShareBackdrop.TRANSPARENT),

    /** Route and logo only. */
    ROUTE_LOGO(ShareBackdrop.TRANSPARENT),

    /** Three statistics, a small route and the logo. */
    STATS_SMALL_ROUTE(ShareBackdrop.TRANSPARENT),

    /** A ticket with the ride icon, the weekday and the date. */
    DATE_TICKET(ShareBackdrop.TRANSPARENT),

    /** The route on a map with the ride name and three statistics. */
    MAP(ShareBackdrop.MAP),

    /** A dark card with a framed map, the ride name and "recorded with RideComp". */
    MAP_FRAMED(ShareBackdrop.MAP),
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

/** Where a card is sent (T10.6): the app opens with the image, the rider picks story, post or chat there. */
public enum class ShareTarget {
    INSTAGRAM,
    WHATSAPP,

    /** The Android share sheet. */
    OTHER,
}

/** Statistics a card can show (§11: up to 4). */
public enum class ShareStat { DISTANCE, ASCENT, TIME, AVG_POWER, AVG_SPEED, CALORIES, AVG_HEART_RATE }

/** A point of the card's line in 0..1 (x right, y down), keeping the ride's proportions. */
public data class SharePoint(val x: Float, val y: Float)

/** A ride position and its distance from the start, as read for the card. */
public data class LinePoint(val latDeg: Double, val lonDeg: Double, val distanceM: Double)

/**
 * The ride's shape for a card (§11 privacy): optionally without the first and last 500 m, projected to 0..1
 * so no coordinate ever reaches the image. Empty when nothing is left to draw.
 */
public object ShareCardLine {
    public fun of(points: List<LinePoint>, isEndsHidden: Boolean): List<SharePoint> =
        trimmed(points, isEndsHidden).let { kept -> if (kept.isEmpty()) emptyList() else normalize(kept) }

    /**
     * The located points left to draw, optionally without the first and last 500 m; empty when fewer than two
     * remain. Only the map cards (T10.6) use these coordinates, and only to place the map.
     */
    public fun trimmed(points: List<LinePoint>, isEndsHidden: Boolean): List<LinePoint> {
        val located = points.filter { GeoMath.isValidCoordinate(it.latDeg, it.lonDeg) }
        val total = located.lastOrNull()?.distanceM ?: return emptyList()
        val hidden = if (isEndsHidden) ShareCardConfig.HIDDEN_ENDS_M else 0.0
        val kept = located.filter { it.distanceM >= hidden && it.distanceM <= total - hidden }
        return if (kept.size < 2) emptyList() else kept
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
