package de.cyclenerd.android.llm.server.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import de.cyclenerd.android.llm.server.ui.components.NetworkInfoSection
import de.cyclenerd.android.llm.server.ui.components.RequestLogsSection
import de.cyclenerd.android.llm.server.ui.components.ServerControlSection

/**
 * Main dashboard screen for the LLM server app.
 *
 * This screen displays:
 * - Server control (start/stop)
 * - Network information (IPs and URLs)
 * - Request logs with performance metrics (recent HTTP requests)
 *
 * Layout Structure:
 * Uses Scaffold for standardized Material Design layout:
 * - TopAppBar: Title and navigation
 * - Body: Scrollable column of sections
 *
 * Why Scaffold?
 * Scaffold is Material 3's layout container that handles:
 * - System bar insets (status bar, navigation bar)
 * - Floating action button positioning
 * - Snackbar hosting
 * - Consistent padding and spacing
 *
 * Without Scaffold, we'd manually handle all these edge cases.
 *
 * Why verticalScroll?
 * The dashboard has multiple sections that might not fit on small
 * screens or when logs are long. verticalScroll makes the entire
 * content scrollable like a webpage.
 *
 * Alternative: LazyColumn
 * - LazyColumn: Only renders visible items (virtual scrolling)
 * - verticalScroll: Renders everything (simple scrolling)
 *
 * We use verticalScroll because:
 * - Limited content (~5 sections, not 1000s of items)
 * - Sections have different layouts (not uniform list)
 * - Simpler than LazyColumn item management
 *
 * WindowInsets Handling:
 * Scaffold automatically applies system bar padding to avoid
 * overlapping with status bar (top), navigation bar (bottom),
 * or gesture navigation area.
 *
 * Edge-to-Edge:
 * The app draws behind system bars (edge-to-edge) but Scaffold
 * ensures content doesn't overlap. This gives:
 * - More immersive experience
 * - More screen space
 * - Modern Material 3 look
 *
 * @param onNavigateToModels Callback to navigate to model management
 * @param viewModel ViewModel managing dashboard state
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onNavigateToModels: () -> Unit,
    onNavigateToSettings: () -> Unit,
    viewModel: DashboardViewModel = viewModel(),
) {
    // Collect state from ViewModel
    // collectAsState() converts Flow to Compose State
    // UI automatically recomposes when these values change
    val serverState by viewModel.serverState.collectAsState()
    val ipAddresses by viewModel.ipAddresses.collectAsState()
    val logs by viewModel.logs.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    // Show error dialog if there's an error message
    errorMessage?.let { error ->
        AlertDialog(
            onDismissRequest = { /* Prevent dismiss by tapping outside */ },
            title = { Text("Server Error") },
            text = { Text(error) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearError()
                    onNavigateToModels()
                }) {
                    Text("Manage Models")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.clearError() }) {
                    Text("OK")
                }
            },
        )
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            DashboardAppBar(
                onNavigateToModels = onNavigateToModels,
                onNavigateToSettings = onNavigateToSettings,
            )
        },
    ) { paddingValues ->
        // paddingValues comes from Scaffold and includes system bar insets
        // We must apply it to avoid content overlapping with TopAppBar
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
        ) {
            // Server control section (start/stop, status)
            ServerControlSection(
                serverState = serverState,
                onToggleServer = viewModel::toggleServer,
                modifier = Modifier.padding(vertical = 8.dp),
            )

            // Network info section (IP addresses, URLs)
            if (ipAddresses.isNotEmpty()) {
                NetworkInfoSection(
                    ipAddresses = ipAddresses,
                    port = 8080, // TODO: Get from server config
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }

            // Request logs section (includes performance metrics per request)
            RequestLogsSection(
                logs = logs,
                onClearLogs = viewModel::clearLogs,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        }
    }
}

/**
 * Top app bar for the dashboard.
 *
 * Displays:
 * - App title
 * - Model management icon (settings/gear icon)
 * - Startup settings icon (power icon)
 *
 * Why TopAppBar?
 * Material 3 standard component that provides:
 * - Consistent height and padding
 * - Title typography
 * - Action button positioning
 * - Elevation/shadow
 *
 * @param onNavigateToModels Callback when models icon clicked
 * @param onNavigateToSettings Callback when startup settings icon clicked
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardAppBar(
    onNavigateToModels: () -> Unit,
    onNavigateToSettings: () -> Unit,
) {
    TopAppBar(
        title = { Text("Local LLM Server") },
        actions = {
            IconButton(onClick = onNavigateToModels) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Model Management",
                )
            }
            IconButton(onClick = onNavigateToSettings) {
                Icon(
                    imageVector = Icons.Default.PowerSettingsNew,
                    contentDescription = "Startup Settings",
                )
            }
        },
    )
}
