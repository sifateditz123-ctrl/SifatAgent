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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.AssistantLanguage
import com.example.ui.AssistantUiState
import com.example.ui.SifatViewModel
import com.example.ui.components.AnimatedAiOrb
import com.example.ui.components.OrbVisualMode
import com.example.ui.components.VoiceWaveformBars
import com.example.ui.theme.CautionAmber
import com.example.ui.theme.DangerCoral
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.NeonViolet
import com.example.voice.VoiceRecognitionState

@Composable
fun VoiceAssistantScreen(
    viewModel: SifatViewModel,
    onBack: () -> Unit,
    onOpenChat: () -> Unit,
    onOpenPermissions: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val prefs by viewModel.preferences.collectAsStateWithLifecycle()
    val voiceState by viewModel.voiceRecognitionState.collectAsStateWithLifecycle()
    val assistantState by viewModel.assistantState.collectAsStateWithLifecycle()
    val isSpeaking by viewModel.isSpeaking.collectAsStateWithLifecycle()
    val lastReply by viewModel.lastVoiceReply.collectAsStateWithLifecycle()

    val micLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.refreshPermissions()
        if (granted) {
            viewModel.startVoiceCapture()
        } else {
            onOpenPermissions()
        }
    }

    val startOrStopMic: () -> Unit = {
        if (voiceState is VoiceRecognitionState.Listening) {
            viewModel.stopVoiceCapture()
        } else {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
            if (granted) {
                viewModel.startVoiceCapture()
            } else {
                micLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    val orbMode = when {
        voiceState is VoiceRecognitionState.Listening -> OrbVisualMode.LISTENING
        voiceState is VoiceRecognitionState.Processing ||
            assistantState is AssistantUiState.Processing ||
            assistantState is AssistantUiState.ExecutingStep -> OrbVisualMode.PROCESSING
        isSpeaking -> OrbVisualMode.SPEAKING
        else -> OrbVisualMode.IDLE
    }

    val rms = (voiceState as? VoiceRecognitionState.Listening)?.rmsLevel ?: 0.2f

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        NeonViolet.copy(alpha = 0.14f),
                        MaterialTheme.colorScheme.background
                    )
                )
            )
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier = Modifier.widthIn(max = 640.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back"
                    )
                }
                Text(
                    text = "Voice Assistant",
                    style = MaterialTheme.typography.titleLarge
                )
                IconButton(
                    onClick = onOpenChat,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Chat,
                        contentDescription = "Open text chat",
                        tint = ElectricCyan
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            // Language switcher chips
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AssistantLanguage.entries.forEach { lang ->
                    FilterChip(
                        selected = prefs.language == lang,
                        onClick = { viewModel.setLanguage(lang) },
                        label = {
                            Text(
                                text = when (lang) {
                                    AssistantLanguage.AUTO -> "Auto"
                                    AssistantLanguage.ENGLISH -> "English"
                                    AssistantLanguage.BANGLA -> "বাংলা"
                                }
                            )
                        }
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // Active state badge
            val stateLabel = when {
                voiceState is VoiceRecognitionState.Listening -> "MICROPHONE ACTIVE • LISTENING"
                voiceState is VoiceRecognitionState.Processing ||
                    assistantState is AssistantUiState.Processing -> "PROCESSING WITH GEMINI"
                assistantState is AssistantUiState.ExecutingStep -> "EXECUTING SAFE ACTION"
                isSpeaking -> "SPEAKING RESPONSE"
                voiceState is VoiceRecognitionState.Error -> "SPEECH SERVICE NOTICE"
                else -> "READY FOR VOICE COMMAND"
            }
            val badgeColor = when {
                voiceState is VoiceRecognitionState.Listening -> DangerCoral
                isSpeaking -> NeonViolet
                voiceState is VoiceRecognitionState.Error -> CautionAmber
                else -> ElectricCyan
            }

            Surface(
                shape = RoundedCornerShape(50),
                color = badgeColor.copy(alpha = 0.16f),
                border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.6f))
            ) {
                Text(
                    text = stateLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = badgeColor,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                )
            }

            Spacer(Modifier.height(20.dp))

            AnimatedAiOrb(
                mode = orbMode,
                rmsLevel = rms,
                size = 228.dp,
                onClick = startOrStopMic
            )

            Spacer(Modifier.height(18.dp))

            VoiceWaveformBars(
                active = voiceState is VoiceRecognitionState.Listening || isSpeaking,
                rmsLevel = if (isSpeaking) 0.7f else rms
            )

            Spacer(Modifier.height(16.dp))

            // Live transcript / response card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val bodyText = when (val vs = voiceState) {
                        is VoiceRecognitionState.Listening ->
                            vs.partialText.ifBlank { "Listening... Try \"Open YouTube\" or \"হোম স্ক্রিনে যাও\"" }
                        is VoiceRecognitionState.Processing -> "Analyzing intent and validating safety..."
                        is VoiceRecognitionState.Error -> vs.message
                        VoiceRecognitionState.Idle -> when (val asState = assistantState) {
                            is AssistantUiState.Processing -> asState.statusLabel
                            is AssistantUiState.Streaming -> asState.partialText
                            is AssistantUiState.ExecutingStep ->
                                "Step ${asState.stepIndex}/${asState.totalSteps}: ${asState.label}"
                            is AssistantUiState.Error -> asState.message
                            AssistantUiState.Idle -> lastReply ?: "Tap the microphone below to talk in Bangla or English."
                        }
                    }

                    Text(
                        text = bodyText,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(Modifier.height(22.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = startOrStopMic,
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (voiceState is VoiceRecognitionState.Listening) {
                            DangerCoral
                        } else {
                            ElectricCyan
                        },
                        contentColor = Color(0xFF050814)
                    ),
                    modifier = Modifier
                        .height(54.dp)
                        .testTag("voice_screen_mic_button")
                ) {
                    Icon(
                        imageVector = if (voiceState is VoiceRecognitionState.Listening) {
                            Icons.Filled.Stop
                        } else {
                            Icons.Filled.Mic
                        },
                        contentDescription = "Microphone toggle"
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (voiceState is VoiceRecognitionState.Listening) {
                            "Stop Listening"
                        } else {
                            "Start Listening"
                        },
                        fontWeight = FontWeight.Bold
                    )
                }

                if (isSpeaking) {
                    OutlinedButton(
                        onClick = { viewModel.stopSpeaking() },
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier
                            .height(54.dp)
                            .testTag("stop_tts_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.VolumeOff,
                            contentDescription = "Stop speaking",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Mute")
                    }
                }
            }

            if (!viewModel.isSpeechRecognitionAvailable()) {
                Spacer(Modifier.height(14.dp))
                Text(
                    text = "Android SpeechRecognizer service is not installed on this device. Use Text Chat for full bilingual AI assistance.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = CautionAmber,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
