package com.ridecomp.platform.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.ridecomp.core.model.LocationFix
import com.ridecomp.core.source.Clock
import com.ridecomp.core.source.LocationSource
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * GPS fixes straight from [LocationManager.GPS_PROVIDER] (no Google Play Services needed,
 * 03-ARCHITECTURE §5). Updates run only while the flow is collected and are removed in `awaitClose`
 * (CO-04, BAT-01, resource table 09 §1). The callback only forwards data (CONC-02).
 *
 * Fix time is derived from the monotonic fix age rather than the GPS clock, which is wrong on some phones.
 */
class AndroidLocationSource(private val context: Context, private val clock: Clock) : LocationSource {
    private val locationManager: LocationManager? = context.getSystemService(LocationManager::class.java)

    override fun isPermissionGranted(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    override fun isGpsEnabled(): Boolean = locationManager?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true

    // Permission is checked with isPermissionGranted() at the start of the flow (STB-04); lint cannot see it.
    @SuppressLint("MissingPermission")
    override fun fixes(intervalMs: Long): Flow<LocationFix> = callbackFlow {
        val manager = requireNotNull(locationManager) { "No LocationManager on this device" }
        // STB-04: permission is checked every time; a revoked permission closes the flow with an error
        // that the service handles (retry + warning), never a crash.
        if (!isPermissionGranted()) throw SecurityException("Location permission not granted")
        val listener = LocationListener { location -> trySend(location.toFix()) }
        manager.requestLocationUpdates(
            LocationManager.GPS_PROVIDER,
            intervalMs,
            MIN_DISTANCE_M,
            listener,
            Looper.getMainLooper(),
        )
        awaitClose { manager.removeUpdates(listener) }
    }

    private fun Location.toFix(): LocationFix {
        val ageMs = (SystemClock.elapsedRealtimeNanos() - elapsedRealtimeNanos) / NANOS_PER_MS
        return LocationFix(
            timeMs = clock.nowMs() - ageMs.coerceAtLeast(0),
            latDeg = latitude,
            lonDeg = longitude,
            altitudeM = if (hasAltitude()) altitude else null,
            accuracyM = if (hasAccuracy()) accuracy.toDouble() else null,
            speedMps = if (hasSpeed()) speed.toDouble() else null,
        )
    }

    private companion object {
        /** Every fix is wanted; filtering happens in the domain (GpsFilter). */
        const val MIN_DISTANCE_M = 0f
        const val NANOS_PER_MS = 1_000_000L
    }
}
