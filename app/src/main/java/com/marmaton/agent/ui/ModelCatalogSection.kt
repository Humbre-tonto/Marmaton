package com.marmaton.agent.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.marmaton.agent.ModelDownloadService
import com.marmaton.agent.llm.ModelCatalog
import com.marmaton.agent.llm.SecurePreferences
import com.marmaton.agent.ui.theme.IonVioletTypography
import com.marmaton.agent.ui.theme.Radii
import com.marmaton.agent.ui.theme.Spacing
import java.util.Locale

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB")
    var value = bytes.toDouble()
    var unit = 0
    while (value >= 1024.0 && unit < units.size - 1) {
        value /= 1024.0
        unit++
    }
    return String.format(Locale.US, "%.1f %s", value, units[unit])
}

/**
 * In-app model catalog: download a curated MediaPipe `.task` model over HTTPS straight into the
 * app, with progress and cancel. Gated Gemma models need a Hugging Face token.
 */
@Composable
fun ModelCatalogSection() {
    val context = LocalContext.current
    val download by ModelDownloadService.state.collectAsState()

    var hfToken by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        hfToken = SecurePreferences.getHuggingFaceToken(context)
    }

    val isDownloading = download.phase == ModelDownloadService.Phase.DOWNLOADING ||
        download.phase == ModelDownloadService.Phase.VERIFYING

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(Radii.lg20)
    ) {
        Column(
            modifier = Modifier.padding(Spacing.space20),
            verticalArrangement = Arrangement.spacedBy(Spacing.space12)
        ) {
            Text(
                text = "Download a model",
                style = IonVioletTypography.title,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "Get an on-device model without leaving the app. These are MediaPipe .task " +
                    "models from the Google AI Edge / LiteRT Community. Downloads run in the " +
                    "background and resume if interrupted.",
                style = IonVioletTypography.bodySm,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Hugging Face token (needed for license-gated Gemma models)
            OutlinedTextField(
                value = hfToken,
                onValueChange = {
                    hfToken = it
                    SecurePreferences.saveHuggingFaceToken(context, it)
                },
                label = { Text("Hugging Face token (for gated models)") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = "Gemma models are license-gated: accept the license once on the model's " +
                    "Hugging Face page, create a read token, and paste it here. The token is stored " +
                    "encrypted on-device and sent only to huggingface.co.",
                style = IonVioletTypography.bodySm,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Active download progress
            if (isDownloading) {
                val pct = (download.fraction * 100).toInt()
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.space4)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(download.modelName, style = IonVioletTypography.label)
                        Text("$pct%", style = IonVioletTypography.label)
                    }
                    LinearProgressIndicator(
                        progress = { download.fraction },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = "${formatBytes(download.downloadedBytes)} / ${formatBytes(download.totalBytes)}",
                        style = IonVioletTypography.bodySm,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedButton(
                        onClick = { ModelDownloadService.cancel(context) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Cancel download")
                    }
                }
            }

            when (download.phase) {
                ModelDownloadService.Phase.COMPLETED ->
                    Text(
                        text = download.message ?: "Model downloaded and set as active.",
                        style = IonVioletTypography.bodySm,
                        color = Color(0xFF2E7D32)
                    )
                ModelDownloadService.Phase.FAILED ->
                    Text(
                        text = download.message ?: "Download failed.",
                        style = IonVioletTypography.bodySm,
                        color = MaterialTheme.colorScheme.error
                    )
                ModelDownloadService.Phase.CANCELLED ->
                    Text(
                        text = download.message ?: "Download cancelled.",
                        style = IonVioletTypography.bodySm,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                else -> {}
            }

            HorizontalDivider()

            // Catalog list
            ModelCatalog.models.forEach { model ->
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(Spacing.space4)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(model.name, style = IonVioletTypography.body)
                            Text(
                                text = "${model.sizeLabel} · .task",
                                style = IonVioletTypography.bodySm,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Button(
                            onClick = { ModelDownloadService.start(context, model.id) },
                            enabled = !isDownloading,
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(Radii.pill100)
                        ) {
                            Text("Download")
                        }
                    }
                    Text(
                        text = model.description,
                        style = IonVioletTypography.bodySm,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(Spacing.space4))
                }
            }
        }
    }
}
