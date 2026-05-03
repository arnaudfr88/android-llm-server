package de.cyclenerd.android.llm.server.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.cyclenerd.android.llm.server.data.RecommendedModels

/**
 * Section showing recommended models for download.
 *
 * @param models List of recommended models
 * @param downloadedModelNames Set of already downloaded model file names
 * @param downloadProgress Map of model file name to download progress (0.0-1.0)
 * @param deviceRamGb Device RAM in GB
 * @param onDownloadModel Callback when download button clicked
 * @param modifier Modifier for the card
 */
@Composable
fun RecommendedModelsSection(
    models: List<RecommendedModels.RecommendedModel>,
    downloadedModelNames: Set<String>,
    downloadProgress: Map<String, Float>,
    deviceRamGb: Int,
    onDownloadModel: (RecommendedModels.RecommendedModel) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
        ) {
            Text(
                text = "Recommended Models",
                style = MaterialTheme.typography.titleMedium,
            )

            Spacer(modifier = Modifier.size(8.dp))

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
                    contentDescription = "Info",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.size(8.dp))
                Text(
                    text = "Stay in the app until the download is complete.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }

            Spacer(modifier = Modifier.size(12.dp))

            if (models.isEmpty()) {
                Text(
                    text = "No recommended models available.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                models.forEach { model ->
                    RecommendedModelItem(
                        model = model,
                        isDownloaded = downloadedModelNames.contains(model.fileName),
                        downloadProgress = downloadProgress[model.fileName],
                        hasInsufficientRam = model.minRamGb > deviceRamGb,
                        onDownload = { onDownloadModel(model) },
                    )
                }
            }
        }
    }
}

@Composable
private fun RecommendedModelItem(
    model: RecommendedModels.RecommendedModel,
    isDownloaded: Boolean,
    downloadProgress: Float?,
    hasInsufficientRam: Boolean,
    onDownload: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = model.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = model.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.size(4.dp))

            // Download button or status on separate line
            if (isDownloaded) {
                Text(
                    text = "Downloaded",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            } else if (downloadProgress == null) {
                Button(onClick = onDownload) {
                    Text("Download")
                }
            }

            Spacer(modifier = Modifier.size(4.dp))

            // RAM requirement line with full width
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Size: ${model.sizeDescription} • Min RAM: ",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "${model.minRamGb} GB",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (hasInsufficientRam) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (hasInsufficientRam) FontWeight.Bold else FontWeight.Normal,
                )
                if (hasInsufficientRam) {
                    Text(
                        text = " (Device has less RAM)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }

        if (downloadProgress != null) {
            Spacer(modifier = Modifier.size(4.dp))
            LinearProgressIndicator(
                progress = { downloadProgress },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = "${(downloadProgress * 100).toInt()}%",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
