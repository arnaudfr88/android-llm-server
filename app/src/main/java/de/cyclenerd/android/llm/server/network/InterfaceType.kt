package de.cyclenerd.android.llm.server.network

/**
 * Enum representing different types of network interfaces.
 *
 * This classification helps us determine which interfaces are safe
 * for server binding. We only want to bind to WIFI and ETHERNET
 * interfaces, never to MOBILE data.
 *
 * Why this matters:
 * Different interface types have different security implications:
 * - WIFI/ETHERNET: Local network, safe for server binding
 * - MOBILE: Public internet, dangerous for server binding
 * - LOOPBACK: Only accessible locally, not useful for network server
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
     * This is the most common interface type on Android devices
     * and is safe for server binding as it represents a local network.
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
     * Safe for server binding as it represents a local network.
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
     * NEVER bind server to this interface!
     * These interfaces have public or carrier-NAT IPs that could
     * expose the server to the internet.
     */
    MOBILE,

    /**
     * Loopback interface.
     *
     * Common interface names:
     * - lo
     *
     * This is the 127.0.0.1 interface used for local-only communication.
     * Not useful for a network server as it's only accessible from
     * the device itself.
     */
    LOOPBACK,

    /**
     * Unknown or unrecognized interface type.
     *
     * For safety, treat unknown interfaces as potentially dangerous
     * and don't bind to them. Better to be cautious.
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
 * 2. Match against known mobile data patterns (highest priority for security)
 * 3. Match against known WiFi patterns
 * 4. Match against known Ethernet patterns
 * 5. Default to UNKNOWN for safety
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
 * For these edge cases, we default to UNKNOWN which prevents binding,
 * erring on the side of security.
 *
 * @return The detected interface type
 */
fun java.net.NetworkInterface.detectType(): InterfaceType {
    val name = this.name.lowercase()

    // Check loopback first (reliable Java API)
    if (this.isLoopback) {
        return InterfaceType.LOOPBACK
    }

    // Check for mobile data patterns (CRITICAL for security)
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

    // Unknown interface - don't bind for safety
    return InterfaceType.UNKNOWN
}

/**
 * Checks if this interface type is safe for server binding.
 *
 * Only WIFI and ETHERNET are considered safe as they represent
 * local networks where we want the server to be accessible.
 *
 * @return true if safe to bind, false otherwise
 */
fun InterfaceType.isSafeForBinding(): Boolean = this == InterfaceType.WIFI || this == InterfaceType.ETHERNET
