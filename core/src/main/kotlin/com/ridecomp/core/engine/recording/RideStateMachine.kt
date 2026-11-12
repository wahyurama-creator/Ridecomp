package com.ridecomp.core.engine.recording

import com.ridecomp.core.model.RideEventType
import com.ridecomp.core.model.RideStatus
import com.ridecomp.core.util.Outcome
import com.ridecomp.core.util.asFailure
import com.ridecomp.core.util.asSuccess

/** Everything that can change the ride status. */
public enum class RideCommand { START, PAUSE, RESUME, AUTO_PAUSE, AUTO_RESUME, FINISH, SAVE, DISCARD }

/** Result of an accepted command: the new status and the event to record (null = nothing to record). */
public data class RideTransition(val status: RideStatus, val event: RideEventType?)

/** A command that is not allowed in the current status (e.g. STOP while recording, 02-DESIGN §3.4). */
public data class InvalidRideCommand(val from: RideStatus, val command: RideCommand)

/**
 * Ride lifecycle (03-ARCHITECTURE §4.2):
 * ```
 * idle ─start→ recording ─pause→ paused ─resume→ recording
 * recording ─autoPause→ autoPaused ─(moving)→ recording ; autoPaused ─pause→ paused
 * paused|autoPaused ─finish→ finished ─save→ idle
 * paused|finished ─discard→ idle
 * ```
 * Finishing is only possible from a paused state (stop lives on the pause screen to avoid mistakes).
 * Pure function: holds no state.
 */
public object RideStateMachine {
    private val transitions: Map<Pair<RideStatus, RideCommand>, RideTransition> = mapOf(
        (RideStatus.IDLE to RideCommand.START) to RideTransition(RideStatus.RECORDING, RideEventType.START),
        (RideStatus.RECORDING to RideCommand.PAUSE) to RideTransition(RideStatus.PAUSED, RideEventType.PAUSE),
        (RideStatus.RECORDING to RideCommand.AUTO_PAUSE) to
            RideTransition(RideStatus.AUTO_PAUSED, RideEventType.AUTO_PAUSE),
        (RideStatus.PAUSED to RideCommand.RESUME) to RideTransition(RideStatus.RECORDING, RideEventType.RESUME),
        (RideStatus.AUTO_PAUSED to RideCommand.AUTO_RESUME) to
            RideTransition(RideStatus.RECORDING, RideEventType.AUTO_RESUME),
        // Manual pause while auto-paused: timer is already stopped, so no new event.
        (RideStatus.AUTO_PAUSED to RideCommand.PAUSE) to RideTransition(RideStatus.PAUSED, null),
        // "Continue" on the idle prompt while auto-paused keeps waiting for movement.
        (RideStatus.AUTO_PAUSED to RideCommand.RESUME) to RideTransition(RideStatus.AUTO_PAUSED, null),
        (RideStatus.PAUSED to RideCommand.FINISH) to RideTransition(RideStatus.FINISHED, RideEventType.STOP),
        (RideStatus.AUTO_PAUSED to RideCommand.FINISH) to RideTransition(RideStatus.FINISHED, RideEventType.STOP),
        (RideStatus.FINISHED to RideCommand.SAVE) to RideTransition(RideStatus.IDLE, null),
        (RideStatus.PAUSED to RideCommand.DISCARD) to RideTransition(RideStatus.IDLE, null),
        (RideStatus.FINISHED to RideCommand.DISCARD) to RideTransition(RideStatus.IDLE, null),
    )

    /** Applies [command] to [from]. */
    public fun apply(from: RideStatus, command: RideCommand): Outcome<RideTransition, InvalidRideCommand> =
        transitions[from to command]?.asSuccess() ?: InvalidRideCommand(from, command).asFailure()

    /** Statuses in which the moving-time clock runs. */
    public fun isMoving(status: RideStatus): Boolean = status == RideStatus.RECORDING

    /** Statuses in which a ride exists and must be protected by the foreground service. */
    public fun isActive(status: RideStatus): Boolean = when (status) {
        RideStatus.RECORDING, RideStatus.PAUSED, RideStatus.AUTO_PAUSED -> true
        RideStatus.IDLE, RideStatus.FINISHED -> false
    }
}
