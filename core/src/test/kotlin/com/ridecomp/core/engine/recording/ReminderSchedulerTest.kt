package com.ridecomp.core.engine.recording

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

private const val MIN = 60_000L

class ReminderSchedulerTest {
    private fun scheduler(drink: Int? = 20, eat: Int? = 45) =
        ReminderScheduler(mapOf(ReminderType.DRINK to drink, ReminderType.EAT to eat))

    @Test
    fun `drink reminder appears after 20 minutes of moving time`() {
        val scheduler = scheduler()

        assertNull(scheduler.onTick(nowMs = 0, movingMs = 19 * MIN).shown)
        assertEquals(ReminderType.DRINK, scheduler.onTick(nowMs = 1_000, movingMs = 20 * MIN).shown)
    }

    @Test
    fun `pauses do not count because only moving time is used`() {
        val scheduler = scheduler()

        // 2 hours of wall-clock time, but only 10 minutes moving.
        val tick = scheduler.onTick(nowMs = 120 * MIN, movingMs = 10 * MIN)

        assertNull(tick.active)
    }

    @Test
    fun `done resets the full interval`() {
        val scheduler = scheduler()
        scheduler.onTick(nowMs = 0, movingMs = 20 * MIN)

        assertEquals(ReminderType.DRINK, scheduler.acknowledge(movingMs = 20 * MIN))
        assertNull(scheduler.onTick(nowMs = 1_000, movingMs = 39 * MIN).active)
        assertEquals(ReminderType.DRINK, scheduler.onTick(nowMs = 2_000, movingMs = 40 * MIN).shown)
    }

    @Test
    fun `snooze re-reminds after 5 minutes`() {
        val scheduler = scheduler()
        scheduler.onTick(nowMs = 0, movingMs = 20 * MIN)

        scheduler.snooze(movingMs = 20 * MIN)

        assertNull(scheduler.onTick(nowMs = 1_000, movingMs = 24 * MIN).active)
        assertEquals(ReminderType.DRINK, scheduler.onTick(nowMs = 2_000, movingMs = 25 * MIN).shown)
    }

    @Test
    fun `no response hides after 10 s and repeats at most twice`() {
        val scheduler = scheduler(eat = null)
        var now = 0L
        var moving = 20 * MIN
        val shownAt = mutableListOf<Long>()

        repeat(6) {
            val tick = scheduler.onTick(now, moving)
            if (tick.shown != null) shownAt += moving / MIN
            // 10 s later the banner auto-hides; then 5 more minutes of riding.
            now += 10_000
            scheduler.onTick(now, moving)
            moving += 5 * MIN
            now += 5 * MIN
        }

        // First at 20, repeats at 25 and 30, then the normal interval: 30 + 20 = 50.
        assertEquals(listOf(20L, 25L, 30L), shownAt)
        assertEquals(ReminderType.DRINK, scheduler.onTick(now, 50 * MIN).shown)
    }

    @Test
    fun `only one banner at a time, eat follows drink`() {
        val scheduler = scheduler(drink = 45, eat = 45)

        assertEquals(ReminderType.DRINK, scheduler.onTick(0, 45 * MIN).shown)
        assertNull(scheduler.onTick(1_000, 45 * MIN).shown)
        scheduler.acknowledge(45 * MIN)
        assertEquals(ReminderType.EAT, scheduler.onTick(2_000, 45 * MIN).shown)
    }

    @Test
    fun `disabled reminders never fire`() {
        val scheduler = scheduler(drink = null, eat = null)

        assertNull(scheduler.onTick(0, 600 * MIN).active)
        assertNull(scheduler.acknowledge(600 * MIN))
        assertNull(scheduler.snooze(600 * MIN))
    }

    @Test
    fun `restore continues relative to the recovered moving time`() {
        val scheduler = scheduler()

        scheduler.restore(movingMs = 30 * MIN)

        assertNull(scheduler.onTick(0, 49 * MIN).active)
        assertEquals(ReminderType.DRINK, scheduler.onTick(1_000, 50 * MIN).shown)
    }

    @Test
    fun `each reminder type maps to its ride event`() {
        assertEquals(
            listOf("REMINDER_DRINK", "REMINDER_EAT"),
            ReminderType.entries.map { it.event.name },
        )
    }
}
