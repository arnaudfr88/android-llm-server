package de.cyclenerd.android.llm.server.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import de.cyclenerd.android.llm.server.service.ServiceState

/**
 * Server control section of the dashboard.
 *
 * Displays:
 * - Server status (Running, Stopped, Starting, Error)
 * - Current model name
 * - Port number
 * - Uptime
 * - Start/Stop button
 *
 * State-Driven UI:
 * The UI appearance is entirely determined by `serverState`.
 * When state changes, Compose automatically recomposes this
 * function to update the UI. No manual UI manipulation needed.
 *
 * Example state flow:
 * 1. User taps "Start" → ServiceState.Starting
 * 2. Button disabled, shows loading spinner
 * 3. Engine loads → ServiceState.Running
 * 4. Button enabled, shows "Stop Server"
 *
 * Why disable during transitions?
 * Starting a service takes 10+ seconds. If user taps "Start"
 * again while starting, we'd attempt to start twice, causing:
 * - Wasted CPU (two initialization attempts)
 * - Potential crashes (resource conflicts)
 * - Confusing state (which start succeeded?)
 *
 * Disabling the button prevents this race condition.
 *
 * Accessibility:
 * - Status announced to screen readers
 * - Button has semantic role
 * - Color not sole indicator (icons + text)
 * - High contrast for visibility
 *
 * @param serverState Current state of the server service
 * @param onToggleServer Callback when start/stop button clicked
 * @param modifier Modifier for the card container
 */
@Composable
fun ServerControlSection(
    serverState: ServiceState,
    onToggleServer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
        ) {
            // Header with status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Server Control",
                    style = MaterialTheme.typography.titleLarge,
                )

                StatusIndicator(serverState)
            }

            Spacer(modifier = Modifier.size(12.dp))

            // Performance tip info box
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .background(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = MaterialTheme.shapes.small,
                        ).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = "Performance Tip",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.size(8.dp))
                Text(
                    text = "For maximum performance, keep the app in the foreground and the screen unlocked.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }

            Spacer(modifier = Modifier.size(16.dp))

            // State-specific content
            when (serverState) {
                is ServiceState.Running -> {
                    RunningStateContent(serverState)
                }
                is ServiceState.Error -> {
                    // Error handled by popup dialog, show stopped state message
                    StoppedStateContent()
                }
                is ServiceState.Starting -> {
                    StartingStateContent()
                }
                is ServiceState.Stopping -> {
                    StoppingStateContent()
                }
                is ServiceState.Stopped -> {
                    StoppedStateContent()
                }
            }

            Spacer(modifier = Modifier.size(16.dp))

            // Control button
            Button(
                onClick = onToggleServer,
                modifier = Modifier.fillMaxWidth(),
                enabled = serverState !is ServiceState.Starting && serverState !is ServiceState.Stopping,
            ) {
                val buttonText =
                    when (serverState) {
                        is ServiceState.Running -> "Stop Server"
                        is ServiceState.Starting -> "Starting..."
                        is ServiceState.Stopping -> "Stopping..."
                        else -> "Start Server"
                    }
                Text(buttonText)
            }
        }
    }
}

/**
 * Status indicator icon.
 *
 * Shows different icons/colors for each state:
 * - Running: Green check
 * - Error: Red error icon
 * - Starting/Stopping: Loading spinner
 * - Stopped: Gray circle
 *
 * Why icons + color?
 * Color alone isn't accessible (color blind users).
 * Icons + text + color provides multiple cues.
 */
@Composable
private fun StatusIndicator(state: ServiceState) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        when (state) {
            is ServiceState.Running -> {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Running",
                    tint = Color(0xFF4CAF50), // Green
                    modifier = Modifier.size(24.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Running",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF4CAF50),
                )
            }
            is ServiceState.Error -> {
                Icon(
                    imageVector = Icons.Default.Error,
                    contentDescription = "Error",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Error",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            is ServiceState.Starting, is ServiceState.Stopping -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (state is ServiceState.Starting) "Starting" else "Stopping",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            is ServiceState.Stopped -> {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Stopped",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Stopped",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun RunningStateContent(state: ServiceState.Running) {
    Column {
        InfoRow(label = "Model", value = state.modelName)
        if (state.uptime > 0) {
            InfoRow(label = "Uptime", value = formatUptime(state.uptime))
        }
    }
}

@Composable
private fun StartingStateContent() {
    Text(
        text = "Initializing LiteRT engine and HTTP server...",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun StoppingStateContent() {
    Text(
        text = "Gracefully shutting down server...",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun StoppedStateContent() {
    Text(
        text = "Server is not running. Tap \"Start Server\" to begin.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun InfoRow(
    label: String,
    value: String,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

private fun formatUptime(seconds: Long): String =
    when {
        seconds < 60 -> "${seconds}s"
        seconds < 3600 -> "${seconds / 60}m"
        else -> "${seconds / 3600}h ${(seconds % 3600) / 60}m"
    }
