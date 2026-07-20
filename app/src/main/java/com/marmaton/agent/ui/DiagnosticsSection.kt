package com.marmaton.agent.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import com.marmaton.agent.llm.BackendConfig
import com.marmaton.agent.llm.SettingsPersistence
import com.marmaton.agent.ui.theme.IonVioletTypography
import com.marmaton.agent.ui.theme.Radii
import com.marmaton.agent.ui.theme.Spacing
import com.marmaton.agent.util.FileLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Diagnostics + model storage: share the persistent log file for debugging, and export the
 * downloaded model file to a location of the user's choice so it survives an uninstall (models
 * downloaded into app storage are otherwise deleted when the app is removed).
 */
@Composable
fun DiagnosticsSection() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val persistence = remember { SettingsPersistence(context) }
    val config by persistence.configFlow.collectAsState(initial = BackendConfig())

    val modelsPath = remember {
        (context.getExternalFilesDir("models") ?: File(context.filesDir, "models")).absolutePath
    }

    var exportStatus by remember { mutableStateOf<String?>(null) }

    // Copy the active model file to a user-chosen destination (survives uninstall, browsable).
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        val srcPath = config.localModelFilePath
        if (uri != null && srcPath.isNotBlank()) {
            coroutineScope.launch {
                exportStatus = "Exporting…"
                val ok = withContext(Dispatchers.IO) {
                    try {
                        File(srcPath).inputStream().use { input ->
                            context.contentResolver.openOutputStream(uri)?.use { output ->
                                input.copyTo(output, 1024 * 1024)
                            }
                        }
                        true
                    } catch (e: Exception) {
                        FileLogger.log("Export", "Model export failed: ${e.message}")
                        false
                    }
                }
                exportStatus = if (ok) "Model exported. It will survive an uninstall; re-import it later with \"Choose & Import Model File\"." else "Export failed."
            }
        }
    }

    fun shareLog() {
        val logFile = FileLogger.file()
        if (logFile == null || !logFile.exists()) {
            exportStatus = "No log file yet."
            return
        }
        try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                logFile
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Share log file"))
        } catch (e: Exception) {
            FileLogger.log("Diagnostics", "Share log failed: ${e.message}")
            exportStatus = "Couldn't share the log: ${e.message}"
        }
    }

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(Radii.lg20)
    ) {
        Column(
            modifier = Modifier.padding(Spacing.space20),
            verticalArrangement = Arrangement.spacedBy(Spacing.space12)
        ) {
            Text(
                text = "Diagnostics & storage",
                style = IonVioletTypography.title,
                color = MaterialTheme.colorScheme.primary
            )

            Text(
                text = "Downloaded models are stored at:\n$modelsPath\nThis folder is removed if you uninstall the app — export a model below to keep it.",
                style = IonVioletTypography.bodySm,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Button(
                onClick = { shareLog() },
                modifier = Modifier.fillMaxWidth(),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(Radii.pill100)
            ) {
                Text("Share log file")
            }

            OutlinedButton(
                onClick = {
                    val name = config.localModelFileName.ifBlank { "model.task" }
                    exportLauncher.launch(name)
                },
                enabled = config.localModelFilePath.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    if (config.localModelFilePath.isNotBlank())
                        "Export current model (keep across uninstall)"
                    else
                        "Export current model (no model downloaded yet)"
                )
            }

            OutlinedButton(
                onClick = {
                    FileLogger.clear()
                    exportStatus = "Log cleared."
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Clear log")
            }

            exportStatus?.let { status ->
                Text(
                    text = status,
                    style = IonVioletTypography.bodySm,
                    color = if (status.contains("fail", ignoreCase = true))
                        MaterialTheme.colorScheme.error
                    else
                        Color(0xFF2E7D32)
                )
            }
        }
    }
}
