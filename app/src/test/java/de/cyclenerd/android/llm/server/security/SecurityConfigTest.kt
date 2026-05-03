package de.cyclenerd.android.llm.server.security

import de.cyclenerd.android.llm.server.server.models.ChatCompletionRequest
import de.cyclenerd.android.llm.server.server.models.ChatMessage
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SecurityConfigTest {
    @Test
    fun `isValidRequest accepts normal request`() {
        val request =
            ChatCompletionRequest(
                model = "gemma-4",
                messages =
                    listOf(
                        ChatMessage(role = "user", content = "Hello, world!"),
                    ),
                maxTokens = 100,
                stream = false,
            )

        assertTrue(SecurityConfig.isValidRequest(request))
    }

    @Test
    fun `isValidRequest accepts large number of messages`() {
        val messages =
            (1..500).map {
                ChatMessage(role = "user", content = "Message $it")
            }

        val request =
            ChatCompletionRequest(
                model = "gemma-4",
                messages = messages,
                maxTokens = 100,
                stream = false,
            )

        assertTrue(SecurityConfig.isValidRequest(request))
    }

    @Test
    fun `isValidRequest accepts long prompts`() {
        val longContent = "a".repeat(50000) // 50k characters

        val request =
            ChatCompletionRequest(
                model = "gemma-4",
                messages =
                    listOf(
                        ChatMessage(role = "user", content = longContent),
                    ),
                maxTokens = 100,
                stream = false,
            )

        assertTrue(SecurityConfig.isValidRequest(request))
    }

    @Test
    fun `isValidRequest accepts large max_tokens`() {
        val request =
            ChatCompletionRequest(
                model = "gemma-4",
                messages =
                    listOf(
                        ChatMessage(role = "user", content = "Hello"),
                    ),
                maxTokens = 10000,
                stream = false,
            )

        assertTrue(SecurityConfig.isValidRequest(request))
    }

    @Test
    fun `isValidRequest rejects negative max_tokens`() {
        val request =
            ChatCompletionRequest(
                model = "gemma-4",
                messages =
                    listOf(
                        ChatMessage(role = "user", content = "Hello"),
                    ),
                maxTokens = -1,
                stream = false,
            )

        assertFalse(SecurityConfig.isValidRequest(request))
    }

    @Test
    fun `isValidRequest rejects zero max_tokens`() {
        val request =
            ChatCompletionRequest(
                model = "gemma-4",
                messages =
                    listOf(
                        ChatMessage(role = "user", content = "Hello"),
                    ),
                maxTokens = 0,
                stream = false,
            )

        assertFalse(SecurityConfig.isValidRequest(request))
    }

    @Test
    fun `isValidRequest rejects model name with path traversal`() {
        val pathTraversalAttempts =
            listOf(
                "../model",
                "..\\model",
                "models/../dangerous",
                "models\\..\\dangerous",
            )

        pathTraversalAttempts.forEach { modelName ->
            val request =
                ChatCompletionRequest(
                    model = modelName,
                    messages =
                        listOf(
                            ChatMessage(role = "user", content = "Hello"),
                        ),
                    maxTokens = 100,
                    stream = false,
                )

            assertFalse(
                "Model name '$modelName' should be rejected",
                SecurityConfig.isValidRequest(request),
            )
        }
    }

    @Test
    fun `isAllowedBindAddress accepts private IP ranges`() {
        val validIPs =
            listOf(
                "192.168.1.1",
                "192.168.0.100",
                "10.0.0.1",
                "10.255.255.254",
                "172.16.0.1",
                "172.31.255.254",
                "127.0.0.1",
            )

        validIPs.forEach { ip ->
            assertTrue(
                "IP $ip should be allowed",
                SecurityConfig.isAllowedBindAddress(ip),
            )
        }
    }

    @Test
    fun `isAllowedBindAddress rejects public IP addresses`() {
        val publicIPs =
            listOf(
                "8.8.8.8", // Google DNS
                "1.1.1.1", // Cloudflare DNS
                "203.0.113.1", // Documentation range
                "0.0.0.0", // All interfaces
            )

        publicIPs.forEach { ip ->
            assertFalse(
                "IP $ip should be rejected",
                SecurityConfig.isAllowedBindAddress(ip),
            )
        }
    }

    @Test
    fun `isAllowedBindAddress rejects invalid 172 range`() {
        // 172.32.x.x is not in the private range (only 172.16-31.x.x)
        assertFalse(SecurityConfig.isAllowedBindAddress("172.32.0.1"))
        assertFalse(SecurityConfig.isAllowedBindAddress("172.15.0.1"))
    }
}
