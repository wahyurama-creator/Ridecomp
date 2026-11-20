package com.ridecomp.core.source

/** The online map's tile cache on the phone (T11.11): tiles seen online, reused without internet. */
public interface MapCacheSource {
    /** Space used now, in bytes. */
    public suspend fun sizeBytes(): Long

    /** Deletes every cached tile. */
    public suspend fun clear()
}
