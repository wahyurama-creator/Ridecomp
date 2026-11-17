package com.ridecomp.platform.map

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import com.ridecomp.core.engine.map.NetworkStatus
import com.ridecomp.core.source.NetworkStatusSource
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * The default network for the online map (D-90). The callback is registered only while the flow is
 * collected — a map on screen — and unregistered in `awaitClose` (resource table 09 §1). "Connected" means
 * the network reaches the internet (VALIDATED); "unmetered" is Wi‑Fi or another free network.
 */
class AndroidNetworkStatusSource(private val context: Context) : NetworkStatusSource {
    override fun observe(): Flow<NetworkStatus> = callbackFlow {
        val manager = context.getSystemService(ConnectivityManager::class.java)
        if (manager == null) {
            trySend(NetworkStatus.OFFLINE)
            awaitClose()
            return@callbackFlow
        }
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                trySend(capabilities.toStatus())
            }

            override fun onLost(network: Network) {
                trySend(NetworkStatus.OFFLINE)
            }
        }
        // Until the first callback: what the phone has now.
        trySend(manager.getNetworkCapabilities(manager.activeNetwork)?.toStatus() ?: NetworkStatus.OFFLINE)
        manager.registerDefaultNetworkCallback(callback)
        awaitClose { manager.unregisterNetworkCallback(callback) }
    }.conflate().distinctUntilChanged()

    private fun NetworkCapabilities.toStatus() = NetworkStatus(
        isConnected = hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),
        isUnmetered = hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED),
    )
}
