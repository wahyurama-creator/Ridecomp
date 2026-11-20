package com.ridecomp.core.repository

import com.ridecomp.core.engine.map.TrailPoint
import kotlinx.coroutines.flow.Flow

/** Where the rider last rode, from stored rides only: no GPS request (T11.12, D-113). */
public interface RidePositionRepository {
    /** Last position of the newest finished ride; null before the first ride with GPS. */
    public fun observeLastPosition(): Flow<TrailPoint?>
}
