package com.ridecomp.core.repository

import com.ridecomp.core.model.GuidePreferences
import com.ridecomp.core.model.GuideTip
import com.ridecomp.core.model.RiderLevel
import kotlinx.coroutines.flow.Flow

/** Rider level and closed tips (Phase 4G), stored with the settings. Implemented in :data. */
public interface GuideRepository {
    public fun observe(): Flow<GuidePreferences>

    public suspend fun setLevel(level: RiderLevel)

    public suspend fun dismiss(tip: GuideTip)

    public suspend fun resetTips()
}
