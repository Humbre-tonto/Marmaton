package com.marmaton.agent.ui

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.marmaton.agent.MarmatonAccessibilityService
import com.marmaton.agent.R
import com.marmaton.agent.llm.*
import com.marmaton.agent.ui.theme.IonVioletTypography
import com.marmaton.agent.ui.theme.Radii
import com.marmaton.agent.ui.theme.Spacing
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Composable
fun OnboardingScreen(
    onFinished: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val persistence = remember { SettingsPersistence(context) }
    val config by persistence.configFlow.collectAsState(initial = BackendConfig())

    var currentStep by remember { mutableStateOf(1) }
    var isAccessibilityEnabled by remember { mutableStateOf(false) }

    // Periodically check accessibility setting during step 2 or 4
    LaunchedEffect(currentStep) {
        while (currentStep == 2 || currentStep == 4) {
            isAccessibilityEnabled = isAccessibilityServiceEnabled(context, MarmatonAccessibilityService::class.java)
            kotlinx.coroutines.delay(1000)
        }
    }

    Scaffold { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(Spacing.space24),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header progress dots
                Row(
                    modifier = Modifier.padding(top = Spacing.space16),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.space8)
                ) {
                    for (i in 1..4) {
                        Box(
                            modifier = Modifier
                                .size(if (currentStep == i) 12.dp else 8.dp)
                                .background(
                                    color = if (currentStep == i) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                                    shape = RoundedCornerShape(percent = 50)
                                )
                        )
                    }
                }

                // Step Contents
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    when (currentStep) {
                        1 -> StepWelcomeAndSafety()
                        2 -> StepAccessibility(
                            isEnabled = isAccessibilityEnabled,
                            onOpenSettings = {
                                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                context.startActivity(intent)
                            }
                        )
                        3 -> StepChooseBackend(
                            selectedType = config.selectedType,
                            onSelect = { type ->
                                coroutineScope.launch {
                                    persistence.updateSelectedType(type)
                                }
                            }
                        )
                        4 -> StepReady(
                            isAccessibilityEnabled = isAccessibilityEnabled,
                            onStartMarmaton = {
                                coroutineScope.launch {
                                    persistence.updateOnboardingCompleted(true)
                                    onFinished()
                                }
                            }
                        )
                    }
                }

                // Bottom Buttons (Not step 1 or step 4, or custom)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = Spacing.space16),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (currentStep > 1 && currentStep < 4) {
                        OutlinedButton(
                            onClick = { currentStep-- },
                            modifier = Modifier
                                .height(56.dp)
                                .weight(1f)
                                .padding(end = Spacing.space8),
                            shape = RoundedCornerShape(Radii.pill100)
                        ) {
                            Text(stringResource(R.string.btn_back), style = IonVioletTypography.title)
                        }
                    }

                    if (currentStep < 4) {
                        val isContinueEnabled = when (currentStep) {
                            1 -> true
                            2 -> isAccessibilityEnabled
                            3 -> true
                            else -> false
                        }

                        Button(
                            onClick = {
                                if (currentStep == 1) {
                                    currentStep = 2
                                } else {
                                    currentStep++
                                }
                            },
                            modifier = Modifier
                                .height(56.dp)
                                .weight(1f)
                                .padding(start = if (currentStep > 1) Spacing.space8 else 0.dp),
                            shape = RoundedCornerShape(Radii.pill100),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            ),
                            enabled = isContinueEnabled
                        ) {
                            Text(
                                text = if (currentStep == 1) stringResource(R.string.onboarding_get_started) else stringResource(R.string.btn_continue),
                                style = IonVioletTypography.title,
                                color = if (isContinueEnabled) Color.White else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StepWelcomeAndSafety() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.space20)
    ) {
        Text(
            text = stringResource(R.string.onboarding_welcome_title),
            style = IonVioletTypography.display,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )

        Text(
            text = stringResource(R.string.onboarding_welcome_desc),
            style = IonVioletTypography.body,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(Spacing.space16))

        // Safety Feature Card 1
        SafetyFeatureCard(
            title = stringResource(R.string.onboarding_safety_1_title),
            desc = stringResource(R.string.onboarding_safety_1_desc),
            bulletColor = MaterialTheme.colorScheme.primary
        )

        // Safety Feature Card 2
        SafetyFeatureCard(
            title = stringResource(R.string.onboarding_safety_2_title),
            desc = stringResource(R.string.onboarding_safety_2_desc),
            bulletColor = MaterialTheme.colorScheme.error
        )

        // Safety Feature Card 3
        SafetyFeatureCard(
            title = stringResource(R.string.onboarding_safety_3_title),
            desc = stringResource(R.string.onboarding_safety_3_desc),
            bulletColor = Color(0xFF2E7D32)
        )
    }
}

