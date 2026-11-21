package com.ridecomp.core.engine.sensors

/**
 * Whether a heart rate device is probably a watch or band rather than a strap (T12.3), from the name it
 * advertises. Only used for a label and an icon, so a wrong guess costs nothing.
 */
public object WatchNames {
    private val WATCH_WORDS = listOf(
        "watch",
        "band",
        "forerunner",
        "fenix",
        "f\u0113nix",
        "venu",
        "vivoactive",
        "instinct",
        "epix",
        "enduro",
        "amazfit",
        "gtr",
        "gts",
    )

    /** Straps and armbands of watch brands (e.g. "Amazfit Helio Strap", "HRM-Pro") stay sensors. */
    private val STRAP_WORDS = listOf("strap", "hrm", "chest")

    public fun looksLikeWatch(name: String?): Boolean {
        val lower = name?.lowercase() ?: return false
        return WATCH_WORDS.any { it in lower } && STRAP_WORDS.none { it in lower }
    }
}

/** A watch: a GloryFit watch (T12.5b) or a device whose name says so (T12.3). */
public val SavedSensor.isWatch: Boolean
    get() = isGloryFitWatch || (SensorKind.HEART_RATE in kinds && WatchNames.looksLikeWatch(name))
