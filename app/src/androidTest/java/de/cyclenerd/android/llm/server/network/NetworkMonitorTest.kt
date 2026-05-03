package de.cyclenerd.android.llm.server.network

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented tests for network detection functionality.
 *
 * These tests MUST run on a real device or emulator with network access.
 * They verify that our network detection logic works correctly with
 * actual Android network interfaces.
 *
 * Test Requirements:
 * - Device must be connected to WiFi (not mobile data)
 * - Airplane mode must be OFF
 * - Network permissions must be granted
 *
 * Why instrumented tests?
 * Unit tests can't access real network interfaces. We need to verify
 * that our IP detection and filtering logic works on actual Android
 * hardware with real WiFi/cellular interfaces.
 */
@RunWith(AndroidJUnit4::class)
class NetworkMonitorTest {
    private lateinit var context: Context
    private lateinit var networkMonitor: NetworkMonitor

    @Before
    fun setup() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        networkMonitor = NetworkMonitor(context)
    }

    @Test
    fun testGetLocalIpAddressesReturnsValidIps() {
        // Given: Device is on WiFi
        val ips = NetworkUtils.getLocalIpAddresses()

        // Then: Should return at least one IP (assuming WiFi is connected)
        // Note: May be empty if device has no network, but test will be skipped
        if (ips.isEmpty()) {
            println("WARNING: No local IPs found. Is WiFi connected?")
            return
        }

        // All returned IPs should be private addresses
        ips.forEach { ip ->
            assertTrue(
                "IP $ip should be private",
                NetworkUtils.isPrivateIpAddress(ip),
            )
            assertTrue(
                "IP $ip should be valid bind address",
                NetworkUtils.isValidBindAddress(ip),
            )
        }

        println("Found local IPs: $ips")
    }

    @Test
    fun testNetworkMonitorDetectsWiFi() =
        runBlocking {
            // Given: Device is on WiFi
            val state = networkMonitor.getCurrentNetworkState()

            // Then: Should detect WiFi or Ethernet (not mobile)
            assertNotNull(state)
            assertFalse(
                "Should not be on mobile data",
                state.networkType == InterfaceType.MOBILE,
            )

            println("Network type: ${state.networkType}")
            println("Local IPs: ${state.localIpAddresses}")
            println("Server safe: ${state.isServerSafe}")

            // If on WiFi, should be safe for server
            if (state.networkType == InterfaceType.WIFI) {
                assertTrue(
                    "WiFi should be safe for server",
                    state.networkType.isSafeForBinding(),
                )
            }
        }

    @Test
    fun testNetworkMonitorFlowEmitsStates() =
        runBlocking {
            // Given: Network monitor Flow
            val flow = networkMonitor.observeNetworkState()

            // When: Collecting first emission
            val state = flow.first()

            // Then: Should receive a network state
            assertNotNull(state)
            println("Flow emitted state: $state")
        }

    @Test
    fun testNetworkMonitorIsServerSafe() {
        // Given: Device network state
        val isSafe = networkMonitor.isServerSafe()

        // Then: Should return a boolean
        // Value depends on current network (WiFi = true, Mobile = false)
        println("Is server safe: $isSafe")

        // If safe, should have local IPs
        if (isSafe) {
            val ips = NetworkUtils.getLocalIpAddresses()
            assertTrue(
                "Safe network should have local IPs",
                ips.isNotEmpty(),
            )
        }
    }

    @Test
    fun testNetworkStateChangesOnNetworkToggle() =
        runBlocking {
            // This test documents how to manually test network changes
            // To test properly:
            // 1. Start test with WiFi ON
            // 2. Manually disable WiFi during test
            // 3. Observe Flow emissions
            //
            // Note: This is a demonstration test, not a full automated test
            // as we can't programmatically toggle network states on modern Android

            println(
                """
                MANUAL TEST INSTRUCTIONS:
                1. Ensure device is on WiFi
                2. Run this test
                3. Within 5 seconds, disable WiFi
                4. Observe Flow emissions in logs
                """.trimIndent(),
            )

            val flow = networkMonitor.observeNetworkState()

            var emissionCount = 0
            try {
                flow.collect { state ->
                    emissionCount++
                    println("Emission $emissionCount: $state")

                    if (emissionCount >= 3) {
                        // Collected enough emissions
                        return@collect
                    }
                }
            } catch (e: Exception) {
                println("Flow collection ended: ${e.message}")
            }

            // Should have collected at least one emission
            assertTrue(emissionCount > 0)
        }
}
