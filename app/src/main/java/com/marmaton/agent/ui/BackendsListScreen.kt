package com.marmaton.agent.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.marmaton.agent.R
import com.marmaton.agent.llm.*
import com.marmaton.agent.ui.theme.IonVioletTypography
import com.marmaton.agent.ui.theme.Radii
import com.marmaton.agent.ui.theme.Spacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackendsListScreen(
    onNavigateToLocalDetail: () -> Unit,
    onNavigateToOllamaDetail: () -> Unit,
    onNavigateToCloudDetail: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val persistence = remember { SettingsPersistence(context) }
    val config by persistence.configFlow.collectAsState(initial = BackendConfig())

    var localStatusText by remember { mutableStateOf("ready") }
    var ollamaStatusText by remember { mutableStateOf("unreachable") }
    var cloudStatusText by remember { mutableStateOf("not configured") }

    LaunchedEffect(config) {
        val hasLocalFile = config.localModelFilePath.isNotBlank() && File(config.localModelFilePath).exists()
        localStatusText = if (hasLocalFile) "ready" else "not ready"

        val ollamaBackend = BackendFactory.createBackend(context, config.copy(selectedType = BackendType.OLLAMA))
        try {
            ollamaStatusText = when (ollamaBackend.status()) {
                is BackendStatus.Ready -> "ready"
                else -> "unreachable"
            }
        } finally {
            ollamaBackend.close()
        }

        val apiKey = SecurePreferences.getApiKey(context)
        cloudStatusText = if (apiKey.isNotBlank()) "ready" else "not configured"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.backends_title), style = IonVioletTypography.headline) }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(Spacing.space16)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Spacing.space16)
        ) {
            // Local file Card
            val localSubtitle = if (config.localModelFileName.isNotBlank()) {
                config.localModelFileName
            } else {
                stringResource(R.string.backends_on_device_subtitle)
            }
            BackendRowCard(
                title = stringResource(R.string.backends_on_device_title),
                subtitle = localSubtitle,
                isActive = config.selectedType == BackendType.LOCAL_FILE,
                status = localStatusText,
                onClick = onNavigateToLocalDetail,
                onSetActive = {
                    coroutineScope.launch {
                        persistence.updateSelectedType(BackendType.LOCAL_FILE)
                    }
                }
            )

            // Ollama Card
            BackendRowCard(
                title = stringResource(R.string.backends_ollama_title),
                subtitle = "${config.ollamaHost}:${config.ollamaPort} · ${config.ollamaModel}",
                isActive = config.selectedType == BackendType.OLLAMA,
                status = ollamaStatusText,
                onClick = onNavigateToOllamaDetail,
                onSetActive = {
                    coroutineScope.launch {
                        persistence.updateSelectedType(BackendType.OLLAMA)
                    }
                }
            )

            // Cloud API Card
            BackendRowCard(
                title = stringResource(R.string.backends_cloud_title),
                subtitle = if (cloudStatusText == "ready") stringResource(R.string.backends_cloud_configured) else stringResource(R.string.backends_cloud_not_configured),
                isActive = config.selectedType == BackendType.CLOUD,
                status = cloudStatusText,
                onClick = onNavigateToCloudDetail,
                onSetActive = {
                    coroutineScope.launch {
                        persistence.updateSelectedType(BackendType.CLOUD)
                    }
                }
            )

            Spacer(modifier = Modifier.height(Spacing.space8))

            // Privacy & Analytics Consent Toggle Card (Coordinated with PR #7)
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(Radii.lg20),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(
                    modifier = Modifier.padding(Spacing.space20),
                    verticalArrangement = Arrangement.spacedBy(Spacing.space12)
                ) {
                    Text(
                        text = "Privacy & Analytics",
                        style = IonVioletTypography.title,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Share anonymous usage data",
                                style = IonVioletTypography.body,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                text = "Help improve Marmaton by sharing diagnostic info.",
                                style = IonVioletTypography.bodySm,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Switch(
                            checked = config.analyticsConsent,
                            onCheckedChange = { consent ->
                                coroutineScope.launch {
                                    persistence.updateAnalyticsConsent(consent)
                                }
                            }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Speak actions aloud",
                                style = IonVioletTypography.body,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                text = "The agent narrates its goal and each action using your device's voice.",
                                style = IonVioletTypography.bodySm,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Switch(
                            checked = config.voiceEnabled,
                            onCheckedChange = { enabled ->
                                coroutineScope.launch {
                                    persistence.updateVoiceEnabled(enabled)
                                }
                            }
                        )
                    }

                    Text(
                        text = "Privacy Policy",
                        style = IonVioletTypography.label.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier
                            .clickable {
                                try {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://marmaton.ai/privacy"))
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    Log.e("BackendsListScreen", "Failed to open privacy policy link", e)
                                }
                            }
                            .padding(vertical = Spacing.space4)
                    )
                }
            }
        }
    }
}

