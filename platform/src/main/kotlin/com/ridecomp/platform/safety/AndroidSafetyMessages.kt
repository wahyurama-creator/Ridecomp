package com.ridecomp.platform.safety

import android.content.Context
import com.ridecomp.core.source.SafetyMessages
import com.ridecomp.platform.R
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/**
 * SMS texts from resources (12-FEATURE-SPECS §5.3). Content is minimal: name, time, map link, accuracy
 * (SEC-SMS-04). The map link uses 6 decimals (about 0.1 m) and a dot decimal separator.
 */
class AndroidSafetyMessages(private val context: Context) : SafetyMessages {
    override fun test(riderName: String): String = context.getString(R.string.sms_test, name(riderName))

    override fun incident(riderName: String, atMs: Long, latDeg: Double?, lonDeg: Double?, accuracyM: Double?): String {
        val time = TIME_FORMAT.format(Instant.ofEpochMilli(atMs).atZone(ZoneId.systemDefault()))
        val location = if (latDeg != null && lonDeg != null) {
            val link = String.format(Locale.ROOT, MAP_LINK_FORMAT, latDeg, lonDeg)
            accuracyM?.let { context.getString(R.string.sms_location_accuracy, link, it.roundToInt()) }
                ?: context.getString(R.string.sms_location, link)
        } else {
            context.getString(R.string.sms_location_unknown)
        }
        return context.getString(R.string.sms_incident, name(riderName), time, location)
    }

    override fun safe(riderName: String): String = context.getString(R.string.sms_safe, name(riderName))

    private fun name(riderName: String): String = riderName.ifBlank { context.getString(R.string.sms_rider_unknown) }

    private companion object {
        val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
        const val MAP_LINK_FORMAT = "https://maps.google.com/?q=%.6f,%.6f"
    }
}
