package com.marmaton.agent.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.marmaton.agent.llm.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val persistence = remember { SettingsPersistence(context) }

    var config by remember { mutableStateOf(BackendConfig()) }
    var apiKey by remember { mutableStateOf("") }

    // Import states
    var isImporting by remember { mutableStateOf(false) }
    var importProgress by remember { mutableStateOf(0f) }
    var importError by remember { mutableStateOf<String?>(null) }
    var importSuccess by remember { mutableStateOf(false) }

    // Load initial settings
    LaunchedEffect(Unit) {
        config = persistence.configFlow.first()
        apiKey = SecurePreferences.getApiKey(context)
    }

    // Helper for SAF file sizing
    fun getFileSize(uri: Uri): Long {
        return try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                cursor.moveToFirst()
                cursor.getLong(sizeIndex)
            } ?: 0L
        } catch (e: Exception) {
            0L
        }
    }

    // Model import helper
    suspend fun importModelFile(uri: Uri) {
        isImporting = true
        importProgress = 0f
        importError = null
        importSuccess = false
        withContext(Dispatchers.IO) {
            try {
                val totalSize = getFileSize(uri)
                val destFile = File(context.filesDir, "imported_model.task")
                if (destFile.exists()) {
                    destFile.delete()
                }
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    destFile.outputStream().use { outputStream ->
                        val buffer = ByteArray(64 * 1024)
                        var bytesRead: Int
                        var totalBytesRead = 0L
                        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                            outputStream.write(buffer, 0, bytesRead)
                            totalBytesRead += bytesRead
                            if (totalSize > 0) {
                                importProgress = totalBytesRead.toFloat() / totalSize
                            }
                        }
                    }
                }
                persistence.updateLocalModel(destFile.absolutePath, uri.toString())
                // Reload config state
                config = persistence.configFlow.first()
                importSuccess = true
            } catch (e: OutOfMemoryError) {
                importError = "OOM Error: Model file is too large to import."
            } catch (e: Exception) {
                importError = "Import failed: ${e.message}"
            } finally {
                isImporting = false
            }
        }
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(uri, takeFlags)
            } catch (e: Exception) {
                Log.w("SettingsScreen", "Failed to take persistable URI permission", e)
            }
            coroutineScope.launch {
                importModelFile(uri)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("LLM Backend Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Section 1: Selected Backend Selector
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Active LLM Inference Backend",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    BackendType.values().forEach { backendType ->
                        val label = when (backendType) {
                            BackendType.LOCAL_FILE -> "On-Device Local Model (Default)"
                            BackendType.OLLAMA -> "Remote Ollama (LAN)"
                            BackendType.CLOUD -> "Cloud OpenAI-Compatible API"
                            BackendType.AICORE -> "Gemini Nano / AICore"
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            RadioButton(
                                selected = config.selectedType == backendType,
                                onClick = {
                                    coroutineScope.launch {
                                        persistence.updateSelectedType(backendType)
                                        config = persistence.configFlow.first()
                                    }
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = label, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }

            // Section 2: Configuration form based on choice
            when (config.selectedType) {
                BackendType.LOCAL_FILE -> {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Local Model File Configuration",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Supports .task / .litertlm models (e.g., Gemma, Qwen, DeepSeek). Large model files will be safely copied/imported to app storage for optimal performance.",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Spacer(modifier = Modifier.height(12.dp))

                            Text(
                                text = "Imported File Path: ${config.localModelFilePath.ifBlank { "Not Imported" }}",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Original URI: ${config.localModelUri.ifBlank { "Not Selected" }}",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Spacer(modifier = Modifier.height(12.dp))

                            if (isImporting) {
                                Text(
                                    text = "Importing: ${(importProgress * 100).toInt()}%",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                LinearProgressIndicator(
                                    progress = { importProgress },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            } else {
                                Button(
                                    onClick = {
                                        filePickerLauncher.launch(arrayOf("*/*"))
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Choose & Import Model File")
                                }
                            }

                            importError?.let { err ->
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(text = err, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                            }

                            if (importSuccess) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(text = "Model successfully imported!", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }

                BackendType.OLLAMA -> {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(
                                text = "Ollama LAN Configuration",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary
                            )

                            var schemeInput by remember { mutableStateOf(config.ollamaScheme) }
                            var hostInput by remember { mutableStateOf(config.ollamaHost) }
                            var portInput by remember { mutableStateOf(config.ollamaPort.toString()) }
                            var modelInput by remember { mutableStateOf(config.ollamaModel) }

                            // Update on changes
                            LaunchedEffect(schemeInput, hostInput, portInput, modelInput) {
                                val p = portInput.toIntOrNull() ?: 11434
                                persistence.updateOllamaConfig(schemeInput, hostInput, p, modelInput)
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = schemeInput,
                                    onValueChange = { schemeInput = it },
                                    label = { Text("Scheme") },
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = portInput,
                                    onValueChange = { portInput = it },
                                    label = { Text("Port") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            OutlinedTextField(
                                value = hostInput,
                                onValueChange = { hostInput = it },
                                label = { Text("Host IP / Name") },
                                modifier = Modifier.fillMaxWidth()
                            )

                            OutlinedTextField(
                                value = modelInput,
                                onValueChange = { modelInput = it },
                                label = { Text("Ollama Model Name") },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                BackendType.CLOUD -> {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(
                                text = "Cloud API Configuration",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary
                            )

                            var urlInput by remember { mutableStateOf(config.cloudBaseUrl) }
                            var modelInput by remember { mutableStateOf(config.cloudModel) }
                            var apiInput by remember { mutableStateOf(apiKey) }

                            // Update config
                            LaunchedEffect(urlInput, modelInput) {
                                persistence.updateCloudConfig(urlInput, modelInput)
                            }

                            OutlinedTextField(
                                value = urlInput,
                                onValueChange = { urlInput = it },
                                label = { Text("OpenAI-Compatible Base URL") },
                                modifier = Modifier.fillMaxWidth()
                            )

                            OutlinedTextField(
                                value = modelInput,
                                onValueChange = { modelInput = it },
                                label = { Text("Model Name") },
                                modifier = Modifier.fillMaxWidth()
                            )

                            OutlinedTextField(
                                value = apiInput,
                                onValueChange = {
                                    apiInput = it
                                    SecurePreferences.saveApiKey(context, it)
                                },
                                label = { Text("API Key") },
                                visualTransformation = PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                BackendType.AICORE -> {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "AICore (Gemini Nano) Options",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Wraps standard Gemini Nano on supported devices.",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }

            // Section 3: Privacy & Data Usage Consent
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Privacy & Data",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Marmaton is local-first and privacy-respecting. We do not collect your data, screen contents, or keys.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Share anonymous usage data",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = "Helps us know which inference backend/model you are using (never sends keys, prompts, or screen text).",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = config.analyticsConsent,
                            onCheckedChange = { checked ->
                                coroutineScope.launch {
                                    persistence.updateAnalyticsConsent(checked)
                                    // Withdrawing consent immediately stops tracking and calls reset()
                                    com.marmaton.agent.analytics.Analytics.get().setEnabled(checked)
                                    config = persistence.configFlow.first()
                                }
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    TextButton(
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://marmaton.ai/privacy"))
                            context.startActivity(intent)
                        },
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text("View our Privacy Policy")
                    }
                }
            }
        }
    }
}
