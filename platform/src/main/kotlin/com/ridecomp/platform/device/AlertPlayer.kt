package com.ridecomp.platform.device

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

private const val START_NOW_MS = 0L
private const val BUZZ_SHORT_MS = 120L
private const val BUZZ_MEDIUM_MS = 200L
private const val BUZZ_GAP_MS = 150L
private const val BUZZ_LONG_MS = 400L

/** Haptic patterns used during a ride (12-FEATURE-SPECS §1–3). */
enum class AlertPattern(val timingsMs: LongArray) {
    /** Auto pause in/out: one short buzz. */
    SHORT(longArrayOf(START_NOW_MS, BUZZ_SHORT_MS)),

    /** Reminder: two buzzes. */
    DOUBLE(longArrayOf(START_NOW_MS, BUZZ_MEDIUM_MS, BUZZ_GAP_MS, BUZZ_MEDIUM_MS)),

    /** Battery mode change and other notices: one longer buzz. */
    LONG(longArrayOf(START_NOW_MS, BUZZ_LONG_MS)),

    /** Incident countdown: played every second, with the alarm tone (12-FEATURE-SPECS §5.3). */
    ALARM(longArrayOf(START_NOW_MS, BUZZ_LONG_MS, BUZZ_GAP_MS, BUZZ_LONG_MS)),
}

/**
 * Vibration for ride alerts. Each vibration is one-shot and self-terminating; [cancel] stops anything
 * running (resource table 09 §1: released on service destroy).
 */
class AlertPlayer(context: Context) {
    private var tones: ToneGenerator? = null

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        context.getSystemService(Vibrator::class.java)
    }

    fun play(pattern: AlertPattern) {
        val target = vibrator?.takeIf { it.hasVibrator() } ?: return
        target.vibrate(VibrationEffect.createWaveform(pattern.timingsMs, NO_REPEAT))
    }

    /**
     * Loud alarm tone on the alarm stream (heard even with media muted) plus [AlertPattern.ALARM]. The tone
     * generator is created on first use and released in [cancel].
     */
    fun alarm() {
        play(AlertPattern.ALARM)
        tone()?.startTone(ToneGenerator.TONE_CDMA_EMERGENCY_RINGBACK, ALARM_TONE_MS)
    }

    /** Short notice tone (off route), on the alarm stream like [alarm]. */
    fun beep() {
        tone()?.startTone(ToneGenerator.TONE_PROP_BEEP2, BEEP_MS)
    }

    private fun tone(): ToneGenerator? = tones
        ?: runCatching { ToneGenerator(AudioManager.STREAM_ALARM, ToneGenerator.MAX_VOLUME) }.getOrNull().also {
            tones =
                it
        }

    fun cancel() {
        vibrator?.cancel()
        tones?.release()
        tones = null
    }

    private companion object {
        const val NO_REPEAT = -1

        /** Slightly shorter than the 1 s tick so tones do not overlap. */
        const val ALARM_TONE_MS = 900
        const val BEEP_MS = 300
    }
}
