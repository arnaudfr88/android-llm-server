package de.cyclenerd.android.llm.server.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for ServiceState.
 *
 * These tests verify state transitions and helper functions.
 */
class ServiceStateTest {
    @Test
    fun `toDisplayText returns correct text for each state`() {
        assertEquals("Server stopped", ServiceState.Stopped.toDisplayText())
        assertEquals("Starting server...", ServiceState.Starting.toDisplayText())
        assertEquals(
            "Server running on port 8080",
            ServiceState
                .Running(
                    ipAddresses = listOf("192.168.211.100"),
                    port = 8080,
                    modelName = "test",
                ).toDisplayText(),
        )
        assertEquals("Stopping server...", ServiceState.Stopping.toDisplayText())
        assertEquals("Error: test error", ServiceState.Error("test error").toDisplayText())
    }

    @Test
    fun `isRunning returns true only for Running state`() {
        assertTrue(
            ServiceState
                .Running(
                    ipAddresses = emptyList(),
                    port = 8080,
                    modelName = "test",
                ).isRunning(),
        )

        assertFalse(ServiceState.Stopped.isRunning())
        assertFalse(ServiceState.Starting.isRunning())
        assertFalse(ServiceState.Stopping.isRunning())
        assertFalse(ServiceState.Error("test").isRunning())
    }

    @Test
    fun `canStop returns true for Running and Error states`() {
        assertTrue(
            ServiceState
                .Running(
                    ipAddresses = emptyList(),
                    port = 8080,
                    modelName = "test",
                ).canStop(),
        )
        assertTrue(ServiceState.Error("test").canStop())

        assertFalse(ServiceState.Stopped.canStop())
        assertFalse(ServiceState.Starting.canStop())
        assertFalse(ServiceState.Stopping.canStop())
    }

    @Test
    fun `Running state includes all required data`() {
        val state =
            ServiceState.Running(
                ipAddresses = listOf("192.168.211.100", "192.168.1.101"),
                port = 8080,
                modelName = "gemma-4-2b-it",
                uptime = 3661,
            )

        assertEquals(2, state.ipAddresses.size)
        assertEquals(8080, state.port)
        assertEquals("gemma-4-2b-it", state.modelName)
        assertEquals(3661L, state.uptime)
    }

    @Test
    fun `Error state includes message`() {
        val error = ServiceState.Error("Network not available")
        assertEquals("Network not available", error.message)
    }
}
