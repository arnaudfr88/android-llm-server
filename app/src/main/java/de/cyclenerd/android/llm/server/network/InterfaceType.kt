package de.cyclenerd.android.llm.server.network

/**
 * Enum representing different types of network interfaces.
 *
 * This classification helps us determine what networks are available.
 * The server now runs on all interface types, including localhost.
 *
 * Why this matters:
 * Different interface types show what connections are available:
 * - WIFI/ETHERNET: Local network interfaces
 * - MOBILE: Cellular data interface
 * - LOOPBACK: Localhost, always available
 * - UNKNOWN: Unrecognized interface
 *
 * Note: This is informational. Server no longer rejects any interface.
 */
enum class InterfaceType {
    /**
     * WiFi (WLAN) interface.
     *
     * Common interface names:
     * - wlan0, wlan1 (most Android devices)
     * - wifi0
     * - wl0
     *
     * Represents a wireless local network connection.
     */
    WIFI,

    /**
     * Ethernet (wired LAN) interface.
     *
     * Common interface names:
     * - eth0, eth1
     * - lan0
     *
     * Less common on mobile devices but may appear on:
     * - Android TV boxes
     * - Tablets with USB-C Ethernet adapters
     * - Development devices with Ethernet dongles
     *
     * Represents a wired local network connection.
     */
    ETHERNET,

    /**
     * Mobile data (cellular) interface.
     *
     * Common interface names:
     * - rmnet0, rmnet_data0 (Qualcomm)
     * - ccmni0 (MediaTek)
     * - wwan0 (generic wireless WAN)
     * - pdp_ip0 (Samsung)
     * - v4-rmnet0 (dual-stack)
     * - clat4 (carrier NAT)
     *
     * Represents a cellular data connection.
     * Note: Server still runs on this interface, but localhost is preferred.
     */
    MOBILE,

    /**
     * Loopback interface.
     *
     * Common interface names:
     * - lo
     *
     * This is the 127.0.0.1 interface used for local-only communication.
     * Always available and is the primary way to access the server
     * when no other networks are connected.
     */
    LOOPBACK,

    /**
     * Unknown or unrecognized interface type.
     *
     * For unrecognized interfaces, we default to treating them as available.
     */
    UNKNOWN,
}

/**
 * Extension function to detect the type of a NetworkInterface.
 *
 * This uses interface naming patterns to classify the interface.
 * While not 100% reliable across all Android devices and manufacturers,
 * it works for the vast majority of devices.
 *
 * Detection strategy:
 * 1. Check if interface is loopback (via Java API)
 * 2. Match against known mobile data patterns
 * 3. Match against known WiFi patterns
 * 4. Match against known Ethernet patterns
 * 5. Default to UNKNOWN for unrecognized interfaces
 *
 * Why interface names?
 * Android doesn't provide a reliable API to query interface type directly.
 * Interface names follow conventions (e.g., wlan for WiFi, rmnet for mobile)
 * that are consistent across most devices and Android versions.
 *
 * Limitations:
 * - Custom ROMs might use non-standard naming
 * - Future Android versions might change conventions
 * - Some exotic devices might use different names
 *
 * For these edge cases, we default to UNKNOWN.
 *
 * @return The detected interface type
 */
fun java.net.NetworkInterface.detectType(): InterfaceType {
    val name = this.name.lowercase()

    // Check loopback first (reliable Java API)
    if (this.isLoopback) {
        return InterfaceType.LOOPBACK
    }

    // Check for mobile data patterns
    when {
        name.startsWith("rmnet") -> return InterfaceType.MOBILE
        name.startsWith("ccmni") -> return InterfaceType.MOBILE
        name.startsWith("wwan") -> return InterfaceType.MOBILE
        name.startsWith("pdp_ip") -> return InterfaceType.MOBILE
        name.startsWith("v4-rmnet") -> return InterfaceType.MOBILE
        name.startsWith("clat") -> return InterfaceType.MOBILE
    }

    // Check for WiFi patterns
    when {
        name.startsWith("wlan") -> return InterfaceType.WIFI
        name.startsWith("wifi") -> return InterfaceType.WIFI
        name.startsWith("wl") && name.length == 3 -> return InterfaceType.WIFI // wl0, wl1
    }

    // Check for Ethernet patterns
    when {
        name.startsWith("eth") -> return InterfaceType.ETHERNET
        name.startsWith("lan") -> return InterfaceType.ETHERNET
    }

    // Unknown interface
    return InterfaceType.UNKNOWN
}

/**
 * Checks if this interface type is safe for server binding.
 *
 * Now returns true for all types - server runs on localhost
 * even without WiFi/Ethernet connections.
 *
 * @return always true (server is always safe now)
 */
fun InterfaceType.isSafeForBinding(): Boolean = true
