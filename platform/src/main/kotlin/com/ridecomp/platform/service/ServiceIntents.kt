package com.ridecomp.platform.service

import android.content.Context
import android.content.Intent
import com.ridecomp.core.engine.recording.RideAction

/** Explicit intents to [RecordingService] (never exported, SEC-PLT-02). Names in one place. */
internal object ServiceIntents {
    const val ACTION_START = "com.ridecomp.platform.action.START"
    const val ACTION_RESUME_RECOVERED = "com.ridecomp.platform.action.RESUME_RECOVERED"
    const val ACTION_PERFORM = "com.ridecomp.platform.action.PERFORM"
    const val EXTRA_ACTIVITY_ID = "com.ridecomp.platform.extra.ACTIVITY_ID"
    const val EXTRA_RIDE_ACTION = "com.ridecomp.platform.extra.RIDE_ACTION"
    const val NO_ID = -1L

    fun start(context: Context): Intent = Intent(context, RecordingService::class.java).setAction(ACTION_START)

    fun resumeRecovered(context: Context, activityId: Long): Intent = Intent(context, RecordingService::class.java)
        .setAction(ACTION_RESUME_RECOVERED)
        .putExtra(EXTRA_ACTIVITY_ID, activityId)

    fun perform(context: Context, action: RideAction): Intent = Intent(context, RecordingService::class.java)
        .setAction(ACTION_PERFORM)
        .putExtra(EXTRA_RIDE_ACTION, action.name)
}
