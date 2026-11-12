package com.ridecomp.feature.ride

import com.ridecomp.core.engine.recording.RideAction
import com.ridecomp.core.model.RideStatus
import com.ridecomp.feature.ride.model.rideAction
import com.ridecomp.ui.util.VolumeKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class VolumeKeyActionsTest {
    @Test
    fun `volume up is lap while riding`() {
        assertEquals(RideAction.LAP, VolumeKey.UP.rideAction(RideStatus.RECORDING))
        assertEquals(RideAction.LAP, VolumeKey.UP.rideAction(RideStatus.AUTO_PAUSED))
        assertNull(VolumeKey.UP.rideAction(RideStatus.PAUSED))
    }

    @Test
    fun `volume down toggles pause and does nothing after the ride`() {
        assertEquals(RideAction.PAUSE, VolumeKey.DOWN.rideAction(RideStatus.RECORDING))
        assertEquals(RideAction.RESUME, VolumeKey.DOWN.rideAction(RideStatus.PAUSED))
        assertNull(VolumeKey.DOWN.rideAction(RideStatus.FINISHED))
        assertNull(VolumeKey.UP.rideAction(RideStatus.IDLE))
    }
}
