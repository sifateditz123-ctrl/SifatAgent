package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.RemoveRedEye
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.automation.ActionValidator
import com.example.data.local.ChatMessageEntity
import com.example.ui.AssistantUiState
import com.example.ui.SifatViewModel
import com.example.ui.components.ActionPlanConfirmationCard
import com.example.ui.theme.DangerCoral
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.NeonViolet
import com.example.voice.VoiceRecognitionState

@Composable
fun ChatScreen(
    viewModel: SifatViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val messages by viewModel.chatMessages.collectAsStateWithLifecycle()
    val assistantState by viewModel.assistantState.collectAsStateWithLifecycle()
    val voiceState by viewModel.voiceRecognitionState.collectAsStateWithLifecycle()
    val accessibilityConnected by viewModel.accessibilityConnected.collectAsStateWithLifecycle()

    var inputText by remember { mutableStateOf("") }
    var includeScreenContext by remember { mutableStateOf(false) }
    var showClearDialog by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    val micLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.refreshPermissions()
        if (granted) viewModel.startVoiceCapture()
    }

    LaunchedEffect(messages.size, assistantState) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Delete Chat History?") },
            text = { Text("All locally stored conversation messages with Sifat Agent will be permanently erased.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearChatHistory()
                        showClearDialog = false
                    }
                ) {
                    Text("Delete All", color = DangerCoral)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) { Text("Cancel") }
            }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .imePadding(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(modifier = Modifier.widthIn(max = 780.dp).fillMaxSize()) {
            // Top bar
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                        Column {
                            Text("Sifat Chat", style = MaterialTheme.typography.titleLarge)
                            Text(
                                text = "Bangla & English • Safe Action Cards",
                                style = MaterialTheme.typography.labelSmall,
                                color = ElectricCyan
                            )
                        }
                    }
                    IconButton(
                        onClick = { showClearDialog = true },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("clear_chat_button")
                    ) {
                        Icon(
                            Icons.Filled.DeleteOutline,
                            contentDescription = "Delete chat history",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (assistantState is AssistantUiState.Processing ||
                assistantState is AssistantUiState.ExecutingStep
            ) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = ElectricCyan
                )
            }

            // Messages list
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (messages.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 48.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "Assalamu Alaikum! Ask Sifat Agent anything.",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.onBackground,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = "Try: \"Open YouTube\", \"বাংলাদেশ সম্পর্কে কিছু বলো\", or \"Dial +8801700000000\"",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }

                items(messages, key = { it.id }) { msg ->
                    ChatMessageItem(
                        message = msg,
                        onSpeak = { viewModel.speakText(msg.content, msg.language) },
                        onDelete = { viewModel.deleteChatMessage(msg.id) },
                        onConfirmPlan = { plan -> viewModel.confirmActionPlan(msg.id, plan) },
                        onCancelPlan = { plan -> viewModel.cancelActionPlan(msg.id, plan) }
                    )
                }

                if (assistantState is AssistantUiState.Streaming) {
                    item(key = "streaming_bubble") {
                        val text = (assistantState as AssistantUiState.Streaming).partialText
                        AssistantBubble(
                            text = text,
                            onSpeak = {},
                            onDelete = null
                        )
                    }
                }

                if (assistantState is AssistantUiState.ExecutingStep) {
                    val stepState = assistantState as AssistantUiState.ExecutingStep
                    item(key = "executing_step") {
                        Surface(
                            color = NeonViolet.copy(alpha = 0.16f),
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, NeonViolet.copy(alpha = 0.5f))
                        ) {
                            Text(
                                text = "Executing step ${stepState.stepIndex}/${stepState.totalSteps}: ${stepState.label}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                }
            }

            // Bottom composer
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 4.dp
            ) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                    if (accessibilityConnected) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(bottom = 6.dp)
                        ) {
                            FilterChip(
                                selected = includeScreenContext,
                                onClick = { includeScreenContext = !includeScreenContext },
                                leadingIcon = {
                                    Icon(
                                        Icons.Filled.RemoveRedEye,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                label = {
                                    Text(
                                        text = if (includeScreenContext) {
                                            "Screen context attached for next message"
                                        } else {
                                            "Include active screen text (opt-in)"
                                        },
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = inputText,
                            onValueChange = { inputText = it },
                            placeholder = { Text("Ask in English or বাংলা...") },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("chat_input_field"),
                            maxLines = 4,
                            shape = RoundedCornerShape(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        IconButton(
                            onClick = {
                                if (voiceState is VoiceRecognitionState.Listening) {
                                    viewModel.stopVoiceCapture()
                                } else {
                                    val granted = ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.RECORD_AUDIO
                                    ) == PackageManager.PERMISSION_GRANTED
                                    if (granted) viewModel.startVoiceCapture()
                                    else micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(
                                    if (voiceState is VoiceRecognitionState.Listening) {
                                        DangerCoral.copy(alpha = 0.2f)
                                    } else {
                                        MaterialTheme.colorScheme.surfaceContainerHigh
                                    }
                                )
                                .testTag("chat_mic_button")
                        ) {
                            Icon(
                                imageVector = if (voiceState is VoiceRecognitionState.Listening) {
                                    Icons.Filled.Stop
                                } else {
                                    Icons.Filled.Mic
                                },
                                contentDescription = "Voice input",
                                tint = if (voiceState is VoiceRecognitionState.Listening) DangerCoral else ElectricCyan
                            )
                        }
                        Spacer(Modifier.width(6.dp))
                        IconButton(
                            onClick = {
                                if (inputText.isNotBlank()) {
                                    viewModel.sendUserPrompt(inputText, includeScreenContext)
                                    inputText = ""
                                    includeScreenContext = false
                                }
                            },
                            enabled = inputText.isNotBlank(),
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.linearGradient(listOf(ElectricCyan, ElectricBlue))
                                )
                                .testTag("chat_send_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Send message",
                                tint = Color.White
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatMessageItem(
    message: ChatMessageEntity,
    onSpeak: () -> Unit,
    onDelete: () -> Unit,
    onConfirmPlan: (com.example.automation.ActionPlan) -> Unit,
    onCancelPlan: (com.example.automation.ActionPlan) -> Unit
) {
    val isUser = message.sender == "USER"
    val parsedPlan = remember(message.actionPlanJson) {
        message.actionPlanJson?.let { ActionValidator.deserializePlanFromJson(it) }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        if (isUser) {
            Surface(
                color = ElectricBlue.copy(alpha = 0.85f),
                shape = RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp),
                modifier = Modifier.widthIn(max = 520.dp)
            ) {
                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                )
            }
        } else {
            AssistantBubble(
                text = message.content,
                onSpeak = onSpeak,
                onDelete = onDelete
            )
            if (parsedPlan != null) {
                Spacer(Modifier.height(8.dp))
                ActionPlanConfirmationCard(
                    plan = parsedPlan,
                    status = message.actionStatus,
                    onConfirm = { onConfirmPlan(parsedPlan) },
                    onCancel = { onCancelPlan(parsedPlan) },
                    modifier = Modifier.widthIn(max = 520.dp)
                )
            }
        }
    }
}

@Composable
private fun AssistantBubble(
    text: String,
    onSpeak: () -> Unit,
    onDelete: (() -> Unit)?
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(18.dp, 18.dp, 18.dp, 4.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.widthIn(max = 520.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(6.dp))
            Row(
                horizontalArrangement = Arrangement.End,
                modifier = Modifier.fillMaxWidth()
            ) {
                IconButton(
                    onClick = onSpeak,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Filled.VolumeUp,
                        contentDescription = "Read aloud",
                        tint = ElectricCyan,
                        modifier = Modifier.size(18.dp)
                    )
                }
                if (onDelete != null) {
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Filled.DeleteOutline,
                            contentDescription = "Delete message",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}
