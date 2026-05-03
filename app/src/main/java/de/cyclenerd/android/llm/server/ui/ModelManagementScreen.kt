package de.cyclenerd.android.llm.server.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import de.cyclenerd.android.llm.server.ui.components.DownloadedModelsSection
import de.cyclenerd.android.llm.server.ui.components.RecommendedModelsSection

/**
 * Model Management screen.
 *
 * Displays:
 * - Downloaded models with delete option
 * - Recommended models with download option
 * - Download progress
 *
 * @param onNavigateBack Callback to navigate back to dashboard
 * @param viewModel ViewModel managing model state
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelManagementScreen(
    onNavigateBack: () -> Unit,
    viewModel: ModelManagementViewModel = viewModel(),
) {
    val downloadedModels by viewModel.downloadedModels.collectAsState()
    val recommendedModels by viewModel.recommendedModels.collectAsState()
    val downloadProgress by viewModel.downloadProgress.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val deviceRamGb by viewModel.deviceRamGb.collectAsState()

    // Show error dialog if there's an error
    errorMessage?.let { error ->
        AlertDialog(
            onDismissRequest = { viewModel.clearError() },
            title = { Text("Error") },
            text = { Text(error) },
            confirmButton = {
                TextButton(onClick = { viewModel.clearError() }) {
                    Text("OK")
                }
            },
        )
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Model Management") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to Dashboard",
                        )
                    }
                },
            )
        },
    ) { paddingValues ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
        ) {
            // Downloaded models section
            DownloadedModelsSection(
                models = downloadedModels,
                onDeleteModel = viewModel::deleteModel,
                onActivateModel = viewModel::activateModel,
                isLoading = isLoading,
                modifier = Modifier.padding(vertical = 8.dp),
            )

            // Recommended models section
            RecommendedModelsSection(
                models = recommendedModels,
                downloadedModelNames = downloadedModels.map { it.fileName }.toSet(),
                downloadProgress = downloadProgress,
                deviceRamGb = deviceRamGb,
                onDownloadModel = viewModel::downloadModel,
                modifier = Modifier.padding(vertical = 8.dp),
            )

            // Attribution note: all recommended models are hosted on Hugging Face.
            Text(
                text = "Models are downloaded from \uD83E\uDD17 Hugging Face",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
            )
        }
    }
}
