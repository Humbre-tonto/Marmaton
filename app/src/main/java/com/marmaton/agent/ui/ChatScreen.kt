package com.marmaton.agent.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.marmaton.agent.chat.ChatSession
import com.marmaton.agent.ui.theme.IonVioletTypography
import com.marmaton.agent.ui.theme.Radii
import com.marmaton.agent.ui.theme.Spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen() {
    val context = LocalContext.current
    val messages by ChatSession.messages.collectAsState()
    val isGenerating by ChatSession.isGenerating.collectAsState()

    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    // Keep the newest item (last message, or the typing indicator) in view as it grows.
    LaunchedEffect(messages.size, isGenerating) {
        val itemCount = messages.size + if (isGenerating) 1 else 0
        if (itemCount > 0) listState.animateScrollToItem(itemCount - 1)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Chat",
                        style = IonVioletTypography.title
                    )
                },
                actions = {
                    if (messages.isNotEmpty()) {
                        IconButton(onClick = { ChatSession.clear() }) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Clear chat"
                            )
                        }
                    }
                }
            )
        },
        bottomBar = {
            ChatInputBar(
                value = input,
                onValueChange = { input = it },
                enabled = !isGenerating,
                onSend = {
                    ChatSession.send(context, input)
                    input = ""
                }
            )
        }
    ) { paddingValues ->
        if (messages.isEmpty()) {
            EmptyChatHint(modifier = Modifier.padding(paddingValues))
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = Spacing.space16),
                verticalArrangement = Arrangement.spacedBy(Spacing.space8),
                contentPadding = PaddingValues(vertical = Spacing.space16)
            ) {
                items(messages) { message ->
                    ChatBubble(message)
                }
                if (isGenerating) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Start
                        ) {
                            AssistantTyping()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyChatHint(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(Spacing.space24),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "💬",
            style = IonVioletTypography.headline
        )
        Spacer(Modifier.height(Spacing.space12))
        Text(
            text = "Chat with your on-device model",
            style = IonVioletTypography.headline,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(Spacing.space8))
        Text(
            text = "Ask a question or get help writing a small piece of code. Uses whichever model you selected under Backends.",
            style = IonVioletTypography.body,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun ChatBubble(message: ChatSession.ChatMessage) {
    val isUser = message.role == ChatSession.Role.USER
    val bubbleColor = if (isUser) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val textColor = if (isUser) Color.White else MaterialTheme.colorScheme.onSurface

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 320.dp)
                .background(bubbleColor, shape = RoundedCornerShape(Radii.md16))
                .padding(horizontal = Spacing.space16, vertical = Spacing.space12)
        ) {
            Text(
                text = message.text,
                style = IonVioletTypography.body,
                color = textColor
            )
        }
    }
}

@Composable
private fun AssistantTyping() {
    Box(
        modifier = Modifier
            .background(
                MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(Radii.md16)
            )
            .padding(horizontal = Spacing.space16, vertical = Spacing.space12)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.space8)
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "Thinking…",
                style = IonVioletTypography.bodySm,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ChatInputBar(
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean,
    onSend: () -> Unit
) {
    Surface(
        tonalElevation = 3.dp,
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.space12),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.space8)
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text("Message") },
                shape = RoundedCornerShape(Radii.md16),
                maxLines = 4
            )
            val canSend = enabled && value.isNotBlank()
            Button(
                onClick = onSend,
                enabled = canSend,
                shape = RoundedCornerShape(Radii.pill100),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                ),
                contentPadding = PaddingValues(horizontal = Spacing.space16, vertical = Spacing.space12)
            ) {
                Text(
                    text = "Send",
                    style = IonVioletTypography.title,
                    color = Color.White
                )
            }
        }
    }
}
