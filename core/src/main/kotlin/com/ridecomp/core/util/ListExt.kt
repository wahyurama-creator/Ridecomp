package com.ridecomp.core.util

import kotlin.math.ceil

/** Every n-th element so that at most [maxSize] remain (charts, 02-DESIGN §6); the order is kept. */
public fun <T> List<T>.downsample(maxSize: Int): List<T> {
    if (size <= maxSize || maxSize <= 0) return this
    val stride = ceil(size.toDouble() / maxSize).toInt()
    return filterIndexed { index, _ -> index % stride == 0 }
}
