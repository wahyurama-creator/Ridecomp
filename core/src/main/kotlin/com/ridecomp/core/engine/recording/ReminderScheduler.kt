package com.ridecomp.core.engine.recording

import com.ridecomp.core.config.ReminderConfig
import com.ridecomp.core.config.Units
import com.ridecomp.core.model.RideEventType

public enum class ReminderType(public val event: RideEventType) {
    DRINK(RideEventType.REMINDER_DRINK),
    EAT(RideEventType.REMINDER_EAT),
}

/** The banner currently shown (12-FEATURE-SPECS §2). */
public data class ActiveReminder(val type: ReminderType, val shownAtMs: Long)

/** Result of one tick: the banner to show (or null) and the reminder that just appeared, to log/vibrate. */
public data class ReminderTick(val active: ActiveReminder?, val shown: ReminderType?)

/**
 * Drink/eat reminders based on **moving time** (they do not run during pauses), computed inside the
 * recording pipeline — no AlarmManager (BAT-05).
 *
 * - "Done" resets the interval; "Snooze" re-reminds after 5 min of moving time.
 * - No response: hidden after 10 s, re-reminded after 5 min, at most twice; then the normal interval
 *   continues.
 */
public class ReminderScheduler(intervalsMin: Map<ReminderType, Int?>) {
    private val intervalsMs: Map<ReminderType, Long> = intervalsMin
        .mapNotNull { (type, minutes) -> minutes?.takeIf { it > 0 }?.let { type to it * Units.MS_PER_MINUTE } }
        .toMap()
    private val nextDueMovingMs: MutableMap<ReminderType, Long> = intervalsMs.toMutableMap()
    private val repeats: MutableMap<ReminderType, Int> = mutableMapOf()
    private var active: ActiveReminder? = null

    /** Restores schedule after recovery: reminders continue relative to the restored moving time. */
    public fun restore(movingMs: Long) {
        intervalsMs.forEach { (type, interval) -> nextDueMovingMs[type] = movingMs + interval }
    }

    /** Called once per second with wall-clock [nowMs] and the ride's moving time. */
    public fun onTick(nowMs: Long, movingMs: Long): ReminderTick {
        val current = active
        if (current != null && nowMs - current.shownAtMs >= ReminderConfig.AUTO_DISMISS_MS) {
            onNoResponse(current.type, movingMs)
        }
        val due = if (active == null) firstDue(movingMs) else null
        if (due != null) active = ActiveReminder(due, nowMs)
        return ReminderTick(active, due)
    }

    /** "Done": the rider drank/ate; the next reminder comes one full interval later. */
    public fun acknowledge(movingMs: Long): ReminderType? = active?.type?.also { type ->
        schedule(type, movingMs + intervalOf(type))
        repeats[type] = 0
        active = null
    }

    /** "Snooze 5 min". */
    public fun snooze(movingMs: Long): ReminderType? = active?.type?.also { type ->
        schedule(type, movingMs + SNOOZE_MS)
        active = null
    }

    private fun onNoResponse(type: ReminderType, movingMs: Long) {
        val count = (repeats[type] ?: 0) + 1
        if (count > ReminderConfig.MAX_REPEATS) {
            repeats[type] = 0
            schedule(type, movingMs + intervalOf(type))
        } else {
            repeats[type] = count
            schedule(type, movingMs + SNOOZE_MS)
        }
        active = null
    }

    private fun firstDue(movingMs: Long): ReminderType? =
        ReminderType.entries.firstOrNull { type -> nextDueMovingMs[type]?.let { movingMs >= it } == true }

    private fun schedule(type: ReminderType, dueMovingMs: Long) {
        nextDueMovingMs[type] = dueMovingMs
    }

    private fun intervalOf(type: ReminderType): Long = intervalsMs[type] ?: SNOOZE_MS

    private companion object {
        const val SNOOZE_MS: Long = ReminderConfig.SNOOZE_MIN * Units.MS_PER_MINUTE
    }
}

/** The recorder side of [ReminderScheduler]: the reminder shown on the ride screen and the events to log. */
public class RideReminders(intervalsMin: Map<ReminderType, Int?>) {
    private val scheduler = ReminderScheduler(intervalsMin)

    public var active: ActiveReminder? = null
        private set

    public fun restore(movingMs: Long) {
        scheduler.restore(movingMs)
    }

    /** The event of a reminder shown this second, if any. */
    public fun onTick(nowMs: Long, movingMs: Long): RideEventType? {
        val tick = scheduler.onTick(nowMs, movingMs)
        active = tick.active
        return tick.shown?.event
    }

    /** True when a shown reminder was acknowledged (logged as REMINDER_ACK). */
    public fun acknowledge(movingMs: Long): Boolean {
        val type = scheduler.acknowledge(movingMs)
        active = null
        return type != null
    }

    public fun snooze(movingMs: Long) {
        scheduler.snooze(movingMs)
        active = null
    }
}
