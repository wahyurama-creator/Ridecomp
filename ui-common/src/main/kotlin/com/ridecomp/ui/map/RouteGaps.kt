package com.ridecomp.ui.map

/** A route cut where the GPS was lost: the parts drawn from fixes, and the straight guesses across each gap. */
internal class RouteParts(val withFix: List<List<MapPoint>>, val guessed: List<List<MapPoint>>)

/**
 * Splits the line at [gaps] (index i = the line from point i to point i + 1, D-149). Without gaps the whole
 * line is one part with a fix.
 */
internal fun List<MapPoint>.splitAtGaps(gaps: List<Int>): RouteParts {
    val cuts = gaps.filter { it in 0 until lastIndex }.distinct().sorted()
    if (cuts.isEmpty()) return RouteParts(listOf(this), emptyList())
    val withFix = mutableListOf<List<MapPoint>>()
    val guessed = mutableListOf<List<MapPoint>>()
    var start = 0
    cuts.forEach { i ->
        withFix += subList(start, i + 1)
        guessed += listOf(this[i], this[i + 1])
        start = i + 1
    }
    withFix += subList(start, size)
    return RouteParts(withFix, guessed)
}
