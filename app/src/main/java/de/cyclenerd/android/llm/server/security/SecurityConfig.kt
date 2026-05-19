package de.cyclenerd.android.llm.server.security

import de.cyclenerd.android.llm.server.server.models.ChatCompletionRequest

/**
 * Security configuration and validation for the LLM server.
 *
 * This object defines security policies for the local network LLM server.
 *
 * Threat Model:
 * - **In Scope**: Network isolation (prevent public internet exposure)
 * - **Out of Scope**: Rate limiting, input validation, DoS protection
 *
 * Assumptions:
 * - Server runs on a trusted local network (home/office WiFi) or localhost
 * - All devices on the network are trusted
 * - Network traffic is not encrypted (local only)
 * - No authentication required (protected by network isolation)
 *
 * Design Philosophy:
 * - Minimal restrictions for local network use
 * - Trust the local network environment
 * - Let the model and hardware determine natural limits
 *
 * Limitations:
 * - This is NOT designed for public internet exposure
 * - No protection against resource exhaustion from local network
 */
object SecurityConfig {
    /**
     * Allowed IP address patterns for server binding.
     *
     * These patterns define which IP ranges the server is allowed to bind to.
     * Only private IP ranges (RFC1918), localhost (127.x.x.x, ::1), and link-local
     * addresses are permitted to prevent accidental exposure to public internet.
     *
     * Patterns (in priority order):
     * - 127.* - IPv4 loopback (localhost)
     * - ::1 - IPv6 loopback
     * - fe80:.* - IPv6 link-local
     * - 192.168.* - Class C private networks (home routers)
     * - 10.* - Class A private networks (corporate networks)
     * - 172.16.* through 172.31.* - Class B private networks
     */
    val ALLOWED_IP_PATTERNS =
        listOf(
            // Localhost / Loopback (highest priority)
            Regex("^127\\.\\d+\\.\\d+\\.\\d+$"),
            Regex("^::1$"),
            // IPv6 link-local
            Regex("^fe80:.*"),
            // RFC1918 Private networks
            Regex("^192\\.168\\.\\d+\\.\\d+$"),
            Regex("^10\\.\\d+\\.\\d+\\.\\d+$"),
            Regex("^172\\.(1[6-9]|2[0-9]|3[0-1])\\.\\d+\\.\\d+$"),
        )

    /**
     * Validates a chat completion request for security issues.
     *
     * This function performs minimal validation:
     * 1. Model name format (prevents path traversal attacks)
     * 2. Basic parameter sanity checks
     *
     * Note: No limits on message count, prompt length, or token count.
     * The model and device hardware will naturally limit what's possible.
     *
     * @param request The incoming chat completion request
     * @return true if request is valid, false if it should be rejected
     */
    fun isValidRequest(request: ChatCompletionRequest): Boolean {
        // Validate model name (prevent path traversal)
        if (request.model.contains("/") ||
            request.model.contains("\\") ||
            request.model.contains("..")
        ) {
            return false
        }

        // Check max_tokens is non-negative if specified
        if (request.maxTokens != null && request.maxTokens <= 0) {
            return false
        }

        return true
    }

    /**
     * Checks if an IP address is allowed for server binding.
     *
     * This prevents the server from binding to public IP addresses,
     * which would expose it to the internet. Localhost and RFC1918
     * private addresses are allowed.
     *
     * @param ipAddress The IP address to check
     * @return true if the IP is in an allowed range, false otherwise
     */
    fun isAllowedBindAddress(ipAddress: String): Boolean =
        ALLOWED_IP_PATTERNS.any { pattern ->
            pattern.matches(ipAddress)
        }
}
