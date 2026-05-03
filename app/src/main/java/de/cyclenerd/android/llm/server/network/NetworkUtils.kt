package de.cyclenerd.android.llm.server.network

import java.net.InetAddress
import java.net.NetworkInterface

/**
 * Network utility functions for detecting local IP addresses.
 *
 * This utility ensures the LLM server only binds to local network interfaces
 * (WiFi/Ethernet) and never exposes itself on mobile data connections which
 * could be accessible from the public internet.
 *
 * Why IP filtering matters:
 * Without proper filtering, the server could bind to a mobile data interface
 * with a public IP address, potentially exposing the LLM API to the internet.
 * This would be a serious security risk as there's no authentication.
 */
object NetworkUtils {
    /**
     * Mobile data interface name patterns to exclude.
     *
     * These interface names indicate cellular/mobile data connections
     * which we must never bind to for security reasons.
     *
     * Common patterns across Android devices:
     * - rmnet*: Qualcomm-based devices (most Android phones)
     * - ccmni*: MediaTek-based devices
     * - wwan*: Wireless WAN (mobile broadband)
     * - pdp_ip*: Samsung devices
     * - v4-rmnet*: Dual-stack mobile data
     * - clat4: Carrier-grade NAT transition
     */
    private val MOBILE_INTERFACE_PATTERNS =
        setOf(
            "rmnet",
            "ccmni",
            "wwan",
            "pdp_ip",
            "v4-rmnet",
            "clat",
        )

    /**
     * Retrieves all local IP addresses suitable for server binding.
     *
     * This function filters network interfaces to return only IPs that are:
     * 1. IPv4 addresses (we don't support IPv6 binding yet)
     * 2. Private network ranges (RFC1918)
     * 3. Not on mobile data interfaces
     * 4. Not loopback addresses
     *
     * Security design:
     * By only returning private IP addresses from WiFi/Ethernet interfaces,
     * we ensure the server is ONLY accessible within the local network.
     * This prevents accidental exposure to the internet via mobile data.
     *
     * Why we exclude mobile data:
     * Mobile data interfaces typically have public IP addresses (or carrier NAT).
     * Binding to these would make the server accessible from the internet,
     * which is dangerous since we have no authentication mechanism.
     *
     * Example valid IPs returned:
     * - 192.168.211.100 (home WiFi)
     * - 10.0.0.50 (office network)
     * - 172.16.0.10 (private LAN)
     *
     * Example IPs NOT returned:
     * - 127.0.0.1 (loopback)
     * - 8.8.8.8 (public internet)
     * - IPs from rmnet0, ccmni0 (mobile data)
     *
     * @return List of local IPv4 addresses safe for server binding.
     *         Empty list if no suitable interfaces are found.
     */
    fun getLocalIpAddresses(): List<String> {
        val localIps = mutableListOf<String>()

        try {
            // Get all network interfaces on the device
            val interfaces = NetworkInterface.getNetworkInterfaces()

            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()

                // Skip if interface is down or loopback
                if (!networkInterface.isUp || networkInterface.isLoopback) {
                    continue
                }

                // Check if this is a mobile data interface
                if (isMobileDataInterface(networkInterface)) {
                    continue
                }

                // Examine all IP addresses on this interface
                val addresses = networkInterface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val address = addresses.nextElement()

                    // Only process IPv4 addresses
                    if (address is InetAddress && !address.isLoopbackAddress) {
                        val ip = address.hostAddress ?: continue

                        // Only include private network IPs (RFC1918)
                        if (isPrivateIpAddress(ip)) {
                            localIps.add(ip)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // Network interface enumeration can fail on some devices
            // Return empty list rather than crashing
            return emptyList()
        }

        return localIps
    }

    /**
     * Checks if a network interface is a mobile data connection.
     *
     * This uses interface naming patterns to detect cellular connections.
     * Different Android device manufacturers use different naming schemes,
     * so we check against all known patterns.
     *
     * @param networkInterface The interface to check
     * @return true if this is a mobile data interface, false otherwise
     */
    private fun isMobileDataInterface(networkInterface: NetworkInterface): Boolean {
        val name = networkInterface.name.lowercase()
        return MOBILE_INTERFACE_PATTERNS.any { pattern ->
            name.startsWith(pattern)
        }
    }

    /**
     * Validates if an IP address is in a private network range (RFC1918).
     *
     * Private IP ranges:
     * - 10.0.0.0/8 (10.0.0.0 - 10.255.255.255)
     * - 172.16.0.0/12 (172.16.0.0 - 172.31.255.255)
     * - 192.168.0.0/16 (192.168.0.0 - 192.168.255.255)
     *
     * These ranges are reserved for private networks and cannot be routed
     * on the public internet, making them safe for local-only services.
     *
     * @param ip IPv4 address string (e.g., "192.168.1.1")
     * @return true if the IP is in a private range, false otherwise
     */
    fun isPrivateIpAddress(ip: String): Boolean {
        val parts = ip.split(".")
        if (parts.size != 4) return false

        return try {
            val octets = parts.map { it.toInt() }

            // Validate all octets are in valid range (0-255)
            if (octets.any { it < 0 || it > 255 }) {
                return false
            }

            val first = octets[0]
            val second = octets[1]

            when (first) {
                10 -> true // 10.0.0.0/8
                172 -> second in 16..31 // 172.16.0.0/12
                192 -> second == 168 // 192.168.0.0/16
                else -> false
            }
        } catch (e: NumberFormatException) {
            false
        }
    }

    /**
     * Checks if an IP address is suitable for server binding.
     *
     * This combines multiple validations:
     * - Must be a valid IPv4 address
     * - Must be in a private network range
     * - Must not be a loopback address
     *
     * @param ip IPv4 address string
     * @return true if safe to bind, false otherwise
     */
    fun isValidBindAddress(ip: String): Boolean {
        if (ip.isEmpty() || ip == "127.0.0.1" || ip.startsWith("127.")) {
            return false
        }
        return isPrivateIpAddress(ip)
    }
}
