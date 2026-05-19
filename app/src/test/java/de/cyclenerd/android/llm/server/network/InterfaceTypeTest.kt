package de.cyclenerd.android.llm.server.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.net.NetworkInterface

/**
 * Unit tests for InterfaceType detection.
 *
 * These tests verify interface naming pattern matching.
 * We use mocked NetworkInterface objects since we can't create
 * real interfaces in unit tests.
 */
class InterfaceTypeTest {
    /**
     * Creates a mock NetworkInterface with the given name.
     */
    private fun mockInterface(
        name: String,
        isLoopback: Boolean = false,
    ): NetworkInterface {
        val iface = mock(NetworkInterface::class.java)
        `when`(iface.name).thenReturn(name)
        `when`(iface.isLoopback).thenReturn(isLoopback)
        return iface
    }

    @Test
    fun `detectType identifies WiFi interfaces`() {
        assertEquals(InterfaceType.WIFI, mockInterface("wlan0").detectType())
        assertEquals(InterfaceType.WIFI, mockInterface("wlan1").detectType())
        assertEquals(InterfaceType.WIFI, mockInterface("WLAN0").detectType()) // Case insensitive
        assertEquals(InterfaceType.WIFI, mockInterface("wifi0").detectType())
        assertEquals(InterfaceType.WIFI, mockInterface("wl0").detectType())
        assertEquals(InterfaceType.WIFI, mockInterface("wl1").detectType())
    }

    @Test
    fun `detectType identifies Ethernet interfaces`() {
        assertEquals(InterfaceType.ETHERNET, mockInterface("eth0").detectType())
        assertEquals(InterfaceType.ETHERNET, mockInterface("eth1").detectType())
        assertEquals(InterfaceType.ETHERNET, mockInterface("ETH0").detectType())
        assertEquals(InterfaceType.ETHERNET, mockInterface("lan0").detectType())
    }

    @Test
    fun `detectType identifies mobile data interfaces`() {
        // Qualcomm devices
        assertEquals(InterfaceType.MOBILE, mockInterface("rmnet0").detectType())
        assertEquals(InterfaceType.MOBILE, mockInterface("rmnet_data0").detectType())
        assertEquals(InterfaceType.MOBILE, mockInterface("v4-rmnet0").detectType())

        // MediaTek devices
        assertEquals(InterfaceType.MOBILE, mockInterface("ccmni0").detectType())
        assertEquals(InterfaceType.MOBILE, mockInterface("ccmni1").detectType())

        // Generic wireless WAN
        assertEquals(InterfaceType.MOBILE, mockInterface("wwan0").detectType())

        // Samsung devices
        assertEquals(InterfaceType.MOBILE, mockInterface("pdp_ip0").detectType())

        // Carrier NAT
        assertEquals(InterfaceType.MOBILE, mockInterface("clat4").detectType())
    }

    @Test
    fun `detectType identifies loopback interface`() {
        val loopback = mockInterface("lo", isLoopback = true)
        assertEquals(InterfaceType.LOOPBACK, loopback.detectType())
    }

    @Test
    fun `detectType returns UNKNOWN for unrecognized interfaces`() {
        assertEquals(InterfaceType.UNKNOWN, mockInterface("unknown0").detectType())
        assertEquals(InterfaceType.UNKNOWN, mockInterface("tun0").detectType())
        assertEquals(InterfaceType.UNKNOWN, mockInterface("dummy0").detectType())
        assertEquals(InterfaceType.UNKNOWN, mockInterface("p2p0").detectType())
    }

    @Test
    fun `isSafeForBinding returns true for all interface types`() {
        // All interface types are now safe for binding since the server
        // can run on any interface and security is handled by SecurityConfig
        // which restricts binding to localhost and private RFC1918 addresses
        assertTrue(InterfaceType.WIFI.isSafeForBinding())
        assertTrue(InterfaceType.ETHERNET.isSafeForBinding())
        assertTrue(InterfaceType.MOBILE.isSafeForBinding())
        assertTrue(InterfaceType.LOOPBACK.isSafeForBinding())
        assertTrue(InterfaceType.UNKNOWN.isSafeForBinding())
    }

    @Test
    fun `detectType prioritizes mobile detection for security`() {
        // Even if name might match other patterns, mobile should be detected first
        val mobileInterface = mockInterface("rmnet_wlan0") // Contains "wlan"
        assertEquals(
            InterfaceType.MOBILE,
            mobileInterface.detectType(),
        )
    }
}
