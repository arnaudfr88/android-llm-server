package de.cyclenerd.android.llm.server.network

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for NetworkUtils.
 *
 * These tests verify IP address validation and filtering logic.
 * Network interface enumeration is tested in instrumented tests
 * since it requires an actual Android device.
 */
class NetworkUtilsTest {
    @Test
    fun `isPrivateIpAddress accepts Class C private range`() {
        // 192.168.0.0/16
        assertTrue(NetworkUtils.isPrivateIpAddress("192.168.0.1"))
        assertTrue(NetworkUtils.isPrivateIpAddress("192.168.1.1"))
        assertTrue(NetworkUtils.isPrivateIpAddress("192.168.255.254"))
        assertTrue(NetworkUtils.isPrivateIpAddress("192.168.100.50"))
    }

    @Test
    fun `isPrivateIpAddress accepts Class A private range`() {
        // 10.0.0.0/8
        assertTrue(NetworkUtils.isPrivateIpAddress("10.0.0.1"))
        assertTrue(NetworkUtils.isPrivateIpAddress("10.1.1.1"))
        assertTrue(NetworkUtils.isPrivateIpAddress("10.255.255.254"))
        assertTrue(NetworkUtils.isPrivateIpAddress("10.128.0.1"))
    }

    @Test
    fun `isPrivateIpAddress accepts Class B private range`() {
        // 172.16.0.0/12 (172.16.0.0 - 172.31.255.255)
        assertTrue(NetworkUtils.isPrivateIpAddress("172.16.0.1"))
        assertTrue(NetworkUtils.isPrivateIpAddress("172.16.1.1"))
        assertTrue(NetworkUtils.isPrivateIpAddress("172.31.255.254"))
        assertTrue(NetworkUtils.isPrivateIpAddress("172.20.0.1"))
        assertTrue(NetworkUtils.isPrivateIpAddress("172.24.100.50"))
    }

    @Test
    fun `isPrivateIpAddress rejects public IP addresses`() {
        assertFalse(NetworkUtils.isPrivateIpAddress("8.8.8.8")) // Google DNS
        assertFalse(NetworkUtils.isPrivateIpAddress("1.1.1.1")) // Cloudflare DNS
        assertFalse(NetworkUtils.isPrivateIpAddress("208.67.222.222")) // OpenDNS
        assertFalse(NetworkUtils.isPrivateIpAddress("93.184.216.34")) // Example.com
    }

    @Test
    fun `isPrivateIpAddress rejects loopback addresses`() {
        assertFalse(NetworkUtils.isPrivateIpAddress("127.0.0.1"))
        assertFalse(NetworkUtils.isPrivateIpAddress("127.0.0.2"))
        assertFalse(NetworkUtils.isPrivateIpAddress("127.255.255.254"))
    }

    @Test
    fun `isPrivateIpAddress rejects invalid Class B range`() {
        // 172.15.x.x is NOT in the private range (starts at 172.16)
        assertFalse(NetworkUtils.isPrivateIpAddress("172.15.0.1"))
        // 172.32.x.x is NOT in the private range (ends at 172.31)
        assertFalse(NetworkUtils.isPrivateIpAddress("172.32.0.1"))
    }

    @Test
    fun `isPrivateIpAddress rejects invalid IP formats`() {
        assertFalse(NetworkUtils.isPrivateIpAddress(""))
        assertFalse(NetworkUtils.isPrivateIpAddress("192.168.1"))
        assertFalse(NetworkUtils.isPrivateIpAddress("192.168.1.1.1"))
        assertFalse(NetworkUtils.isPrivateIpAddress("not.an.ip.address"))
        assertFalse(NetworkUtils.isPrivateIpAddress("192.168.1.999"))
        assertFalse(NetworkUtils.isPrivateIpAddress("192.168.-1.1"))
    }

    @Test
    fun `isPrivateIpAddress handles edge cases`() {
        assertFalse(NetworkUtils.isPrivateIpAddress("0.0.0.0"))
        assertFalse(NetworkUtils.isPrivateIpAddress("255.255.255.255"))
        assertFalse(NetworkUtils.isPrivateIpAddress("192.167.1.1")) // Not 192.168
        assertFalse(NetworkUtils.isPrivateIpAddress("192.169.1.1")) // Not 192.168
    }

    @Test
    fun `isValidBindAddress rejects loopback`() {
        assertFalse(NetworkUtils.isValidBindAddress("127.0.0.1"))
        assertFalse(NetworkUtils.isValidBindAddress("127.0.0.2"))
        assertFalse(NetworkUtils.isValidBindAddress("127.255.255.254"))
    }

    @Test
    fun `isValidBindAddress rejects empty string`() {
        assertFalse(NetworkUtils.isValidBindAddress(""))
    }

    @Test
    fun `isValidBindAddress accepts private IPs`() {
        assertTrue(NetworkUtils.isValidBindAddress("192.168.1.1"))
        assertTrue(NetworkUtils.isValidBindAddress("10.0.0.1"))
        assertTrue(NetworkUtils.isValidBindAddress("172.16.0.1"))
    }

    @Test
    fun `isValidBindAddress rejects public IPs`() {
        assertFalse(NetworkUtils.isValidBindAddress("8.8.8.8"))
        assertFalse(NetworkUtils.isValidBindAddress("1.1.1.1"))
    }

    @Test
    fun `getLocalIpAddresses returns list without crashing`() {
        // This test just verifies the function doesn't crash
        // Actual IP detection is tested in instrumented tests
        val ips = NetworkUtils.getLocalIpAddresses()

        // Should return a list (may be empty in test environment)
        // All returned IPs should be valid private addresses
        ips.forEach { ip ->
            assertTrue(
                "Returned IP $ip should be private",
                NetworkUtils.isPrivateIpAddress(ip),
            )
        }
    }
}