@Composable
fun BackendRowCard(
    title: String,
    subtitle: String,
    isActive: Boolean,
    status: String,
    onClick: () -> Unit,
    onSetActive: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radii.lg20),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier.padding(Spacing.space16),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Radio button to Set Active
            RadioButton(
                selected = isActive,
                onClick = onSetActive,
                colors = RadioButtonDefaults.colors(
                    selectedColor = MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier.padding(end = Spacing.space4)
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onClick() }
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.space8)
                ) {
                    Text(
                        text = title,
                        style = IonVioletTypography.title,
                        color = MaterialTheme.colorScheme.onBackground
                    )

                    if (isActive) {
                        Box(
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(Radii.sm12))
                                .padding(horizontal = Spacing.space8, vertical = Spacing.space4)
                        ) {
                            Text(
                                text = stringResource(R.string.backend_status_active),
                                style = IonVioletTypography.mono.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(Spacing.space4))

                Text(
                    text = subtitle,
                    style = IonVioletTypography.bodySm,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(Spacing.space8))

                val statusDotColor = when (status) {
                    "ready" -> Color(0xFF2E7D32)
                    "unreachable" -> MaterialTheme.colorScheme.error
                    else -> Color.Gray
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.space4)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(statusDotColor, shape = CircleShape)
                    )
                    Text(
                        text = status,
                        style = IonVioletTypography.mono.copy(fontSize = 11.sp),
                        color = statusDotColor
                    )
                }
            }

            IconButton(onClick = onClick) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "Details",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// LOCAL BACKEND DETAIL SCREEN
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalBackendDetailScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val persistence = remember { SettingsPersistence(context) }
    val config by persistence.configFlow.collectAsState(initial = BackendConfig())

    var isImporting by remember { mutableStateOf(false) }
    var importProgress by remember { mutableStateOf(0f) }
    var importError by remember { mutableStateOf<String?>(null) }
    var importSuccess by remember { mutableStateOf(false) }

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

    fun getFileName(uri: Uri): String {
        return try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                cursor.moveToFirst()
                cursor.getString(nameIndex)
            } ?: "imported_model.task"
        } catch (e: Exception) {
            "imported_model.task"
        }
    }

    suspend fun importModelFile(uri: Uri) {
        isImporting = true
        importProgress = 0f
        importError = null
        importSuccess = false
        withContext(Dispatchers.IO) {
            try {
                val totalSize = getFileSize(uri)
                val fileName = getFileName(uri)
                val isGguf = fileName.endsWith(".gguf", ignoreCase = true)
                val destFileName = if (isGguf) "imported_model.gguf" else "imported_model.task"
                val destFile = File(context.filesDir, destFileName)
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
                persistence.updateLocalModel(destFile.absolutePath, uri.toString(), fileName)
                importSuccess = true
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
                Log.w("LocalBackendDetail", "Failed to take persistable URI permission", e)
            }
            coroutineScope.launch {
                importModelFile(uri)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.backends_local_detail_title), style = IonVioletTypography.title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(Spacing.space16)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Spacing.space16)
        ) {
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(Radii.lg20)
            ) {
                Column(
                    modifier = Modifier.padding(Spacing.space20),
                    verticalArrangement = Arrangement.spacedBy(Spacing.space12)
                ) {
                    Text(
                        text = stringResource(R.string.backends_local_model_file),
                        style = IonVioletTypography.title,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Text(
                        text = "Name: ${config.localModelFileName.ifBlank { "gemma-3n-e4b" }}",
                        style = IonVioletTypography.body,
                        color = MaterialTheme.colorScheme.onBackground
                    )

                    Text(
                        text = "Path: ${config.localModelFilePath.ifBlank { "Not Imported" }}",
                        style = IonVioletTypography.mono,
                        color = MaterialTheme.colorScheme.onBackground
                    )

                    Text(
                        text = "URI: ${config.localModelUri.ifBlank { "Not Selected" }}",
                        style = IonVioletTypography.bodySm,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (isImporting) {
                        Spacer(modifier = Modifier.height(Spacing.space8))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = stringResource(R.string.backends_local_downloading),
                                style = IonVioletTypography.label
                            )
                            Text(
                                text = "${(importProgress * 100).toInt()}%",
                                style = IonVioletTypography.label
                            )
                        }

                        LinearProgressIndicator(
                            progress = { importProgress },
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        Button(
                            onClick = { filePickerLauncher.launch(arrayOf("*/*")) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(Radii.pill100)
                        ) {
                            Text(stringResource(R.string.backends_local_choose_different), style = IonVioletTypography.title)
                        }
                    }

                    importError?.let { err ->
                        Text(text = err, color = MaterialTheme.colorScheme.error, style = IonVioletTypography.bodySm)
                    }

                    if (importSuccess) {
                        Text(text = "Success!", color = Color(0xFF2E7D32), style = IonVioletTypography.bodySm)
                    }

                    Spacer(modifier = Modifier.height(Spacing.space8))

                    // Button inside details to Set as Active
                    if (config.selectedType != BackendType.LOCAL_FILE) {
                        Button(
                            onClick = {
                                coroutineScope.launch {
                                    persistence.updateSelectedType(BackendType.LOCAL_FILE)
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(Radii.pill100),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Text("Set as active backend", style = IonVioletTypography.title)
                        }
                    }
                }
            }

            ModelCatalogSection()

            DiagnosticsSection()
        }
    }
}

// OLLAMA BACKEND DETAIL SCREEN
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OllamaBackendDetailScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val persistence = remember { SettingsPersistence(context) }
    var config by remember { mutableStateOf(BackendConfig()) }

    var schemeInput by remember { mutableStateOf("http") }
    var hostInput by remember { mutableStateOf("10.0.2.2") }
    var portInput by remember { mutableStateOf("11434") }
    var modelInput by remember { mutableStateOf("gemma") }

    var testStatusMessage by remember { mutableStateOf<String?>(null) }
    var isTesting by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        config = persistence.configFlow.first()
        schemeInput = config.ollamaScheme
        hostInput = config.ollamaHost
        portInput = config.ollamaPort.toString()
        modelInput = config.ollamaModel
    }

    LaunchedEffect(schemeInput, hostInput, portInput, modelInput) {
        val p = portInput.toIntOrNull() ?: 11434
        persistence.updateOllamaConfig(schemeInput, hostInput, p, modelInput)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.backends_ollama_detail_title), style = IonVioletTypography.title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(Spacing.space16)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Spacing.space16)
        ) {
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(Radii.lg20)
            ) {
                Column(
                    modifier = Modifier.padding(Spacing.space20),
                    verticalArrangement = Arrangement.spacedBy(Spacing.space12)
                ) {
                    OutlinedTextField(
                        value = schemeInput,
                        onValueChange = { schemeInput = it },
                        label = { Text("Scheme") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = hostInput,
                        onValueChange = { hostInput = it },
                        label = { Text(stringResource(R.string.backends_ollama_host)) },
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = portInput,
                        onValueChange = { portInput = it },
                        label = { Text(stringResource(R.string.backends_ollama_port)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = modelInput,
                        onValueChange = { modelInput = it },
                        label = { Text(stringResource(R.string.backends_ollama_model)) },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Button(
                        onClick = {
                            coroutineScope.launch {
                                isTesting = true
                                testStatusMessage = null
                                val backend = OllamaBackend(schemeInput, hostInput, portInput.toIntOrNull() ?: 11434, modelInput)
                                testStatusMessage = when (backend.status()) {
                                    is BackendStatus.Ready -> "Connected successfully!"
                                    else -> "Connection failed."
                                }
                                isTesting = false
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(Radii.pill100),
                        enabled = !isTesting
                    ) {
                        Text(stringResource(R.string.backends_ollama_test_connection), style = IonVioletTypography.title)
                    }

                    testStatusMessage?.let { msg ->
                        Text(
                            text = msg,
                            color = if (msg.contains("success")) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error,
                            style = IonVioletTypography.bodySm
                        )
                    }

                    Spacer(modifier = Modifier.height(Spacing.space8))

                    // Button inside details to Set as Active
                    if (config.selectedType != BackendType.OLLAMA) {
                        Button(
                            onClick = {
                                coroutineScope.launch {
                                    persistence.updateSelectedType(BackendType.OLLAMA)
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(Radii.pill100),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Text("Set as active backend", style = IonVioletTypography.title)
                        }
                    }
                }
            }
        }
    }
}

// CLOUD BACKEND DETAIL SCREEN
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CloudBackendDetailScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val persistence = remember { SettingsPersistence(context) }
    var config by remember { mutableStateOf(BackendConfig()) }

    var urlInput by remember { mutableStateOf("https://api.openai.com") }
    var modelInput by remember { mutableStateOf("gpt-4o-mini") }
    var apiInput by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        config = persistence.configFlow.first()
        urlInput = config.cloudBaseUrl
        modelInput = config.cloudModel
        apiInput = SecurePreferences.getApiKey(context)
    }

    LaunchedEffect(urlInput, modelInput) {
        persistence.updateCloudConfig(urlInput, modelInput)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.backends_cloud_detail_title), style = IonVioletTypography.title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(Spacing.space16)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Spacing.space16)
        ) {
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(Radii.lg20)
            ) {
                Column(
                    modifier = Modifier.padding(Spacing.space20),
                    verticalArrangement = Arrangement.spacedBy(Spacing.space12)
                ) {
                    OutlinedTextField(
                        value = urlInput,
                        onValueChange = { urlInput = it },
                        label = { Text(stringResource(R.string.backends_cloud_endpoint)) },
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = modelInput,
                        onValueChange = { modelInput = it },
                        label = { Text(stringResource(R.string.backends_cloud_model)) },
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = apiInput,
                        onValueChange = {
                            apiInput = it
                            SecurePreferences.saveApiKey(context, it)
                        },
                        label = { Text(stringResource(R.string.backends_cloud_api_key)) },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text(
                        text = stringResource(R.string.backends_cloud_privacy_note),
                        style = IonVioletTypography.bodySm,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(Spacing.space8))

                    // Button inside details to Set as Active
                    if (config.selectedType != BackendType.CLOUD) {
                        Button(
                            onClick = {
                                coroutineScope.launch {
                                    persistence.updateSelectedType(BackendType.CLOUD)
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(Radii.pill100),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Text("Set as active backend", style = IonVioletTypography.title)
                        }
                    }
                }
            }
        }
    }
}
