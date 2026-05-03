package de.cyclenerd.android.llm.server.utils

import android.util.Log
import de.cyclenerd.android.llm.server.BuildConfig
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Centralised logging utility for the Local LLM Server.
 *
 * Performance characteristics:
 *  - [d] (DEBUG) is gated behind [BuildConfig.PERF_LOGGING]; in release
 *    builds the call is a single bytecode boolean check that R8 then
 *    eliminates entirely (see proguard-rules.pro).
 *  - All other levels write to logcat unconditionally — those are rare
 *    and we want them in production crash reports.
 *  - Log buffer uses a lock-free [ConcurrentLinkedQueue] so the inference
 *    decode loop never blocks on logging.
 *
 * Always prefer the lambda overload `Logger.d("TAG") { "expensive message" }`
 * for hot paths — the lambda is **not** invoked when DEBUG is disabled,
 * which means callers don't pay the string concatenation cost.
 */
object Logger {
    private const val TAG_PREFIX = "LLMServer"
    private const val MAX_BUFFER_SIZE = 50

    /** True when verbose logging is allowed (debug builds only). */
    @JvmField
    val isDebug: Boolean = BuildConfig.PERF_LOGGING

    private val logBuffer = ConcurrentLinkedQueue<LogEntry>()
    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    data class LogEntry(
        val timestamp: String,
        val level: LogLevel,
        val tag: String,
        val message: String,
        val threadName: String,
    )

    enum class LogLevel { DEBUG, INFO, WARN, ERROR }

    /**
     * Log a debug message. Compiled out in release builds.
     */
    fun d(
        tag: String,
        message: String,
    ) {
        if (!isDebug) return
        log(LogLevel.DEBUG, tag, message)
        Log.d("$TAG_PREFIX:$tag", message)
    }

    /**
     * Log a debug message lazily — the [build] lambda is only invoked when
     * debug logging is enabled, eliminating string concatenation in
     * release builds.
     */
    inline fun d(
        tag: String,
        build: () -> String,
    ) {
        if (!isDebug) return
        d(tag, build())
    }

    fun i(
        tag: String,
        message: String,
    ) {
        log(LogLevel.INFO, tag, message)
        Log.i("$TAG_PREFIX:$tag", message)
    }

    fun w(
        tag: String,
        message: String,
    ) {
        log(LogLevel.WARN, tag, message)
        Log.w("$TAG_PREFIX:$tag", message)
    }

    fun e(
        tag: String,
        message: String,
        throwable: Throwable? = null,
    ) {
        log(LogLevel.ERROR, tag, message)
        if (throwable != null) {
            Log.e("$TAG_PREFIX:$tag", message, throwable)
        } else {
            Log.e("$TAG_PREFIX:$tag", message)
        }
    }

    private fun log(
        level: LogLevel,
        tag: String,
        message: String,
    ) {
        val timestamp = timeFormat.format(Date())
        val threadName = Thread.currentThread().name
        val entry = LogEntry(timestamp, level, tag, message, threadName)
        logBuffer.offer(entry)
        while (logBuffer.size > MAX_BUFFER_SIZE) logBuffer.poll()
    }

    fun getRecentLogs(): List<LogEntry> = logBuffer.toList().reversed()

    fun clearLogs() = logBuffer.clear()
}
