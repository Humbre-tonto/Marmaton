package com.marmaton.agent.ui

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.marmaton.agent.AgentForegroundService
import com.marmaton.agent.MarmatonAccessibilityService
import com.marmaton.agent.R
import com.marmaton.agent.llm.*
import com.marmaton.agent.ui.theme.IonVioletTypography
import com.marmaton.agent.ui.theme.LocalCustomColors
import com.marmaton.agent.ui.theme.Radii
import com.marmaton.agent.ui.theme.Spacing
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class HomeState {
    IDLE, RUNNING, FINISHED, ERROR, BLOCKING
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeControlScreen() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val persistence = remember { SettingsPersistence(context) }
    val config by persistence.configFlow.collectAsState(initial = BackendConfig())

    var activeBackendStatus by remember { mutableStateOf<BackendStatus>(BackendStatus.NotReady("Checking...")) }
    var activeBackendDisplayName by remember { mutableStateOf("Local model") }

    val isAgentRunning by AgentForegroundService.isRunning.collectAsState()
    val runLog by AgentForegroundService.runLog.collectAsState()
    val userGoal by AgentForegroundService.userGoal.collectAsState()

    var userGoalInput by remember { mutableStateOf("") }
    var isAccessibilityEnabled by remember { mutableStateOf(false) }

    // Real service state tracking
    var hasFinishedSuccessfully by remember { mutableStateOf(false) }
    var hasFinishedWithError by remember { mutableStateOf(false) }
    var lastErrorLine by remember { mutableStateOf("") }

    // Periodically check if Accessibility service is enabled
    LaunchedEffect(Unit) {
        while (true) {
            isAccessibilityEnabled = isAccessibilityServiceEnabled(context, MarmatonAccessibilityService::class.java)
            delay(1000)
        }
    }

    // Periodically check active backend status
    LaunchedEffect(config) {
        while (true) {
            val backend = BackendFactory.createBackend(context, config)
            activeBackendDisplayName = backend.displayName
            activeBackendStatus = try {
                backend.status()
            } catch (e: Throwable) {
                BackendStatus.Unavailable(e.message ?: e.toString())
            }
            delay(2000)
        }
    }

    // Observe runLog to detect real finished/error agent transitions
    LaunchedEffect(runLog) {
        if (runLog.isNotEmpty()) {
            val lastLine = runLog.last()
            if (lastLine.contains("[Agent] Goal Successfully Achieved!")) {
                hasFinishedSuccessfully = true
                hasFinishedWithError = false
            } else if (lastLine.contains("[Error]")) {
                hasFinishedWithError = true
                hasFinishedSuccessfully = false
                lastErrorLine = lastLine
            }
        }
    }

    // Clear completed/error states if we start running again
    LaunchedEffect(isAgentRunning) {
        if (isAgentRunning) {
            hasFinishedSuccessfully = false
            hasFinishedWithError = false
        }
    }

    // Goal input initialization helper
    LaunchedEffect(userGoal) {
        if (userGoal.isNotEmpty() && userGoalInput.isEmpty()) {
            userGoalInput = userGoal
        }
    }

    // Determine State
    val currentState = when {
        !isAccessibilityEnabled -> HomeState.BLOCKING
        isAgentRunning -> HomeState.RUNNING
        hasFinishedSuccessfully -> HomeState.FINISHED
        hasFinishedWithError -> HomeState.ERROR
        else -> HomeState.IDLE
    }

    // Continuous timer for running state
    var elapsedSeconds by remember { mutableStateOf(0) }
    LaunchedEffect(isAgentRunning) {
        if (isAgentRunning) {
            elapsedSeconds = 0
            while (isAgentRunning) {
                delay(1000)
                elapsedSeconds++
            }
        }
    }

    val minutes = elapsedSeconds / 60
    val seconds = elapsedSeconds % 60
    val timeFormatted = String.format("%d:%02d", minutes, seconds)

    Scaffold(
        bottomBar = {
            if (isAgentRunning) {
                Button(
                    onClick = {
                        AgentForegroundService.stopAgent(context)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .padding(Spacing.space8),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(Radii.pill100)
                ) {
                    Text(
                        text = stringResource(R.string.home_emergency_stop),
                        style = IonVioletTypography.title.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(Spacing.space16),
            verticalArrangement = Arrangement.spacedBy(Spacing.space16)
        ) {
            when (currentState) {
                HomeState.BLOCKING -> {
                    // Blocking State (Accessibility off)
                    BlockingStateCard(
                        onOpenSettings = {
                            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                            context.startActivity(intent)
                        }
                    )
                }

                HomeState.IDLE -> {
                    // IDLE State
                    IdleStateCard(
                        userGoalInput = userGoalInput,
                        onGoalChange = { userGoalInput = it },
                        isStartEnabled = activeBackendStatus is BackendStatus.Ready,
                        onStart = {
                            val goal = if (userGoalInput.isNotBlank()) userGoalInput else "Turn on Battery Saver"
                            AgentForegroundService.startAgent(context, goal)
                        },
                        activeBackendDisplayName = activeBackendDisplayName,
                        activeBackendStatus = activeBackendStatus,
                        isAccessibilityEnabled = isAccessibilityEnabled
                    )
                }

                HomeState.RUNNING -> {
                    // RUNNING State
                    RunningStateCard(
                        timeFormatted = timeFormatted,
                        onStop = {
                            AgentForegroundService.stopAgent(context)
                        },
                        goal = userGoal,
                        runLog = runLog
                    )
                }

                HomeState.FINISHED -> {
                    // FINISHED State
                    FinishedStateCard(
                        timeFormatted = timeFormatted.ifEmpty { "0:12" },
                        stepCount = runLog.filter { it.contains("[Action]") }.size.coerceAtLeast(1),
                        onRunAgain = {
                            hasFinishedSuccessfully = false
                            AgentForegroundService.startAgent(context, userGoal)
                        },
                        onNewObjective = {
                            hasFinishedSuccessfully = false
                            userGoalInput = ""
                        }
                    )
                }

                HomeState.ERROR -> {
                    // ERROR State
                    ErrorStateCard(
                        errorDetail = lastErrorLine.ifEmpty { "Unexpected error context found." },
                        onRetry = {
                            hasFinishedWithError = false
                            AgentForegroundService.startAgent(context, userGoal)
                        },
                        onCancel = {
                            hasFinishedWithError = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun BlockingStateCard(onOpenSettings: () -> Unit) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radii.xl24),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(Spacing.space24),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.space16)
        ) {
            Text(
                text = stringResource(R.string.home_blocking_title),
                style = IonVioletTypography.headline,
                color = MaterialTheme.colorScheme.error
            )

            Text(
                text = stringResource(R.string.home_blocking_desc),
                style = IonVioletTypography.body,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center
            )

            Button(
                onClick = onOpenSettings,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(Radii.pill100),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text(
                    text = stringResource(R.string.onboarding_accessibility_button),
                    style = IonVioletTypography.title,
                    color = Color.White
                )
            }
        }
    }
}

@Composable
fun IdleStateCard(
    userGoalInput: String,
    onGoalChange: (String) -> Unit,
    isStartEnabled: Boolean,
    onStart: () -> Unit,
    activeBackendDisplayName: String,
    activeBackendStatus: BackendStatus,
    isAccessibilityEnabled: Boolean
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.space16)
    ) {
        // Hero Card
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(Radii.xl24),
            colors = CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier.padding(Spacing.space24),
                verticalArrangement = Arrangement.spacedBy(Spacing.space16)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "AGENT · IDLE",
                        style = IonVioletTypography.label,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(Color.Gray, shape = CircleShape)
                    )
                }

                Text(
                    text = stringResource(R.string.home_agent_idle_title),
                    style = IonVioletTypography.headline,
                    color = MaterialTheme.colorScheme.onBackground
                )

                OutlinedTextField(
                    value = userGoalInput,
                    onValueChange = onGoalChange,
                    placeholder = { Text(stringResource(R.string.home_goal_label)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(76.dp),
                    shape = RoundedCornerShape(Radii.md16)
                )

                Button(
                    onClick = onStart,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(Radii.pill100),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    ),
                    enabled = isStartEnabled && isAccessibilityEnabled
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(Spacing.space8),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Start",
                            tint = Color.White
                        )
                        Text(
                            text = stringResource(R.string.home_start_btn),
                            style = IonVioletTypography.title,
                            color = Color.White
                        )
                    }
                }
            }
        }

        // Info Rows
        val successColor = LocalCustomColors.current.successContainer
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.space12)
        ) {
            InfoCard(
                modifier = Modifier.weight(1f),
                label = stringResource(R.string.home_backend_title),
                value = activeBackendDisplayName,
                statusDotColor = if (activeBackendStatus is BackendStatus.Ready) successColor else MaterialTheme.colorScheme.error,
                statusText = if (activeBackendStatus is BackendStatus.Ready) stringResource(R.string.home_backend_ready) else stringResource(R.string.home_backend_not_ready)
            )

            InfoCard(
                modifier = Modifier.weight(1f),
                label = stringResource(R.string.home_accessibility_title),
                value = if (isAccessibilityEnabled) stringResource(R.string.home_accessibility_connected) else stringResource(R.string.home_accessibility_disconnected),
                statusDotColor = if (isAccessibilityEnabled) successColor else MaterialTheme.colorScheme.error,
                statusText = if (isAccessibilityEnabled) stringResource(R.string.home_accessibility_service_on) else stringResource(R.string.home_accessibility_service_off)
            )
        }
    }
}

