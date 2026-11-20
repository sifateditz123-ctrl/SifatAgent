package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.ui.AssistantUiState
import com.example.ui.SifatViewModel
import com.example.ui.components.AnimatedAiOrb
import com.example.ui.components.OrbVisualMode
import com.example.ui.components.VoiceWaveformBars
import com.example.ui.theme.CautionAmber
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.NeonViolet
import com.example.ui.theme.SafeEmerald
import com.example.voice.VoiceRecognitionState

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    viewModel: SifatViewModel,
    onNavigateChat: () -> Unit,
    onNavigateVoice: () -> Unit,
    onNavigateAutomation: () -> Unit,
    onNavigatePermissions: () -> Unit,
    onNavigateSettings: () -> Unit,
    onNavigateHistory: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val prefs by viewModel.preferences.collectAsStateWithLifecycle()
    val voiceState by viewModel.voiceRecognitionState.collectAsStateWithLifecycle()
    val assistantState by viewModel.assistantState.collectAsStateWithLifecycle()
    val isSpeaking by viewModel.isSpeaking.collectAsStateWithLifecycle()
    val wakeRunning by viewModel.wakeWordServiceRunning.collectAsStateWithLifecycle()
    val accessibilityConnected by viewModel.accessibilityConnected.collectAsStateWithLifecycle()
    val lastReply by viewModel.lastVoiceReply.collectAsStateWithLifecycle()

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.refreshPermissions()
        if (granted) {
            viewModel.startVoiceCapture()
        } else {
            onNavigatePermissions()
        }
    }

    val triggerTapToTalk: () -> Unit = {
        if (voiceState is VoiceRecognitionState.Listening) {
            viewModel.stopVoiceCapture()
        } else {
            val hasMic = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
            if (hasMic) {
                viewModel.startVoiceCapture()
            } else {
                micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    val orbMode = when {
        voiceState is VoiceRecognitionState.Listening -> OrbVisualMode.LISTENING
        assistantState is AssistantUiState.Processing ||
            assistantState is AssistantUiState.ExecutingStep ||
            voiceState is VoiceRecognitionState.Processing -> OrbVisualMode.PROCESSING
        isSpeaking -> OrbVisualMode.SPEAKING
        else -> OrbVisualMode.IDLE
    }

    val rms = (voiceState as? VoiceRecognitionState.Listening)?.rmsLevel ?: 0.15f

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.surface
                    )
                )
            )
    ) {
        val isWide = maxWidth >= 680.dp

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier.widthIn(max = 760.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top Brand Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "Sifat Agent",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = prefs.language.label,
                            style = MaterialTheme.typography.labelMedium,
                            color = ElectricCyan
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        IconButton(
                            onClick = onNavigatePermissions,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("home_permissions_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Shield,
                                contentDescription = "Permissions setup",
                                tint = if (accessibilityConnected) SafeEmerald else CautionAmber
                            )
                        }
                        IconButton(
                            onClick = onNavigateSettings,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("home_settings_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Settings,
                                contentDescription = "Settings",
                                tint = MaterialTheme.colorScheme.onBackground
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Status telemetry pills
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StatusPill(
                        label = if (accessibilityConnected) "Accessibility: Ready" else "Accessibility: Off",
                        color = if (accessibilityConnected) SafeEmerald else CautionAmber,
                        onClick = onNavigatePermissions
                    )
                    StatusPill(
                        label = if (wakeRunning) "Wake-Word: Hey Sifat ON" else "Wake-Word: Off",
                        color = if (wakeRunning) ElectricCyan else MaterialTheme.colorScheme.onSurfaceVariant,
                        onClick = onNavigateSettings
                    )
                    StatusPill(
                        label = if (prefs.automationMasterEnabled) "Safety Guard: Active" else "Automation: Paused",
                        color = if (prefs.automationMasterEnabled) NeonViolet else CautionAmber,
                        onClick = onNavigateAutomation
                    )
                }

                Spacer(Modifier.height(16.dp))

                // Futuristic Hero Orb + Microphone Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        Image(
                            painter = painterResource(id = R.drawable.img_assistant_banner),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            alpha = 0.22f,
                            modifier = Modifier.matchParentSize()
                        )
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            AnimatedAiOrb(
                                mode = orbMode,
                                rmsLevel = rms,
                                size = if (isWide) 220.dp else 190.dp,
                                onClick = triggerTapToTalk
                            )

                            Spacer(Modifier.height(10.dp))

                            if (voiceState is VoiceRecognitionState.Listening || isSpeaking) {
                                VoiceWaveformBars(
                                    active = true,
                                    rmsLevel = if (isSpeaking) 0.65f else rms
                                )
                            }

                            val statusHeadline = when (val vs = voiceState) {
                                is VoiceRecognitionState.Listening ->
                                    vs.partialText.ifBlank { "Listening... Speak in Bangla or English" }
                                is VoiceRecognitionState.Processing -> "Processing your request..."
                                is VoiceRecognitionState.Error -> vs.message
                                VoiceRecognitionState.Idle -> when (val asState = assistantState) {
                                    is AssistantUiState.Processing -> asState.statusLabel
                                    is AssistantUiState.ExecutingStep ->
                                        "Step ${asState.stepIndex}/${asState.totalSteps}: ${asState.label}"
                                    is AssistantUiState.Error -> asState.message
                                    else -> lastReply ?: "Tap the orb or microphone to speak or give a phone command."
                                }
                            }

                            Text(
                                text = statusHeadline,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 8.dp)
                            )

                            Spacer(Modifier.height(16.dp))

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Button(
                                    onClick = triggerTapToTalk,
                                    shape = RoundedCornerShape(16.dp),
                                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (voiceState is VoiceRecognitionState.Listening) {
                                            MaterialTheme.colorScheme.error
                                        } else {
                                            ElectricCyan
                                        },
                                        contentColor = Color(0xFF050814)
                                    ),
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("home_mic_button")
                                ) {
                                    Icon(
                                        imageVector = if (voiceState is VoiceRecognitionState.Listening) {
                                            Icons.Filled.Stop
                                        } else {
                                            Icons.Filled.Mic
                                        },
                                        contentDescription = "Microphone"
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = if (voiceState is VoiceRecognitionState.Listening) "Stop" else "Tap to Talk",
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Surface(
                                    onClick = onNavigateVoice,
                                    shape = RoundedCornerShape(16.dp),
                                    color = NeonViolet.copy(alpha = 0.2f),
                                    border = BorderStroke(1.dp, NeonViolet.copy(alpha = 0.6f)),
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("home_voice_hud_button")
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.AutoAwesome,
                                            contentDescription = "Open Voice HUD",
                                            tint = NeonViolet,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            text = "Voice HUD",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(18.dp))

                // Quick Bilingual Suggestions
                Text(
                    text = "Quick Bilingual Commands",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val suggestions = listOf(
                        "Open YouTube",
                        "Open Wi-Fi Settings",
                        "স্ক্রিন পড়ে শোনাও",
                        "বাংলায় একটি অনুপ্রেরণামূলক কথা বলো",
                        "Set brightness to 75%"
                    )
                    suggestions.forEach { sample ->
                        AssistChip(
                            onClick = {
                                viewModel.sendUserPrompt(sample)
                                onNavigateChat()
                            },
                            label = {
                                Text(
                                    text = sample,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                            )
                        )
                    }
                }

                Spacer(Modifier.height(20.dp))

                // Feature Navigation Cards
                Text(
                    text = "Command Center",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))

                val navItems = listOf(
                    NavTileData(
                        title = "AI Chat",
                        subtitle = "Bilingual chat & confirmed action cards",
                        icon = Icons.AutoMirrored.Filled.Chat,
                        accent = ElectricCyan,
                        tag = "nav_chat_card",
                        onClick = onNavigateChat
                    ),
                    NavTileData(
                        title = "Safe Automation",
                        subtitle = "Allow-listed phone controls & screen reader",
                        icon = Icons.Filled.Bolt,
                        accent = NeonViolet,
                        tag = "nav_automation_card",
                        onClick = onNavigateAutomation
                    ),
                    NavTileData(
                        title = "Command History",
                        subtitle = "Audit executed & blocked actions",
                        icon = Icons.Filled.History,
                        accent = SafeEmerald,
                        tag = "nav_history_card",
                        onClick = onNavigateHistory
                    ),
                    NavTileData(
                        title = "Permissions & Safety",
                        subtitle = "Mic, Accessibility & privacy controls",
                        icon = Icons.Filled.Security,
                        accent = CautionAmber,
                        tag = "nav_permissions_card",
                        onClick = onNavigatePermissions
                    )
                )

                navItems.chunked(2).forEach { pair ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        pair.forEach { item ->
                            HomeNavCard(
                                item = item,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }
            }
        }
    }
}

private data class NavTileData(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val accent: Color,
    val tag: String,
    val onClick: () -> Unit
)

@Composable
private fun HomeNavCard(item: NavTileData, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier
            .testTag(item.tag)
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = item.onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        border = BorderStroke(1.dp, item.accent.copy(alpha = 0.35f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(item.accent.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = item.icon,
                    contentDescription = item.title,
                    tint = item.accent
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = item.subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun StatusPill(label: String, color: Color, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = color.copy(alpha = 0.14f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.45f))
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}
