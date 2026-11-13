package com.ridecomp.platform.safety

import android.Manifest
import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SmsManager
import androidx.core.content.ContextCompat
import com.ridecomp.core.source.DispatcherProvider
import com.ridecomp.core.source.Logger
import com.ridecomp.core.source.SmsGateway
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The single class that sends SMS (SEC-SMS-02). It waits for the radio "sent" result of every part, so a
 * missing signal or credit is reported as a failure and retried by the incident flow. The result receiver is
 * not exported and the PendingIntent is immutable and explicit (SEC-PLT-04). Numbers are never logged
 * (SEC-SMS-05).
 */
class AndroidSmsGateway(
    private val context: Context,
    private val dispatchers: DispatcherProvider,
    private val logger: Logger,
) : SmsGateway {
    private val requestCodes = AtomicInteger()

    override fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED

    override suspend fun send(phone: String, text: String): Boolean {
        val isSent = withContext(dispatchers.io) { if (hasPermission()) sendParts(phone, text) else false }
        if (!isSent) logger.warn(TAG) { "SMS not sent" }
        return isSent
    }

    private suspend fun sendParts(phone: String, text: String): Boolean {
        val manager = smsManager() ?: return false
        val parts = manager.divideMessage(text)
        val action = SENT_ACTION_PREFIX + requestCodes.incrementAndGet()
        val result = CompletableDeferred<Boolean>()
        val pending = AtomicInteger(parts.size)
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context, intent: Intent) {
                when {
                    resultCode != Activity.RESULT_OK -> result.complete(false)
                    pending.decrementAndGet() == 0 -> result.complete(true)
                }
            }
        }
        ContextCompat.registerReceiver(context, receiver, IntentFilter(action), ContextCompat.RECEIVER_NOT_EXPORTED)
        return try {
            val sentIntents = ArrayList(parts.map { sentIntent(action) })
            manager.sendMultipartTextMessage(phone, null, parts, sentIntents, null)
            withTimeoutOrNull(SENT_TIMEOUT_MS) { result.await() } ?: false
        } catch (e: SecurityException) {
            logger.warn(TAG, e) { "SMS permission revoked" }
            false
        } catch (e: IllegalArgumentException) {
            logger.warn(TAG, e) { "SMS rejected by the system" }
            false
        } finally {
            context.unregisterReceiver(receiver)
        }
    }

    private fun sentIntent(action: String): PendingIntent = PendingIntent.getBroadcast(
        context,
        requestCodes.incrementAndGet(),
        Intent(action).setPackage(context.packageName),
        PendingIntent.FLAG_IMMUTABLE,
    )

    private fun smsManager(): SmsManager? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.getSystemService(SmsManager::class.java)
    } else {
        @Suppress("DEPRECATION") // The only API before Android 12.
        SmsManager.getDefault()
    }

    private companion object {
        const val TAG = "SmsGateway"
        const val SENT_ACTION_PREFIX = "com.ridecomp.platform.action.SMS_SENT."

        /** Without a result by then (no network) the send counts as failed and is retried later. */
        const val SENT_TIMEOUT_MS = 30_000L
    }
}
