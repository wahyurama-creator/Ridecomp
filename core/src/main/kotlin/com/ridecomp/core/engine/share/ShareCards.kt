package com.ridecomp.core.engine.share

import com.ridecomp.core.engine.geo.GeoMath
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
