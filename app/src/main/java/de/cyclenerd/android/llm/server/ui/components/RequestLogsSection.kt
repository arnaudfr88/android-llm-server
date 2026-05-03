package de.cyclenerd.android.llm.server.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Request logs section showing recent HTTP requests.
 *
 * Displays the last 50 requests with newest at the top:
 * - Timestamp
 * - HTTP method (GET, POST)
 * - Request path
 * - Source IP address
 * - Status code (color-coded)
 *
 * Why show logs?
 * - Debugging: See what requests are being made and from where
 * - Monitoring: Verify server is responding
 * - Transparency: User knows server is working
 * - Security: Identify which devices are using the server
 *
 * Why newest first?
 * - Natural reading order: Latest events at top (like news feed)
 * - Most relevant information first
 * - User can scroll down in main UI to see older logs if needed
 *
 * Why no internal scrolling?
 * - Simpler UX: Only one scroll (the main screen)
 * - All logs visible in natural page flow
 * - No nested scroll confusion
 * - Better for accessibility
 *
 * Why limit to 50 logs?
 * - Memory: Each log entry uses RAM
 * - Performance: Long lists slow down rendering
 * - Usefulness: Users rarely need more than recent history
 *
 * Color-coded status codes:
 * - 2xx (Success): Green
 * - 4xx (Client error): Orange
 * - 5xx (Server error): Red
 * - Other: Default text color
 *
 * This helps quickly identify errors without reading text.
 *
 * @param logs List of log entries (newest first)
 * @param onClearLogs Callback to clear all logs
 * @param modifier Modifier for the card
 */
@Composable
fun RequestLogsSection(
    logs: List<LogEntry>,
    onClearLogs: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
        ) {
            // Header with title and clear button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Request Logs",
                    style = MaterialTheme.typography.titleMedium,
                )

                if (logs.isNotEmpty()) {
                    TextButton(onClick = onClearLogs) {
                        Text("Clear")
                    }
                }
            }

            Spacer(modifier = Modifier.size(8.dp))

            if (logs.isEmpty()) {
                Text(
                    text = "No requests yet. Logs will appear here when server receives requests.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                // Display all logs without internal scrolling
                // Main UI scroll handles all scrolling
                Column {
                    logs.forEach { log ->
                        LogEntryRow(log)
                    }
                }
            }
        }
    }
}

/**
 * Single log entry row.
 *
 * Displays:
 * - Timestamp (formatted time)
 * - Source IP address
 * - HTTP method (GET, POST, etc.)
 * - Status code (color-coded)
 * - Request path
 * - Performance metrics:
 *   - Total tokens (prompt + completion)
 *   - Tokens per second
 *   - Time to first token (in seconds)
 *   - Total time (in seconds, color-coded: >30s orange, >60s red)
 *
 * Layout:
 * Row 1: [Timestamp] [Source IP] [Method] [Status]
 * Row 2: [Path]
 * Row 3: [Performance metrics]
 * Divider
 *
 * Why two rows?
 * Paths can be long (e.g., "/v1/chat/completions?stream=true").
 * Giving path its own row prevents truncation and improves readability.
 *
 * Why show source IP?
 * Helps identify which device/client made the request.
 * Useful for multi-device debugging and security monitoring.
 *
 * Why color-code total time?
 * - Normal (<30s): Default color
 * - Warning (>30s): Orange - Slow response
 * - Error (>60s): Red - Very slow response
 * This helps quickly identify performance issues.
 *
 * @param log Log entry to display
 */
@Composable
private fun LogEntryRow(log: LogEntry) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            // Timestamp
            Text(
                text = log.timestamp.formatTime(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // Source IP
            Text(
                text = log.sourceIp,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium,
            )

            // HTTP method
            Text(
                text = log.method,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
            )

            // Status code (color-coded)
            Text(
                text = log.statusCode.toString(),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = log.statusCode.toStatusColor(),
            )
        }

        // Request path
        Text(
            text = log.path,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // Performance metrics (if available)
        log.metrics?.let { metrics ->
            Spacer(modifier = Modifier.size(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                // Total tokens (prompt + completion)
                Text(
                    text = "${metrics.totalTokens} tokens (${metrics.promptTokens}+${metrics.totalTokensGenerated})",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                )

                // Speed
                Text(
                    text = "${"%.1f".format(metrics.overallTokensPerSecond)} tok/s",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                )

                // Time to first token (in milliseconds)
                Text(
                    text = "TTFT: ${metrics.timeToFirstTokenMs.toInt()}ms",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                )

                // Total time (color-coded: >30s warning, >60s error)
                val totalTimeSec = metrics.totalTimeMs / 1000.0
                val timeColor =
                    when {
                        totalTimeSec > 60 -> Color(0xFFF44336) // Red for >60s
                        totalTimeSec > 30 -> Color(0xFFFF9800) // Orange for >30s
                        else -> MaterialTheme.colorScheme.secondary
                    }
                Text(
                    text = "${"%.1f".format(totalTimeSec)}s",
                    style = MaterialTheme.typography.bodySmall,
                    color = timeColor,
                    fontWeight = if (totalTimeSec > 30) FontWeight.Bold else FontWeight.Normal,
                )
            }
        }
    }

    HorizontalDivider()
}

/**
 * Log entry data class.
 *
 * Represents a single HTTP request log with performance metrics.
 *
 * @property timestamp When request was received (Unix time)
 * @property method HTTP method (GET, POST, etc.)
 * @property path Request path (e.g., "/v1/chat/completions")
 * @property sourceIp Source IP address of the client
 * @property statusCode HTTP response status
 * @property metrics Performance metrics (null for non-inference requests like /health)
 */
data class LogEntry(
    val timestamp: Long,
    val method: String,
    val path: String,
    val sourceIp: String,
    val statusCode: Int,
    val metrics: de.cyclenerd.android.llm.server.inference.PerformanceMetrics? = null,
)

/**
 * Formats Unix timestamp to readable time.
 *
 * Format: HH:mm:ss (e.g., "14:23:45")
 *
 * Why not show date?
 * Logs are recent (last few requests). Users care about
 * time, not date. Saves space in compact UI.
 *
 * @return Formatted time string
 */
private fun Long.formatTime(): String {
    val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    return sdf.format(Date(this))
}

/**
 * Maps HTTP status code to display color.
 *
 * - 2xx: Green (success)
 * - 4xx: Orange (client error)
 * - 5xx: Red (server error)
 * - Other: Default
 *
 * Why color code?
 * Quick visual scanning. Errors jump out immediately
 * without reading text.
 *
 * @return Color for status code
 */
private fun Int.toStatusColor(): Color =
    when (this) {
        in 200..299 -> Color(0xFF4CAF50) // Green
        in 400..499 -> Color(0xFFFF9800) // Orange
        in 500..599 -> Color(0xFFF44336) // Red
        else -> Color.Unspecified
    }
