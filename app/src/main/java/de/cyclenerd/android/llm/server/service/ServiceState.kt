package de.cyclenerd.android.llm.server.service

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Sealed class representing the state of the LLM Server Service.
 *
 * This provides type-safe state management with exhaustive when() checks.
 * States flow: Stopped → Starting → Running ⇄ Stopping → Stopped
 *
 * Why sealed class?
 * - Compile-time exhaustive when() checks
 * - Type-safe state data (Running has IPs, Error has message)
 * - Better than enums for states with associated data
 *
 * Parcelable for Intent extras:
 * We send state updates via broadcasts, which require Parcelable/Serializable.
 */
@Parcelize
sealed class ServiceState : Parcelable {
    /**
     * Service is completely stopped.
     *
     * No resources allocated, no background work happening.
     * Safe to start the service from this state.
     */
    @Parcelize
    data object Stopped : ServiceState()

    /**
     * Service is initializing.
     *
     * Components are being started in order:
     * 1. Network monitoring
     * 2. LiteRT engine
     * 3. Ktor server
     *
     * This state typically lasts 10-15 seconds (engine loading time).
     */
    @Parcelize
    data object Starting : ServiceState()

    /**
     * Service is fully running and accepting requests.
     *
     * All components initialized successfully:
     * - Network monitor active
     * - LiteRT engine loaded
     * - Ktor server listening
     *
     * @property ipAddresses List of IPs where server is accessible
     * @property port HTTP port number
     * @property modelName Name of loaded model
     * @property uptime Seconds since service started
     */
    @Parcelize
    data class Running(
        val ipAddresses: List<String>,
        val port: Int,
        val modelName: String,
        val uptime: Long = 0,
    ) : ServiceState()

    /**
     * Service is shutting down gracefully.
     *
     * Shutdown sequence:
     * 1. Stop accepting new requests
     * 2. Wait for active requests (max 30s)
     * 3. Shutdown engine
     * 4. Release resources
     */
    @Parcelize
    data object Stopping : ServiceState()

    /**
     * Service encountered an error.
     *
     * Common errors:
     * - Model file not found
     * - Network not available
     * - Out of memory
     * - Port already in use
     *
     * @property message Human-readable error description
     */
    @Parcelize
    data class Error(
        val message: String,
    ) : ServiceState()
}

/**
 * Extension function to get display text for the notification.
 *
 * Provides user-friendly status messages for each state.
 *
 * @return Short status text for notification
 */
fun ServiceState.toDisplayText(): String =
    when (this) {
        is ServiceState.Stopped -> "Server stopped"
        is ServiceState.Starting -> "Starting server..."
        is ServiceState.Running -> "Server running on port $port"
        is ServiceState.Stopping -> "Stopping server..."
        is ServiceState.Error -> "Error: $message"
    }

/**
 * Extension function to check if the service is in a running state.
 *
 * @return true if service is Running
 */
fun ServiceState.isRunning(): Boolean = this is ServiceState.Running

/**
 * Extension function to check if the service can be stopped.
 *
 * @return true if service is in a state that can be stopped
 */
fun ServiceState.canStop(): Boolean = this is ServiceState.Running || this is ServiceState.Error
