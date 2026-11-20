package com.ridecomp.platform.system

import android.net.TrafficStats

/**
 * Traffic stats tags of RideComp's own connections. Android counts tagged sockets per purpose; an untagged
 * socket trips StrictMode (STB-01). Any stable values; one per kind of request.
 */
object TrafficTags {
    const val UPDATE_CHECK: Int = 0x5243_0001
    const val STRAVA: Int = 0x5243_0002
    const val MAP_PACKS: Int = 0x5243_0003
}

/** Runs [block] with this thread's sockets tagged [tag], then clears the tag. */
inline fun <T> withTrafficTag(tag: Int, block: () -> T): T {
    TrafficStats.setThreadStatsTag(tag)
    try {
        return block()
    } finally {
        TrafficStats.clearThreadStatsTag()
    }
}