@Composable
fun InfoCard(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    statusDotColor: Color,
    statusText: String
) {
    ElevatedCard(
        modifier = modifier,
        shape = RoundedCornerShape(Radii.md16),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(Spacing.space16),
            verticalArrangement = Arrangement.spacedBy(Spacing.space4)
        ) {
            Text(
                text = label,
                style = IonVioletTypography.label,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = IonVioletTypography.title.copy(fontSize = 15.sp),
                color = MaterialTheme.colorScheme.primary
            )
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
                    text = statusText,
                    style = IonVioletTypography.mono.copy(fontSize = 11.sp),
                    color = statusDotColor
                )
            }
        }
    }
}

@Composable
fun RunningStateCard(
    timeFormatted: String,
    onStop: () -> Unit,
    goal: String,
    runLog: List<String>
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alphaAnim by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.space16)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.space8)
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .alpha(alphaAnim)
                        .background(MaterialTheme.colorScheme.primary, shape = CircleShape)
                )
                Text(
                    text = "${stringResource(R.string.home_agent_running)} · $timeFormatted",
                    style = IonVioletTypography.title,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            Button(
                onClick = onStop,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(Radii.pill100),
                contentPadding = PaddingValues(horizontal = Spacing.space16, vertical = Spacing.space8)
            ) {
                Text(
                    text = stringResource(R.string.home_stop_btn),
                    style = IonVioletTypography.title.copy(fontSize = 14.sp)
                )
            }
        }

        // Action Step Card
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(Radii.xl24),
            colors = CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier.padding(Spacing.space24),
                verticalArrangement = Arrangement.spacedBy(Spacing.space16)
            ) {
                val currentStepIndex = runLog.filter { it.contains("[Action]") }.size.coerceAtLeast(1)
                Text(
                    text = "STEP $currentStepIndex · TAP",
                    style = IonVioletTypography.label,
                    color = MaterialTheme.colorScheme.primary
                )

                Text(
                    text = "\"$goal\"",
                    style = IonVioletTypography.headline.copy(fontSize = 20.sp),
                    color = MaterialTheme.colorScheme.onBackground
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.space12)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(MaterialTheme.colorScheme.primaryContainer, shape = CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "⚡",
                            style = androidx.compose.ui.text.TextStyle(fontSize = 18.sp)
                        )
                    }

                    Column {
                        Text(
                            text = "Tapping \"$goal\"",
                            style = IonVioletTypography.title.copy(fontSize = 15.sp),
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "Settings › System",
                            style = IonVioletTypography.bodySm,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f),
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        // Mini Step List
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(Spacing.space8)
        ) {
            items(runLog.takeLast(3)) { logLine ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(Radii.sm12))
                        .padding(Spacing.space12),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.space12)
                ) {
                    Text(
                        text = "✓",
                        style = IonVioletTypography.title,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = logLine,
                        style = IonVioletTypography.bodySm,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
            }
        }
    }
}

