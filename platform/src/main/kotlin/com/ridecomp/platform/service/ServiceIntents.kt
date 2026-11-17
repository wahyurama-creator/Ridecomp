package com.ridecomp.platform.service

import android.content.Context
import android.content.Intent
import com.ridecomp.core.engine.recording.RideAction
import com.ridecomp.core.engine.safety.IncidentAction

/** Explicit intents to [RecordingService] (never exported, SEC-PLT-02). Names in one place. */
internal object ServiceIntents {
    const val ACTION_START = "com.ridecomp.platform.action.START"
    const val ACTION_RESUME_RECOVERED = "com.ridecomp.platform.action.RESUME_RECOVERED"
    const val ACTION_PERFORM = "com.ridecomp.platform.action.PERFORM"
    const val ACTION_INCIDENT = "com.ridecomp.platform.action.INCIDENT"
    const val EXTRA_INCIDENT_ACTION = "com.ridecomp.platform.extra.INCIDENT_ACTION"
    const val EXTRA_ACTIVITY_ID = "com.ridecomp.platform.extra.ACTIVITY_ID"
    const val EXTRA_RIDE_ACTION = "com.ridecomp.platform.extra.RIDE_ACTION"
    const val EXTRA_PROFILE_ID = "com.ridecomp.platform.extra.PROFILE_ID"
    const val EXTRA_ROUTE_ID = "com.ridecomp.platform.extra.ROUTE_ID"
    const val EXTRA_WITH_SESSION = "com.ridecomp.platform.extra.WITH_SESSION"
    const val NO_ID = -1L

    fun start(context: Context, profileId: Long?, routeId: Long?, withSession: Boolean): Intent =
        Intent(context, RecordingService::class.java)
            .setAction(ACTION_START)
            .putExtra(EXTRA_PROFILE_ID, profileId ?: NO_ID)
            .putExtra(EXTRA_ROUTE_ID, routeId ?: NO_ID)
            .putExtra(EXTRA_WITH_SESSION, withSession)

    fun resumeRecovered(context: Context, activityId: Long): Intent = Intent(context, RecordingService::class.java)
        .setAction(ACTION_RESUME_RECOVERED)
        .putExtra(EXTRA_ACTIVITY_ID, activityId)

    fun perform(context: Context, action: RideAction): Intent = Intent(context, RecordingService::class.java)
        .setAction(ACTION_PERFORM)
        .putExtra(EXTRA_RIDE_ACTION, action.name)

    fun incident(context: Context, action: IncidentAction): Intent = Intent(context, RecordingService::class.java)
        .setAction(ACTION_INCIDENT)
        .putExtra(EXTRA_INCIDENT_ACTION, action.name)
}
