package com.ridecomp.platform.device

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.core.content.ContextCompat
import com.ridecomp.core.engine.recording.BatteryStatus
import com.ridecomp.core.source.BatteryLevelSource
import com.ridecomp.core.source.IdGenerator
import com.ridecomp.core.source.RideNameProvider
import com.ridecomp.core.usecase.TimeOfDay
import com.ridecomp.platform.R
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

/**
 * Reads the sticky ACTION_BATTERY_CHANGED broadcast on demand: no receiver stays registered
 * (12-FEATURE-SPECS §3, resource table 09 §1).
 */
class AndroidBatteryLevelSource(private val context: Context) : BatteryLevelSource {
    override fun read(): BatteryStatus? {
        val intent: Intent? = ContextCompat.registerReceiver(
            context,
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, UNKNOWN) ?: UNKNOWN
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, UNKNOWN) ?: UNKNOWN
        val plugged = intent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, NOT_PLUGGED) ?: NOT_PLUGGED
        return if (level >= 0 && scale > 0) {
            BatteryStatus(percent = level * PERCENT / scale, isCharging = plugged != NOT_PLUGGED)
        } else {
            null
        }
    }

    private companion object {
        const val UNKNOWN = -1
        const val NOT_PLUGGED = 0
        const val PERCENT = 100
    }
}

/** Random UUIDs, used as activity uuid / Strava external id. */
class UuidIdGenerator : IdGenerator {
    override fun newId(): String = UUID.randomUUID().toString()
}

/** Localized default ride names from the local time of day (02-DESIGN §3.6). */
class AndroidRideNameProvider(private val context: Context) : RideNameProvider {
    override fun defaultName(startedAtMs: Long): String {
        val hour = Instant.ofEpochMilli(startedAtMs).atZone(ZoneId.systemDefault()).hour
        val resId = when (TimeOfDay.ofHour(hour)) {
            TimeOfDay.MORNING -> R.string.ride_name_morning
            TimeOfDay.NOON -> R.string.ride_name_noon
            TimeOfDay.AFTERNOON -> R.string.ride_name_afternoon
            TimeOfDay.EVENING -> R.string.ride_name_evening
        }
        return context.getString(resId)
    }
}
