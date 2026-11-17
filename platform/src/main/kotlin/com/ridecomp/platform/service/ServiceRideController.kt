package com.ridecomp.platform.service

import android.content.Context
import androidx.core.content.ContextCompat
import com.ridecomp.core.engine.recording.LiveRide
import com.ridecomp.core.engine.recording.RideAction
import com.ridecomp.core.engine.safety.IncidentAction
import com.ridecomp.core.source.RideController
import kotlinx.coroutines.flow.StateFlow

/**
 * [RideController] that drives [RecordingService] with explicit intents. Every command goes through
 * `startForegroundService`; the service calls `startForeground` first thing (STB-03).
 */
class ServiceRideController(private val context: Context, private val holder: RideStateHolder) : RideController {
    override val liveRide: StateFlow<LiveRide> get() = holder.liveRide
    override val activeActivityId: StateFlow<Long?> get() = holder.activeActivityId

    override fun start(profileId: Long?, routeId: Long?, withSession: Boolean) {
        ContextCompat.startForegroundService(context, ServiceIntents.start(context, profileId, routeId, withSession))
    }

    override fun resumeRecovered(activityId: Long) {
        ContextCompat.startForegroundService(context, ServiceIntents.resumeRecovered(context, activityId))
    }

    override fun perform(action: RideAction) {
        ContextCompat.startForegroundService(context, ServiceIntents.perform(context, action))
    }

    override fun performIncident(action: IncidentAction) {
        ContextCompat.startForegroundService(context, ServiceIntents.incident(context, action))
    }
}
