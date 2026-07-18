package com.marmaton.agent.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.marmaton.agent.AgentForegroundService
import com.marmaton.agent.R
import com.marmaton.agent.ui.theme.IonVioletTypography
import com.marmaton.agent.ui.theme.Radii
import com.marmaton.agent.ui.theme.Spacing
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivityLogScreen() {
    val context = LocalContext.current
    val runLog by AgentForegroundService.runLog.collectAsState()
    val isAgentRunning by AgentForegroundService.isRunning.collectAsState()

    var selectedIndex by remember { mutableStateOf(0) }
    val options = listOf(
        stringResource(R.string.activity_timeline),
        stringResource(R.string.activity_raw)
    )

    // Remember the timestamps mapped to each log line so they stay stable
    val logTimestamps = remember(runLog) {
        val formatter = SimpleDateFormat("h:mm:ss a", Locale.getDefault())
        val currentTime = formatter.format(Date())
        runLog.associateWith { currentTime }
    }

    val listState = rememberLazyListState()
    LaunchedEffect(runLog.size) {
        if (runLog.isNotEmpty()) {
            listState.animateScrollToItem(runLog.size - 1)
        }
    }

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
                        style = IonVioletTypography.title
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
            Text(
                text = stringResource(R.string.activity_title),
                style = IonVioletTypography.headline,
                color = MaterialTheme.colorScheme.onBackground
            )

            // Segmented Button
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier.fillMaxWidth()
            ) {
                options.forEachIndexed { index, label ->
                    SegmentedButton(
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                        onClick = { selectedIndex = index },
                        selected = index == selectedIndex
                    ) {
                        Text(text = label, style = IonVioletTypography.label)
                    }
                }
            }

            if (selectedIndex == 0) {
                // Timeline View
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(Spacing.space12)
                ) {
                    items(runLog) { logLine ->
                        val isHighlighted = isAgentRunning && logLine == runLog.last()
                        val animatedBorderColor by animateColorAsState(
                            targetValue = if (isHighlighted) MaterialTheme.colorScheme.primary else Color.Transparent,
                            label = "border"
                        )

                        val timestamp = logTimestamps[logLine] ?: ""

                        ElevatedCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(2.dp, animatedBorderColor, RoundedCornerShape(Radii.md16)),
                            shape = RoundedCornerShape(Radii.md16),
                            colors = CardDefaults.elevatedCardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(Spacing.space16),
                                verticalArrangement = Arrangement.spacedBy(Spacing.space4)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = if (logLine.contains("[Action]")) "ACTION" else "INFO",
                                        style = IonVioletTypography.label,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = timestamp,
                                        style = IonVioletTypography.mono.copy(fontSize = 11.sp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Text(
                                    text = logLine,
                                    style = IonVioletTypography.bodySm,
                                    color = MaterialTheme.colorScheme.onBackground
                                )

                                if (logLine.contains("[Reasoner]")) {
                                    HorizontalDivider(modifier = Modifier.padding(vertical = Spacing.space8))
                                    Text(
                                        text = stringResource(R.string.activity_reasoning, logLine.substringAfter("[Reasoner]")),
                                        style = IonVioletTypography.bodySm,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                // Raw Console Logs
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(Color.Black, shape = RoundedCornerShape(Radii.md16))
                        .padding(Spacing.space12)
                ) {
                    LazyColumn(
                        state = listState,
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
                                style = IonVioletTypography.mono,
                                modifier = Modifier.padding(vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
