package com.ridecomp.core.engine.sensors

/**
 * Bluetooth names that mark a watch or fitness band (T12.3, D-137). Matched case-insensitively as parts
 * of the advertised name. The brands also sell chest straps and sensors, so only watch model names are
 * listed for them, and [NOT_A_WATCH] wins over a match.
 */
public object WatchNames {
    /** Parts of a watch's or band's Bluetooth name (smartwatch-compat.md and brand model lines). */
    public val WATCH_PARTS: List<String> = listOf(
        // Generic words used by many brands (e.g. "HUAWEI WATCH HR-E57", "Xiaomi Smart Band 8").
        "watch",
        "band",
        // Garmin watch lines.
        "forerunner",
        "fenix",
        "epix",
        "venu",
        "vivoactive",
        "instinct",
        "enduro",
        // Polar watch lines.
        "vantage",
        "pacer",
        "grit x",
        "ignite",
        "unite",
        // COROS watch lines.
        "coros pace",
        "coros apex",
        "coros vertix",
        // Suunto watch lines.
        "suunto race",
        "suunto vertical",
        "suunto 9",
        "suunto 5",
        // Amazfit / Zepp and similar watch lines (e.g. "Aolon GTR3").
        "amazfit",
        "zepp",
        "gtr",
        "gts",
        "t-rex",
    )

    /** Parts that mark a chest strap, armband or bike sensor even when a watch part also matches. */
    public val NOT_A_WATCH: List<String> = listOf(
        "hrm",
        "strap",
        "tickr",
        "h10",
        "h9",
        "oh1",
        "verity",
        "sensor",
    )
}

/**
 * Whether a device with this Bluetooth [name] is most likely a watch or band (T12.3). Only a display hint
 * (label and icon); it never changes how the sensor is connected or read. A blank name is not a watch.
 */
public fun isLikelyWatch(name: String?): Boolean {
    val lower = name?.trim()?.lowercase().orEmpty()
    return lower.isNotEmpty() &&
        WatchNames.NOT_A_WATCH.none { it in lower } &&
        WatchNames.WATCH_PARTS.any { it in lower }
}
