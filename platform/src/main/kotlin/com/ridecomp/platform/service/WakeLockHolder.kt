package com.ridecomp.platform.service

import android.content.Context
import android.os.PowerManager
import com.ridecomp.core.config.RecordingConfig

/**
 * Partial wakelock owned by [RecordingService] while a ride exists (BAT-02): always acquired with a timeout
 * and renewed before it expires; [release] is called in `finally` and `onDestroy` (resource table 09 §1).
 */
internal class WakeLockHolder(context: Context) {
    private val wakeLock: PowerManager.WakeLock? = context.getSystemService(PowerManager::class.java)
        ?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG)
        ?.apply { setReferenceCounted(false) }
    private var acquiredAtMs: Long? = null

    /** Acquires, or renews when less than the renew margin is left. */
    fun acquireOrRenew(nowMs: Long) {
        val lock = wakeLock ?: return
        val acquiredAt = acquiredAtMs
        val isDue = acquiredAt == null ||
            !lock.isHeld ||
            nowMs - acquiredAt >= RecordingConfig.WAKELOCK_TIMEOUT_MS - RecordingConfig.WAKELOCK_RENEW_MARGIN_MS
        if (isDue) {
            lock.acquire(RecordingConfig.WAKELOCK_TIMEOUT_MS)
            acquiredAtMs = nowMs
        }
    }

    fun release() {
        wakeLock?.takeIf { it.isHeld }?.release()
        acquiredAtMs = null
    }

    private companion object {
        const val TAG = "RideComp:recording"
    }
}
