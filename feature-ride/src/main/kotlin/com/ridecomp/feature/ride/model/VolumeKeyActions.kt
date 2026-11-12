package com.ridecomp.feature.ride.model

import com.ridecomp.core.engine.recording.RideAction
import com.ridecomp.core.model.RideStatus
import com.ridecomp.ui.util.VolumeKey

/** Volume Up = lap, Volume Down = pause/resume (02-DESIGN §3.4); null when the key does nothing now. */
fun VolumeKey.rideAction(status: RideStatus): RideAction? = when (this) {
    VolumeKey.UP -> when (status) {
        RideStatus.RECORDING, RideStatus.AUTO_PAUSED -> RideAction.LAP
        RideStatus.PAUSED, RideStatus.FINISHED, RideStatus.IDLE -> null
    }
    VolumeKey.DOWN -> when (status) {
        RideStatus.RECORDING, RideStatus.AUTO_PAUSED -> RideAction.PAUSE
        RideStatus.PAUSED -> RideAction.RESUME
        RideStatus.FINISHED, RideStatus.IDLE -> null
    }
}
