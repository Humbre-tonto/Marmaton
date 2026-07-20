package com.marmaton.agent.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.marmaton.agent.AgentForegroundService
import com.marmaton.agent.ui.theme.IonVioletTypography
import com.marmaton.agent.ui.theme.Radii
import com.marmaton.agent.ui.theme.Spacing
import com.marmaton.agent.workflow.Workflow
import com.marmaton.agent.workflow.WorkflowRepository
import kotlinx.coroutines.launch
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkflowsScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = remember { WorkflowRepository(context) }
    val workflows by repo.workflowsFlow.collectAsState(initial = emptyList())
    val isAgentRunning by AgentForegroundService.isRunning.collectAsState()

    // null = editor closed; otherwise the workflow being edited (a fresh one for "new").
    var editing by remember { mutableStateOf<Workflow?>(null) }
    var showEditor by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = "Workflows", style = IonVioletTypography.title) }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    editing = Workflow(id = UUID.randomUUID().toString(), name = "", steps = listOf(""))
                    showEditor = true
                },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.Add, contentDescription = "New workflow", tint = Color.White)
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = Spacing.space16)
        ) {
            if (isAgentRunning) {
                AgentBusyBanner()
                Spacer(Modifier.height(Spacing.space12))
            }

            if (workflows.isEmpty()) {
                EmptyWorkflowsHint(modifier = Modifier.weight(1f))
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(Spacing.space12),
                    contentPadding = PaddingValues(vertical = Spacing.space16)
                ) {
                    items(workflows) { workflow ->
                        WorkflowCard(
                            workflow = workflow,
                            runEnabled = !isAgentRunning,
                            onRun = {
                                AgentForegroundService.startWorkflow(context, workflow.name, workflow.steps)
                            },
                            onEdit = {
                                editing = workflow
                                showEditor = true
                            },
                            onDelete = {
                                scope.launch { repo.delete(workflow.id) }
                            }
                        )
                    }
                }
            }
        }
    }

    val current = editing
    if (showEditor && current != null) {
        WorkflowEditorDialog(
            initial = current,
            onDismiss = { showEditor = false; editing = null },
            onSave = { saved ->
                scope.launch { repo.upsert(saved) }
                showEditor = false
                editing = null
            }
        )
    }
}

@Composable
private fun AgentBusyBanner() {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = Spacing.space12),
        shape = RoundedCornerShape(Radii.md16),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Text(
            text = "The agent is currently running. Stop it from the Home tab before starting a workflow.",
            style = IonVioletTypography.bodySm,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(Spacing.space12)
        )
    }
}

@Composable
private fun EmptyWorkflowsHint(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(Spacing.space24),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "🔀", style = IonVioletTypography.headline)
        Spacer(Modifier.height(Spacing.space12))
        Text(
            text = "No workflows yet",
            style = IonVioletTypography.headline,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(Spacing.space8))
        Text(
            text = "Create a workflow to have the agent do several things in order — like opening a chat, sending a message, then going home. Tap + to start.",
            style = IonVioletTypography.body,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun WorkflowCard(
    workflow: Workflow,
    runEnabled: Boolean,
    onRun: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radii.xl24),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(Spacing.space16),
            verticalArrangement = Arrangement.spacedBy(Spacing.space8)
        ) {
            Text(
                text = workflow.name.ifBlank { "Untitled workflow" },
                style = IonVioletTypography.title,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "${workflow.stepCount} step${if (workflow.stepCount == 1) "" else "s"}",
                style = IonVioletTypography.label,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            workflow.steps.forEachIndexed { index, step ->
                Text(
                    text = "${index + 1}. $step",
                    style = IonVioletTypography.bodySm,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.space8),
                horizontalArrangement = Arrangement.spacedBy(Spacing.space8),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onRun,
                    enabled = runEnabled,
                    shape = RoundedCornerShape(Radii.pill100),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    contentPadding = PaddingValues(horizontal = Spacing.space16, vertical = Spacing.space8)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White)
                    Spacer(Modifier.width(Spacing.space4))
                    Text("Run", style = IonVioletTypography.title, color = Color.White)
                }
                TextButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit")
                    Spacer(Modifier.width(Spacing.space4))
                    Text("Edit")
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
private fun WorkflowEditorDialog(
    initial: Workflow,
    onDismiss: () -> Unit,
    onSave: (Workflow) -> Unit
) {
    var name by remember { mutableStateOf(initial.name) }
    val steps = remember {
        mutableStateListOf<String>().apply {
            addAll(if (initial.steps.isEmpty()) listOf("") else initial.steps)
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        ElevatedCard(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 560.dp),
            shape = RoundedCornerShape(Radii.xl24),
            colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .padding(Spacing.space20)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.space12)
            ) {
                Text(
                    text = if (initial.name.isBlank()) "New workflow" else "Edit workflow",
                    style = IonVioletTypography.headline,
                    color = MaterialTheme.colorScheme.onBackground
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Workflow name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(Radii.md16)
                )

                Text(
                    text = "Steps (run in order)",
                    style = IonVioletTypography.label,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                steps.forEachIndexed { index, step ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.space8)
                    ) {
                        OutlinedTextField(
                            value = step,
                            onValueChange = { steps[index] = it },
                            label = { Text("Step ${index + 1}") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(Radii.md16),
                            placeholder = { Text("e.g. Send \"on my way\" to Mom") }
                        )
                        if (steps.size > 1) {
                            IconButton(onClick = { steps.removeAt(index) }) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Remove step",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }

                TextButton(onClick = { steps.add("") }) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(Spacing.space4))
                    Text("Add step")
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = Spacing.space8),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.space12)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(Radii.pill100)
                    ) {
                        Text("Cancel")
                    }
                    val cleanedSteps = steps.map { it.trim() }.filter { it.isNotEmpty() }
                    val canSave = name.isNotBlank() && cleanedSteps.isNotEmpty()
                    Button(
                        onClick = {
                            onSave(initial.copy(name = name.trim(), steps = cleanedSteps))
                        },
                        enabled = canSave,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(Radii.pill100),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("Save", color = Color.White)
                    }
                }
            }
        }
    }
}