@Composable
fun FinishedStateCard(
    timeFormatted: String,
    stepCount: Int,
    onRunAgain: () -> Unit,
    onNewObjective: () -> Unit
) {
    val successColor = LocalCustomColors.current.successContainer
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radii.xl24),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(Spacing.space24),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.space16)
        ) {
            Text(
                text = stringResource(R.string.home_finished_success),
                style = IonVioletTypography.headline,
                color = if (successColor != Color.Transparent) successColor else Color(0xFF2E7D32)
            )

            Text(
                text = stringResource(R.string.home_finished_desc, timeFormatted, stepCount),
                style = IonVioletTypography.body,
                color = MaterialTheme.colorScheme.onBackground
            )

            Text(
                text = stringResource(R.string.home_verification_line),
                style = IonVioletTypography.bodySm,
                color = if (successColor != Color.Transparent) successColor else Color(0xFF2E7D32),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(Spacing.space8))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.space12)
            ) {
                Button(
                    onClick = onRunAgain,
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp),
                    shape = RoundedCornerShape(Radii.pill100),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text(
                        text = stringResource(R.string.home_run_again),
                        style = IonVioletTypography.title,
                        color = Color.White
                    )
                }

                OutlinedButton(
                    onClick = onNewObjective,
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp),
                    shape = RoundedCornerShape(Radii.pill100)
                ) {
                    Text(
                        text = stringResource(R.string.home_new_objective),
                        style = IonVioletTypography.title
                    )
                }
            }
        }
    }
}

@Composable
fun ErrorStateCard(
    errorDetail: String,
    onRetry: () -> Unit,
    onCancel: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radii.xl24),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(Spacing.space24),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.space16)
        ) {
            Text(
                text = stringResource(R.string.home_error_title),
                style = IonVioletTypography.headline,
                color = MaterialTheme.colorScheme.error
            )

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(Spacing.space4),
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    text = errorDetail,
                    style = IonVioletTypography.body,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.space12)
            ) {
                Button(
                    onClick = onRetry,
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp),
                    shape = RoundedCornerShape(Radii.pill100),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text(
                        text = stringResource(R.string.home_retry),
                        style = IonVioletTypography.title,
                        color = Color.White
                    )
                }

                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp),
                    shape = RoundedCornerShape(Radii.pill100)
                ) {
                    Text(
                        text = stringResource(R.string.backends_cancel),
                        style = IonVioletTypography.title
                    )
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
