package com.marmaton.agent.ui

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Switch
import androidx.compose.foundation.layout.PaddingValues
import android.net.Uri
import com.marmaton.agent.AgentForegroundService
import com.marmaton.agent.MarmatonAccessibilityService
import com.marmaton.agent.llm.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(onNavigateToSettings: () -> Unit) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val persistence = remember { SettingsPersistence(context) }
    val config by persistence.configFlow.collectAsState(initial = BackendConfig())

    var activeBackendStatus by remember { mutableStateOf<BackendStatus>(BackendStatus.NotReady("Checking status...")) }
    var activeBackendDisplayName by remember { mutableStateOf("Local Model File") }

    val modelStatus by GemmaAgentEngine.modelStatus.collectAsState()
    val isDownloading by GemmaAgentEngine.isDownloading.collectAsState()

    val isAgentRunning by AgentForegroundService.isRunning.collectAsState()
    val runLog by AgentForegroundService.runLog.collectAsState()

    var userGoalInput by remember { mutableStateOf("Turn on Battery Saver") }
    var isAccessibilityEnabled by remember { mutableStateOf(false) }

    // Periodically check if Accessibility service is enabled
    LaunchedEffect(Unit) {
        GemmaAgentEngine.initModel()
        while (true) {
            isAccessibilityEnabled = isAccessibilityServiceEnabled(context, MarmatonAccessibilityService::class.java)
            kotlinx.coroutines.delay(1000)
        }
    }

    // Periodically check backend status
    LaunchedEffect(config) {
        while (true) {
            val backend = BackendFactory.createBackend(context, config)
            activeBackendDisplayName = backend.displayName
            activeBackendStatus = try {
                backend.status()
            } catch (e: Throwable) {
                BackendStatus.Unavailable(e.message ?: e.toString())
            }
            kotlinx.coroutines.delay(2000)
        }
    }

    val logListState = rememberLazyListState()
    LaunchedEffect(runLog.size) {
        if (runLog.isNotEmpty()) {
            logListState.animateScrollToItem(runLog.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Marmaton AI Device Agent") },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(imageVector = Icons.Default.Settings, contentDescription = "Settings")
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // LLM Backend Status Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Active LLM Backend",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Selected: $activeBackendDisplayName",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Spacer(modifier = Modifier.height(4.dp))

                    val statusText = when (val status = activeBackendStatus) {
                        is BackendStatus.Ready -> "Ready"
                        is BackendStatus.NotReady -> "Not Ready: ${status.reason}"
                        is BackendStatus.Unavailable -> "Unavailable: ${status.reason}"
                    }

                    val statusColor = when (activeBackendStatus) {
                        is BackendStatus.Ready -> Color(0xFF2E7D32)
                        is BackendStatus.NotReady -> Color(0xFFE65100)
                        is BackendStatus.Unavailable -> MaterialTheme.colorScheme.error
                    }

                    Text(
                        text = "Status: $statusText",
                        style = MaterialTheme.typography.bodyMedium,
                        color = statusColor
                    )

                    // For AICore backend specifically, let's keep the download buttons if relevant
                    if (config.selectedType == BackendType.AICORE) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = "AICore Internal Status: $modelStatus", style = MaterialTheme.typography.bodySmall)
                        if (isDownloading) {
                            Spacer(modifier = Modifier.height(8.dp))
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        } else if (modelStatus.contains("download") || modelStatus.contains("ready to be downloaded")) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(onClick = {
                                coroutineScope.launch {
                                    GemmaAgentEngine.startDownload()
                                }
                            }) {
                                Text("Download Gemma 4")
                            }
                        }
                    }
                }
            }

            // Permissions Card
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Permissions & System Setup",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isAccessibilityEnabled) "Accessibility: Enabled" else "Accessibility: Disabled",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isAccessibilityEnabled) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error
                        )
                        Button(onClick = {
                            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                            context.startActivity(intent)
                        }) {
                            Text("Open Settings")
                        }
                    }
                }
            }

            // Privacy & Data Consent Card
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Privacy & Data",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Marmaton is local-first and privacy-respecting. We never collect keys, prompts, or screen text.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(modifier = Modifier.height(8.dp))
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
                                text = "Helps us improve. Anonymous only, no personal data.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = config.analyticsConsent,
                            onCheckedChange = { checked ->
                                coroutineScope.launch {
                                    persistence.updateAnalyticsConsent(checked)
                                    com.marmaton.agent.analytics.Analytics.get().setEnabled(checked)
                                }
                            }
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    val localUri = Uri.parse("https://marmaton.ai/privacy")
                    TextButton(
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, localUri)
                            context.startActivity(intent)
                        },
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text("View our Privacy Policy")
                    }
                }
            }

            // Agent Goal & Execution Controls
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Set Agent Objective",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = userGoalInput,
                        onValueChange = { userGoalInput = it },
                        label = { Text("What should the agent do?") },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isAgentRunning
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val isBackendReady = activeBackendStatus is BackendStatus.Ready
                        Button(
                            onClick = {
                                AgentForegroundService.startAgent(context, userGoalInput)
                            },
                            modifier = Modifier.weight(1f),
                            enabled = !isAgentRunning && isAccessibilityEnabled && isBackendReady
                        ) {
                            Text("Start Agent")
                        }

                        Button(
                            onClick = {
                                AgentForegroundService.stopAgent(context)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier.weight(1f),
                            enabled = isAgentRunning
                        ) {
                            Text("Emergency Stop")
                        }
                    }
                }
            }

            // Live Terminal Run Log
            Text(
                text = "Live Execution Logs",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Color.Black, shape = RoundedCornerShape(8.dp))
                    .padding(8.dp)
            ) {
                LazyColumn(
                    state = logListState,
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(runLog) { logLine ->
                        Text(
                            text = logLine,
                            color = when {
                                logLine.contains("[Reasoner]") || logLine.contains("[Gemma]") -> Color(0xFF81C784)
                                logLine.contains("[Action]") -> Color(0xFF64B5F6)
                                logLine.contains("[Error]") -> Color(0xFFE57373)
                                else -> Color.White
                            },
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}

private fun isAccessibilityServiceEnabled(context: Context, service: Class<out AccessibilityService>): Boolean {
    val expectedComponentName = "${context.packageName}/${service.name}"
    val enabledServicesSetting = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
    ) ?: return false

    return enabledServicesSetting.split(':').any { it.equals(expectedComponentName, ignoreCase = true) }
}
