package de.cyclenerd.android.llm.server.inference

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for PerformanceMetrics.
 *
 * These tests verify metrics calculations and data class behavior.
 */
class PerformanceMetricsTest {
    @Test
    fun `overallTokensPerSecond calculates correctly`() {
        val metrics =
            PerformanceMetrics(
                totalTokensGenerated = 100,
                totalTimeMs = 5000, // 5 seconds
            )

        // 100 tokens / 5 seconds = 20 tokens/second
        assertEquals(20f, metrics.overallTokensPerSecond, 0.01f)
    }

    @Test
    fun `overallTokensPerSecond handles zero time`() {
        val metrics =
            PerformanceMetrics(
                totalTokensGenerated = 100,
                totalTimeMs = 0,
            )

        assertEquals(0f, metrics.overallTokensPerSecond, 0.01f)
    }

    @Test
    fun `toString includes all metrics`() {
        val metrics =
            PerformanceMetrics(
                decodeTokensPerSecond = 15.5f,
                timeToFirstTokenMs = 500,
                totalTokensGenerated = 100,
                totalTimeMs = 5000,
                peakMemoryUsageMb = 4096,
            )

        val str = metrics.toString()
        assertTrue(str.contains("500"))
        assertTrue(str.contains("100"))
        assertTrue(str.contains("5000"))
        assertTrue(str.contains("4096"))
    }

    @Test
    fun `default metrics are zero`() {
        val metrics = PerformanceMetrics()

        assertEquals(0f, metrics.prefillTokensPerSecond, 0.01f)
        assertEquals(0f, metrics.decodeTokensPerSecond, 0.01f)
        assertEquals(0L, metrics.timeToFirstTokenMs)
        assertEquals(0, metrics.totalTokensGenerated)
        assertEquals(0L, metrics.totalTimeMs)
        assertEquals(0L, metrics.peakMemoryUsageMb)
    }
}
