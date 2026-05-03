package de.cyclenerd.android.llm.server.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/**
 * Network information section showing server access URLs.
 *
 * Displays all local IP addresses where the server is accessible.
 * Each URL has a copy button for easy sharing.
 *
 * Why show all IPs?
 * A device might have multiple network interfaces:
 * - WiFi: 192.168.211.100
 * - Ethernet (USB-C adapter): 192.168.1.101
 * - VPN: 10.0.0.50
 *
 * Users on different networks need different IPs. Showing all
 * lets them choose the right one.
 *
 * Clipboard API:
 * Android provides ClipboardManager for copy/paste. We get it via:
 * `context.getSystemService(Context.CLIPBOARD_SERVICE)`
 *
 * Android 12+ Behavior:
 * On Android 12+, the system automatically shows a toast when
 * text is copied ("Copied to clipboard"). On older versions,
 * we show our own toast for consistency.
 *
 * Why clickable URLs?
 * Makes it easy to copy without tapping tiny button.
 * Larger hit target = better accessibility.
 *
 * @param ipAddresses List of local IP addresses
 * @param port HTTP port number
 * @param modifier Modifier for the card
 */
@Composable
fun NetworkInfoSection(
    ipAddresses: List<String>,
    port: Int,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
        ) {
            Text(
                text = "Access URLs",
                style = MaterialTheme.typography.titleMedium,
            )

            Spacer(modifier = Modifier.size(8.dp))

            if (ipAddresses.isEmpty()) {
                Text(
                    text = "No local network detected. Connect to WiFi or Ethernet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            } else {
                Text(
                    text = "Server is accessible from these URLs:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(modifier = Modifier.size(8.dp))

                ipAddresses.forEach { ip ->
                    val url = "http://$ip:$port"
                    UrlListItem(
                        url = url,
                        onCopy = { copyToClipboard(context, url) },
                    )
                }
            }
        }
    }
}

/**
 * Single URL list item with copy button.
 *
 * Design:
 * - URL text takes up most width
 * - Copy icon button on right
 * - Entire row clickable for copying
 *
 * Why entire row clickable?
 * Accessibility best practice:
 * - Larger hit target (easier to tap)
 * - Works better with TalkBack
 * - More forgiving for users with motor impairments
 *
 * @param url Complete HTTP URL
 * @param onCopy Callback when copy button/row clicked
 */
@Composable
private fun UrlListItem(
    url: String,
    onCopy: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onCopy)
                .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = url,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )

        IconButton(onClick = onCopy) {
            Icon(
                imageVector = Icons.Default.ContentCopy,
                contentDescription = "Copy URL",
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/**
 * Copies text to system clipboard.
 *
 * This uses Android's ClipboardManager which:
 * - Stores text in system clipboard
 * - Makes it available to all apps
 * - Persists across app switches
 *
 * ClipData structure:
 * - label: Description of clipboard content
 * - text: Actual content
 *
 * We label it "Server URL" so when user long-presses in
 * another app, they see "Server URL" in paste menu.
 *
 * Android 12+ automatically shows toast, but we show ours
 * on older versions for consistency.
 *
 * @param context Android context
 * @param text Text to copy
 */
private fun copyToClipboard(
    context: Context,
    text: String,
) {
    val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clipData = ClipData.newPlainText("Server URL", text)
    clipboardManager.setPrimaryClip(clipData)

    // Show confirmation toast
    // Android 12+ shows system toast automatically, but we show ours for older versions
    Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
}
