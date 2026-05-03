package de.cyclenerd.android.llm.server.utils

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for the Logger utility.
 *
 * These tests verify:
 * - Log entries are properly buffered
 * - Buffer size limit is enforced
 * - Log entries contain correct metadata
 * - Thread-safe operation
 */
class LoggerTest {
    @Before
    fun setup() {
        Logger.clearLogs()
    }

    @After
    fun teardown() {
        Logger.clearLogs()
    }

    @Test
    fun `logger buffers log entries`() {
        // Given: Log some messages
        Logger.i("TestTag", "Test message 1")
        Logger.d("TestTag", "Test message 2")
        Logger.w("TestTag", "Test message 3")

        // When: Retrieve logs
        val logs = Logger.getRecentLogs()

        // Then: All messages should be in buffer
        assertEquals(3, logs.size)
        assertEquals("Test message 3", logs[0].message)
        assertEquals("Test message 2", logs[1].message)
        assertEquals("Test message 1", logs[2].message)
    }

    @Test
    fun `logger enforces buffer size limit`() {
        // Given: Log more than MAX_BUFFER_SIZE messages
        repeat(60) { i ->
            Logger.i("TestTag", "Message $i")
        }

        // When: Retrieve logs
        val logs = Logger.getRecentLogs()

        // Then: Only last 50 should be retained
        assertEquals(50, logs.size)
        assertEquals("Message 59", logs[0].message)
        assertEquals("Message 10", logs[49].message)
    }

    @Test
    fun `logger includes timestamp and thread info`() {
        // Given: Log a message
        Logger.i("TestTag", "Test message")

        // When: Retrieve logs
        val logs = Logger.getRecentLogs()

        // Then: Entry should have metadata
        val entry = logs[0]
        assertTrue(entry.timestamp.isNotEmpty())
        assertTrue(entry.threadName.isNotEmpty())
        assertEquals(Logger.LogLevel.INFO, entry.level)
        assertEquals("TestTag", entry.tag)
    }

    @Test
    fun `clearLogs removes all entries`() {
        // Given: Log some messages
        Logger.i("TestTag", "Message 1")
        Logger.i("TestTag", "Message 2")

        // When: Clear logs
        Logger.clearLogs()

        // Then: Buffer should be empty
        assertEquals(0, Logger.getRecentLogs().size)
    }

    @Test
    fun `logger handles different log levels`() {
        // Given: Log with different levels
        Logger.d("TestTag", "Debug")
        Logger.i("TestTag", "Info")
        Logger.w("TestTag", "Warning")
        Logger.e("TestTag", "Error")

        // When: Retrieve logs
        val logs = Logger.getRecentLogs()

        // Then: All levels should be captured
        assertEquals(4, logs.size)
        assertEquals(Logger.LogLevel.ERROR, logs[0].level)
        assertEquals(Logger.LogLevel.WARN, logs[1].level)
        assertEquals(Logger.LogLevel.INFO, logs[2].level)
        assertEquals(Logger.LogLevel.DEBUG, logs[3].level)
    }
}
