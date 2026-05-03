package de.cyclenerd.android.llm.server.inference

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for AccelerationType.
 *
 * These tests verify the acceleration type mapping and display names.
 */
class AccelerationTypeTest {
    @Test
    fun `CPU backend maps correctly`() {
        val backend = AccelerationType.CPU.toLiteRtBackend()
        assertEquals("CPU", AccelerationType.CPU.displayName())
    }

    @Test
    fun `GPU backend maps correctly`() {
        val backend = AccelerationType.GPU.toLiteRtBackend()
        assertEquals("GPU", AccelerationType.GPU.displayName())
    }

    @Test
    fun `NPU backend includes library path`() {
        val npu = AccelerationType.NPU("/path/to/libs")
        val backend = npu.toLiteRtBackend()
        assertEquals("NPU", npu.displayName())
    }

    @Test
    fun `display names are correct`() {
        assertEquals("CPU", AccelerationType.CPU.displayName())
        assertEquals("GPU", AccelerationType.GPU.displayName())
        assertEquals("NPU", AccelerationType.NPU("").displayName())
    }
}
