package de.cyclenerd.android.llm.server.network

import java.net.InetAddress
import java.net.NetworkInterface

/**
 * Network utility functions for detecting local IP addresses.
 *
 * This utility now accepts all network interfaces including loopback,
 * allowing the LLM server to run even without WiFi/Ethernet connections.
 * The server is still restricted by SecurityConfig to only bind to
 * localhost and private RFC1918 addresses.
 */
object NetworkUtils {
    /**
     * Retrieves all local IP addresses suitable for server binding.
     *
     * This function now returns:
     * 1. IPv4 loopback addresses (127.x.x.x)
     * 2. Private network ranges (RFC1918)
     * 3. IPv4 addresses from any active interface
     *
     * No more filtering by interface type - server works standalone
     * on localhost without WiFi/Ethernet.
     *
     * Example valid IPs returned:
     * - 127.0.0.1 (loopback - ALWAYS available)
     * - 192.168.211.100 (home WiFi)
     * - 10.0.0.50 (office network)
     * - 172.16.0.10 (private LAN)
     *
     * @return List of local IPv4 addresses safe for server binding.
     *         Always includes 127.0.0.1 as fallback.
     */
    fun getLocalIpAddresses(): List<String> {
        val localIps = mutableListOf<String>()

        try {
            // Get all network interfaces on the device
            val interfaces = NetworkInterface.getNetworkInterfaces()

            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()

                // Skip if interface is down
                if (!networkInterface.isUp) {
                    continue
                }

                // Examine all IP addresses on this interface
                val addresses = networkInterface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val address = addresses.nextElement()

                    // Only process IPv4 addresses
                    if (address is InetAddress) {
                        val ip = address.hostAddress ?: continue

                        // Include loopback (127.x.x.x) or private network IPs (RFC1918)
                        if (address.isLoopbackAddress || isPrivateIpAddress(ip)) {
                            if (!localIps.contains(ip)) {
                                localIps.add(ip)
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // Network interface enumeration can fail on some devices
            // Continue with what we found
        }

        // Ensure localhost is always available as fallback
        if (!localIps.contains("127.0.0.1")) {
            localIps.add("127.0.0.1")
        }

        return localIps
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
     * Now accepts localhost and private addresses.
     *
     * @param ip IPv4 address string
     * @return true if safe to bind, false otherwise
     */
    fun isValidBindAddress(ip: String): Boolean {
        if (ip.isEmpty()) {
            return false
        }
        // Accept loopback (127.x.x.x) or private addresses
        return ip.startsWith("127.") || isPrivateIpAddress(ip)
    }
}