@Composable
private fun SafetyFeatureCard(
    title: String,
    desc: String,
    bulletColor: Color
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
            horizontalArrangement = Arrangement.spacedBy(Spacing.space12)
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .background(bulletColor, shape = RoundedCornerShape(percent = 50))
            )
            Column {
                Text(
                    text = title,
                    style = IonVioletTypography.title,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = desc,
                    style = IonVioletTypography.bodySm,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun StepAccessibility(
    isEnabled: Boolean,
    onOpenSettings: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.space20)
    ) {
        Text(
            text = stringResource(R.string.onboarding_accessibility_title),
            style = IonVioletTypography.headline,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )

        Text(
            text = stringResource(R.string.onboarding_accessibility_desc),
            style = IonVioletTypography.body,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(Spacing.space16))

        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(Radii.lg20)
        ) {
            Column(
                modifier = Modifier.padding(Spacing.space24),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.space16)
            ) {
                val statusText = if (isEnabled) {
                    stringResource(R.string.onboarding_accessibility_status_enabled)
                } else {
                    stringResource(R.string.onboarding_accessibility_status_disabled)
                }

                val statusColor = if (isEnabled) {
                    Color(0xFF2E7D32)
                } else {
                    MaterialTheme.colorScheme.error
                }

                Text(
                    text = statusText,
                    style = IonVioletTypography.title.copy(fontWeight = FontWeight.Bold),
                    color = statusColor
                )

                Button(
                    onClick = onOpenSettings,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(Radii.pill100),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text(
                        text = stringResource(R.string.onboarding_accessibility_button),
                        style = IonVioletTypography.title,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
private fun StepChooseBackend(
    selectedType: BackendType,
    onSelect: (BackendType) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.space16)
    ) {
        Text(
            text = stringResource(R.string.onboarding_backend_title),
            style = IonVioletTypography.headline,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )

        Text(
            text = stringResource(R.string.onboarding_backend_desc),
            style = IonVioletTypography.bodySm,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(Spacing.space8))

        BackendSelectCard(
            type = BackendType.LOCAL_FILE,
            title = stringResource(R.string.onboarding_backend_local),
            desc = stringResource(R.string.onboarding_backend_local_desc),
            isSelected = selectedType == BackendType.LOCAL_FILE,
            onClick = { onSelect(BackendType.LOCAL_FILE) }
        )

        BackendSelectCard(
            type = BackendType.OLLAMA,
            title = stringResource(R.string.onboarding_backend_ollama),
            desc = stringResource(R.string.onboarding_backend_ollama_desc),
            isSelected = selectedType == BackendType.OLLAMA,
            onClick = { onSelect(BackendType.OLLAMA) }
        )

        BackendSelectCard(
            type = BackendType.CLOUD,
            title = stringResource(R.string.onboarding_backend_cloud),
            desc = stringResource(R.string.onboarding_backend_cloud_desc),
            isSelected = selectedType == BackendType.CLOUD,
            onClick = { onSelect(BackendType.CLOUD) }
        )
    }
}

@Composable
private fun BackendSelectCard(
    type: BackendType,
    title: String,
    desc: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(Radii.lg20),
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Row(
            modifier = Modifier.padding(Spacing.space16),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.space16)
        ) {
            RadioButton(
                selected = isSelected,
                onClick = onClick,
                colors = RadioButtonDefaults.colors(
                    selectedColor = MaterialTheme.colorScheme.primary
                )
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = IonVioletTypography.title,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = desc,
                    style = IonVioletTypography.bodySm,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun StepReady(
    isAccessibilityEnabled: Boolean,
    onStartMarmaton: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.space20)
    ) {
        Text(
            text = stringResource(R.string.onboarding_ready_title),
            style = IonVioletTypography.headline,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )

        Text(
            text = stringResource(R.string.onboarding_ready_desc),
            style = IonVioletTypography.body,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(Spacing.space16))

        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(Radii.lg20)
        ) {
            Column(
                modifier = Modifier.padding(Spacing.space24),
                verticalArrangement = Arrangement.spacedBy(Spacing.space16)
            ) {
                ChecklistRow(
                    text = stringResource(R.string.onboarding_check_accessibility),
                    isChecked = isAccessibilityEnabled
                )

                ChecklistRow(
                    text = stringResource(R.string.onboarding_check_backend),
                    isChecked = true // Backend defaults to LOCAL_FILE which is configurable later
                )
            }
        }

        Spacer(modifier = Modifier.height(Spacing.space24))

        Button(
            onClick = onStartMarmaton,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(Radii.pill100),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            ),
            enabled = isAccessibilityEnabled
        ) {
            Text(
                text = stringResource(R.string.onboarding_start_marmaton),
                style = IonVioletTypography.title,
                color = Color.White
            )
        }
    }
}

@Composable
private fun ChecklistRow(
    text: String,
    isChecked: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.space12)
    ) {
        Box(
            modifier = Modifier
                .size(16.dp)
                .background(
                    color = if (isChecked) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error,
                    shape = RoundedCornerShape(percent = 50)
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (isChecked) "✓" else "✗",
                style = TextStyle(
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            )
        }

        Text(
            text = text,
            style = IonVioletTypography.body,
            color = MaterialTheme.colorScheme.onBackground
        )
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
