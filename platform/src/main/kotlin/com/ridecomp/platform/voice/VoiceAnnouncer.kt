package com.ridecomp.platform.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.ridecomp.core.source.Logger
import java.util.Locale

/**
 * Speaks ride announcements with the phone's own text-to-speech (T7.1, D-91): no library, no internet.
 * Music is ducked while speaking (transient audio focus) and comes back after. Owned by the recording
 * service: [start] when a ride with announcements starts, [shutdown] when the pipeline stops (09 §1).
 */
class VoiceAnnouncer(private val context: Context, private val logger: Logger) {
    private val audio = context.getSystemService(AudioManager::class.java)
    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()
    private val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        .setAudioAttributes(attributes)
        .build()
    private var tts: TextToSpeech? = null
    private var isReady = false
    private val pending = ArrayDeque<String>()
    private var nextId = 0

    @Synchronized
    fun start(locale: Locale) {
        if (tts != null) return
        tts = TextToSpeech(context.applicationContext) { status -> onInit(status, locale) }
    }

    @Synchronized
    private fun onInit(status: Int, locale: Locale) {
        val engine = tts ?: return
        if (status != TextToSpeech.SUCCESS) {
            logger.warn(TAG) { "Text-to-speech unavailable ($status)" }
            shutdown()
            return
        }
        val result = engine.setLanguage(locale)
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            logger.warn(TAG) { "No voice for the app language; the phone's default voice is used" }
        }
        engine.setAudioAttributes(attributes)
        engine.setOnUtteranceProgressListener(FocusReleaser())
        isReady = true
        while (pending.isNotEmpty()) say(engine, pending.removeFirst())
    }

    /** Queued until the engine is ready (at most [MAX_PENDING], older ones dropped). */
    @Synchronized
    fun speak(text: String) {
        val engine = tts ?: return
        if (isReady) {
            say(engine, text)
        } else {
            if (pending.size == MAX_PENDING) pending.removeFirst()
            pending.addLast(text)
        }
    }

    private fun say(engine: TextToSpeech, text: String) {
        audio?.requestAudioFocus(focus)
        engine.speak(text, TextToSpeech.QUEUE_ADD, null, UTTERANCE_PREFIX + nextId++)
    }

    @Synchronized
    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isReady = false
        pending.clear()
        audio?.abandonAudioFocusRequest(focus)
    }

    /** Gives the audio back to music once nothing more is being said. */
    private inner class FocusReleaser : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) = Unit

        override fun onDone(utteranceId: String?) {
            release()
        }

        @Deprecated("Deprecated in Java")
        override fun onError(utteranceId: String?) {
            release()
        }

        private fun release() {
            if (tts?.isSpeaking != true) audio?.abandonAudioFocusRequest(focus)
        }
    }

    private companion object {
        const val TAG = "VoiceAnnouncer"
        const val UTTERANCE_PREFIX = "ride-"
        const val MAX_PENDING = 3
    }
}
