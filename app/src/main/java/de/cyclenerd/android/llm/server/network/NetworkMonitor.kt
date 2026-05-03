package de.cyclenerd.android.llm.server.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Monitors network changes and provides real-time updates via Kotlin Flow.
 *
 * This class tracks network connectivity changes and emits the current list
 * of safe local IP addresses whenever the network state changes. It's designed
 * to work seamlessly with coroutines and Compose UI updates.
 *
 * Why Flow instead of LiveData?
 * - Flow is part of Kotlin Coroutines, not Android-specific
 * - Better integration with Ktor server (which uses coroutines)
 * - More flexible operators for transformation and filtering
 * - Works in non-Android Kotlin contexts (future-proof)
 *
 * Use case:
 * The Ktor server needs to know when network changes occur so it can:
 * 1. Rebind to new IP addresses when WiFi networks change
 * 2. Stop the server if WiFi disconnects
 * 3. Warn users if device switches to mobile data only
 *
 * @param context Application context (not Activity to avoid memory leaks)
 */
class NetworkMonitor(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val connectivityManager =
        appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    /**
     * Flow that emits network state updates.
     *
     * Emission triggers:
     * - When a network becomes available
     * - When a network is lost
     * - When network capabilities change (e.g., WiFi to mobile data)
     *
     * The emitted NetworkState includes:
     * - Current list of safe local IPs
     * - Whether the device is on a safe network (WiFi/Ethernet)
     * - Current network type
     *
     * Why callbackFlow?
     * This is the idiomatic way to convert callback-based APIs (like
     * ConnectivityManager.NetworkCallback) into Kotlin Flows. The callback
     * flow stays active until explicitly closed, making it perfect for
     * long-running network monitoring.
     *
     * Memory management:
     * The Flow automatically unregisters the network callback when all
     * collectors cancel (e.g., when the UI is destroyed). This prevents
     * memory leaks and unnecessary battery drain.
     *
     * @return Flow of NetworkState updates
     */
    fun observeNetworkState(): Flow<NetworkState> =
        callbackFlow {
            val callback =
                object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        // Network connected - send updated state
                        trySend(getCurrentNetworkState())
                    }

                    override fun onLost(network: Network) {
                        // Network disconnected - send updated state
                        trySend(getCurrentNetworkState())
                    }

                    override fun onCapabilitiesChanged(
                        network: Network,
                        networkCapabilities: NetworkCapabilities,
                    ) {
                        // Network type changed (e.g., WiFi <-> Mobile)
                        trySend(getCurrentNetworkState())
                    }
                }

            // Request to monitor all networks
            val request =
                NetworkRequest
                    .Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .build()

            // Register callback
            connectivityManager.registerNetworkCallback(request, callback)

            // Send initial state immediately
            trySend(getCurrentNetworkState())

            // Keep flow alive until cancelled
            // When cancelled, unregister callback to prevent memory leaks
            awaitClose {
                connectivityManager.unregisterNetworkCallback(callback)
            }
        }.distinctUntilChanged() // Only emit when state actually changes

    /**
     * Gets the current network state snapshot.
     *
     * This examines all active networks and determines:
     * 1. What local IPs are available
     * 2. What type of network we're on (WiFi, Ethernet, Mobile)
     * 3. Whether it's safe to run the server
     *
     * @return Current network state
     */
    fun getCurrentNetworkState(): NetworkState {
        val localIps = NetworkUtils.getLocalIpAddresses()
        val networkType = detectCurrentNetworkType()

        return NetworkState(
            localIpAddresses = localIps,
            networkType = networkType,
            isServerSafe = networkType.isSafeForBinding() && localIps.isNotEmpty(),
        )
    }

    /**
     * Detects the current active network type.
     *
     * Checks all active networks and returns the "best" one:
     * - WiFi is preferred
     * - Ethernet is second choice
     * - Mobile is least preferred (and unsafe for server)
     *
     * @return The detected network type, or UNKNOWN if no network
     */
    private fun detectCurrentNetworkType(): InterfaceType {
        val activeNetwork = connectivityManager.activeNetwork ?: return InterfaceType.UNKNOWN
        val capabilities =
            connectivityManager.getNetworkCapabilities(activeNetwork)
                ?: return InterfaceType.UNKNOWN

        return when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ->
                InterfaceType.WIFI
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) ->
                InterfaceType.ETHERNET
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ->
                InterfaceType.MOBILE
            else -> InterfaceType.UNKNOWN
        }
    }

    /**
     * Synchronous check if server can safely start right now.
     *
     * This is useful for one-time checks before starting the server,
     * while observeNetworkState() is used for continuous monitoring.
     *
     * @return true if safe to start server, false otherwise
     */
    fun isServerSafe(): Boolean = getCurrentNetworkState().isServerSafe
}

/**
 * Represents the current state of network connectivity.
 *
 * @property localIpAddresses List of local IPs safe for server binding
 * @property networkType Current network connection type
 * @property isServerSafe Whether it's safe to run the server on this network
 */
data class NetworkState(
    val localIpAddresses: List<String>,
    val networkType: InterfaceType,
    val isServerSafe: Boolean,
)
