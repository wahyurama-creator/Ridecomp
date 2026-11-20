package com.ridecomp.core.repository

import kotlinx.coroutines.flow.Flow

/** Which region packs are installed and which offers were declined (T11.12, stored in `settings`). */
public interface MapPackStateRepository {
    /** Region id → index version it was downloaded from. */
    public fun observeInstalled(): Flow<Map<String, String>>

    /** [version] null forgets the region. */
    public suspend fun setInstalled(regionId: String, version: String?)

    /** Offers declined with "Later", as `id@version`. */
    public fun observeDismissed(): Flow<Set<String>>

    public suspend fun dismiss(offerKey: String)

    /** Downloads also on mobile data; Wi‑Fi only by default. */
    public fun observeMobileDataAllowed(): Flow<Boolean>

    public suspend fun setMobileDataAllowed(isAllowed: Boolean)
}
