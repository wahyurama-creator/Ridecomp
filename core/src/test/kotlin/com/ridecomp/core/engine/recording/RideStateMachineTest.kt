package com.ridecomp.core.engine.recording

import com.ridecomp.core.model.RideEventType
import com.ridecomp.core.model.RideStatus
import com.ridecomp.core.util.Outcome
import com.ridecomp.core.util.getOrNull
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class RideStateMachineTest {
    private fun run(vararg commands: RideCommand): List<RideTransition> {
        var status = RideStatus.IDLE
        return commands.map { command ->
            val transition = requireNotNull(RideStateMachine.apply(status, command).getOrNull()) {
                "$command rejected in $status"
            }
            status = transition.status
            transition
        }
    }

    @Test
    fun `full manual ride emits start pause resume pause stop`() {
        val events = run(
            RideCommand.START,
            RideCommand.PAUSE,
            RideCommand.RESUME,
            RideCommand.PAUSE,
            RideCommand.FINISH,
            RideCommand.SAVE,
        ).map { it.event }

        assertEquals(
            listOf(
                RideEventType.START,
                RideEventType.PAUSE,
                RideEventType.RESUME,
                RideEventType.PAUSE,
                RideEventType.STOP,
                null,
            ),
            events,
        )
    }

    @Test
    fun `auto pause cycle records auto events`() {
        val transitions = run(RideCommand.START, RideCommand.AUTO_PAUSE, RideCommand.AUTO_RESUME)

        assertEquals(RideStatus.RECORDING, transitions.last().status)
        assertEquals(listOf(RideEventType.AUTO_PAUSE, RideEventType.AUTO_RESUME), transitions.drop(1).map { it.event })
    }

    @Test
    fun `finish is possible from the idle prompt while auto paused`() {
        val last = run(RideCommand.START, RideCommand.AUTO_PAUSE, RideCommand.FINISH).last()

        assertEquals(RideTransition(RideStatus.FINISHED, RideEventType.STOP), last)
    }

    @Test
    fun `manual pause while auto paused adds no timer event`() {
        val last = run(RideCommand.START, RideCommand.AUTO_PAUSE, RideCommand.PAUSE).last()

        assertEquals(RideTransition(RideStatus.PAUSED, null), last)
    }

    @Test
    fun `manual pause is never auto resumed`() {
        val result = RideStateMachine.apply(RideStatus.PAUSED, RideCommand.AUTO_RESUME)

        assertIs<Outcome.Failure<InvalidRideCommand>>(result)
    }

    @Test
    fun `stop is not possible while recording`() {
        val result = RideStateMachine.apply(RideStatus.RECORDING, RideCommand.FINISH)

        assertEquals(Outcome.Failure(InvalidRideCommand(RideStatus.RECORDING, RideCommand.FINISH)), result)
    }

    @Test
    fun `discard from pause and from summary returns to idle`() {
        assertEquals(RideStatus.IDLE, run(RideCommand.START, RideCommand.PAUSE, RideCommand.DISCARD).last().status)
        assertEquals(
            RideStatus.IDLE,
            run(RideCommand.START, RideCommand.PAUSE, RideCommand.FINISH, RideCommand.DISCARD).last().status,
        )
    }

    @Test
    fun `start is only possible from idle`() {
        RideStatus.entries.filter { it != RideStatus.IDLE }.forEach {
            assertIs<Outcome.Failure<InvalidRideCommand>>(RideStateMachine.apply(it, RideCommand.START))
        }
    }

    @Test
    fun `moving clock runs only while recording`() {
        assertTrue(RideStateMachine.isMoving(RideStatus.RECORDING))
        assertFalse(RideStateMachine.isMoving(RideStatus.AUTO_PAUSED))
        assertFalse(RideStateMachine.isMoving(RideStatus.PAUSED))
    }

    @Test
    fun `active statuses need the foreground service`() {
        assertEquals(
            setOf(RideStatus.RECORDING, RideStatus.PAUSED, RideStatus.AUTO_PAUSED),
            RideStatus.entries.filter(RideStateMachine::isActive).toSet(),
        )
    }
}
