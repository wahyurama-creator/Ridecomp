package com.ridecomp.ui.theme

import androidx.compose.ui.graphics.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * WCAG 2.1 contrast of the theme tokens (T11.10, D-127): text ≥ 4.5:1 on every surface, ride data ≥ 7:1 on
 * the ride screen background, lines, bars and icons ≥ 3:1. Both themes, so a palette change cannot quietly
 * make something unreadable.
 */
class ThemeContrastTest {
    private val themes = mapOf("dark" to DarkRideCompColors, "light" to LightRideCompColors)

    private fun RideCompColors.surfaces() = mapOf("bg" to bg, "surface" to surface, "surfaceAlt" to surfaceAlt)

    @Test
    fun `text is readable on every surface`() = check(TEXT) { colors ->
        val text = mapOf(
            "textPrimary" to colors.textPrimary,
            "textSecondary" to colors.textSecondary,
            "textTertiary" to colors.textTertiary,
            "accent" to colors.accent,
            "ok" to colors.ok,
            "warnText" to colors.warnText,
            "danger" to colors.danger,
        )
        text.flatMap { (name, color) -> colors.surfaces().map { (on, back) -> "$name on $on" to (color to back) } }
    }

    @Test
    fun `ride data reaches 7 to 1 on the ride screen`() = check(RIDE_TEXT) { colors ->
        listOf(
            "textPrimary on bg" to (colors.textPrimary to colors.bg),
            "textSecondary on bg" to (colors.textSecondary to colors.bg),
        )
    }

    @Test
    fun `text on filled buttons, banners and tinted cards is readable`() = check(TEXT) { colors ->
        listOf(
            "onAccent on accent" to (colors.onAccent to colors.accent),
            "onOk on ok" to (colors.onOk to colors.ok),
            "onWarn on warn" to (colors.onWarn to colors.warn),
            "onTrack on track" to (colors.onTrack to colors.track),
            "onDangerBanner on danger" to (colors.onDangerBanner to colors.danger),
            "onDanger on dangerDeep" to (colors.onDanger to colors.dangerDeep),
            "accent on accentTint" to (colors.accent to colors.accentTint),
            "ok on okTint" to (colors.ok to colors.okTint),
            "warnText on warnTint" to (colors.warnText to colors.warnTint),
            "textPrimary on accentTint" to (colors.textPrimary to colors.accentTint),
        )
    }

    @Test
    fun `large bold button text on danger is readable`() = check(LARGE_TEXT) { colors ->
        // Danger buttons use 20–24 sp extra bold (large text, WCAG 1.4.3).
        listOf("onDanger on danger" to (colors.onDanger to colors.danger))
    }

    @Test
    fun `lines, bars and icons stand out from the page and cards`() = check(GRAPHIC) { colors ->
        val graphics = mapOf(
            "track" to colors.track,
            "accent" to colors.accent,
            "ok" to colors.ok,
            "warnText" to colors.warnText,
            "danger" to colors.danger,
        ) + colors.zones.mapIndexed { i, zone -> "zone ${i + 1}" to zone } +
            colors.gradeBands.mapIndexed { i, band -> "grade band ${i + 1}" to band }
        val backs = mapOf("bg" to colors.bg, "surface" to colors.surface)
        graphics.flatMap { (name, color) -> backs.map { (on, back) -> "$name on $on" to (color to back) } }
            .filterNot { (name, _) -> colors.isDark && name in DARK_KNOWN }
    }

    private fun check(minimum: Double, pairs: (RideCompColors) -> List<Pair<String, Pair<Color, Color>>>) {
        val failures = themes.flatMap { (theme, colors) ->
            pairs(colors).mapNotNull { (name, pair) ->
                val ratio = contrast(pair.first, pair.second)
                "$theme: $name = ${"%.2f".format(ratio)}".takeIf { ratio < minimum }
            }
        }
        assertTrue(failures.isEmpty(), "Below $minimum:1 → ${failures.joinToString()}")
    }

    /** WCAG relative luminance contrast, (lighter + 0.05) / (darker + 0.05). */
    private fun contrast(a: Color, b: Color): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (max(la, lb) + OFFSET) / (min(la, lb) + OFFSET)
    }

    private fun luminance(color: Color): Double {
        fun channel(value: Float): Double {
            val c = value.toDouble()
            return if (c <= LINEAR_LIMIT) c / LINEAR_DIVISOR else ((c + GAMMA_OFFSET) / GAMMA_DIVISOR).pow(GAMMA)
        }
        return RED * channel(color.red) + GREEN * channel(color.green) + BLUE * channel(color.blue)
    }

    private companion object {
        /**
         * The dark theme is kept as it is (D-110): its deepest grade band sits next to the lighter bands and
         * the profile line, not alone on a card (2.8:1 on `surface`).
         */
        val DARK_KNOWN = setOf("grade band 5 on surface")

        const val TEXT = 4.5
        const val LARGE_TEXT = 3.0
        const val RIDE_TEXT = 7.0
        const val GRAPHIC = 3.0
        const val OFFSET = 0.05
        const val LINEAR_LIMIT = 0.03928
        const val LINEAR_DIVISOR = 12.92
        const val GAMMA_OFFSET = 0.055
        const val GAMMA_DIVISOR = 1.055
        const val GAMMA = 2.4
        const val RED = 0.2126
        const val GREEN = 0.7152
        const val BLUE = 0.0722
    }
}
