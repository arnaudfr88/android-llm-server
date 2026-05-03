package de.cyclenerd.android.llm.server.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.cyclenerd.android.llm.server.data.ModelInfo

/**
 * Section showing downloaded models.
 *
 * @param models List of downloaded models
 * @param onDeleteModel Callback when delete button clicked
 * @param onActivateModel Callback when model is activated
 * @param isLoading Whether models are being loaded
 * @param modifier Modifier for the card
 */
@Composable
fun DownloadedModelsSection(
    models: List<ModelInfo>,
    onDeleteModel: (ModelInfo) -> Unit,
    onActivateModel: (ModelInfo) -> Unit,
    isLoading: Boolean,
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
                text = "Downloaded Models",
                style = MaterialTheme.typography.titleMedium,
            )

            Spacer(modifier = Modifier.size(12.dp))

            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
            } else if (models.isEmpty()) {
                Text(
                    text = "No models downloaded yet. Download a model from the recommended section below.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                models.forEach { model ->
                    ModelListItem(
                        model = model,
                        onDelete = { onDeleteModel(model) },
                        onActivate = { onActivateModel(model) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ModelListItem(
    model: ModelInfo,
    onDelete: () -> Unit,
    onActivate: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onActivate)
                .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Active indicator icon
        Icon(
            imageVector = if (model.isActive) Icons.Filled.CheckCircle else Icons.Outlined.Circle,
            contentDescription = if (model.isActive) "Active model" else "Inactive model",
            tint = if (model.isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp),
        )

        Spacer(modifier = Modifier.size(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = model.displayName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (model.isActive) FontWeight.Bold else FontWeight.Normal,
                color = if (model.isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "Size: ${model.getFormattedSize()}${if (model.isActive) " • Active" else ""}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        IconButton(onClick = onDelete) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = "Delete model",
                tint = MaterialTheme.colorScheme.error,
            )
        }
    }
}
