package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.AiProviderMode
import com.example.data.local.AssistantLanguage
import com.example.data.local.ThemeMode
import com.example.ui.SifatViewModel
import com.example.ui.theme.CautionAmber
import com.example.ui.theme.DangerCoral
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.SafeEmerald

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    viewModel: SifatViewModel,
    onBack: () -> Unit,
    onOpenPrivacyPolicy: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val prefs by viewModel.preferences.collectAsStateWithLifecycle()
    val wakeRunning by viewModel.wakeWordServiceRunning.collectAsStateWithLifecycle()
    val banglaTtsOk by viewModel.banglaVoiceSupported.collectAsStateWithLifecycle()

    var backendUrlInput by remember(prefs.customBackendUrl) { mutableStateOf(prefs.customBackendUrl) }
    var backendTokenInput by remember { mutableStateOf("") }
    var backendSavedFeedback by remember { mutableStateOf(false) }
    var showResetConfirm by remember { mutableStateOf(false) }

    val wakePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        viewModel.refreshPermissions()
        val micGranted = results[Manifest.permission.RECORD_AUDIO] == true ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (micGranted) {
            viewModel.toggleWakeWord(true)
        }
    }

    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            title = { Text("Delete All Local Data?") },
            text = { Text("This clears all chat messages, command history, encrypted backend tokens, and resets settings.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.resetAllData()
                        showResetConfirm = false
                    }
                ) {
                    Text("Reset Everything", color = DangerCoral)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirm = false }) { Text("Cancel") }
            }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier = Modifier.widthIn(max = 760.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Text("Settings", style = MaterialTheme.typography.headlineMedium)
            }

            // Theme Card
            SettingsSectionCard(title = "Appearance & Theme") {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ThemeMode.entries.forEach { mode ->
                        FilterChip(
                            selected = prefs.themeMode == mode,
                            onClick = { viewModel.setThemeMode(mode) },
                            label = { Text(mode.label) },
                            modifier = Modifier.testTag("theme_${mode.name.lowercase()}")
                        )
                    }
                }
            }

            // Language Card
            SettingsSectionCard(title = "Bilingual Language") {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AssistantLanguage.entries.forEach { lang ->
                        FilterChip(
                            selected = prefs.language == lang,
                            onClick = { viewModel.setLanguage(lang) },
                            label = { Text(lang.label) }
                        )
                    }
                }
            }

            // Voice & Wake-word Card
            SettingsSectionCard(title = "Voice, Speech & Wake-Word") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Speak AI Responses (TTS)", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = if (banglaTtsOk) {
                                "Bangla & English TextToSpeech ready"
                            } else {
                                "English TTS ready (install Bangla voice data in Android TTS settings for Bangla audio)"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (banglaTtsOk) MaterialTheme.colorScheme.onSurfaceVariant else CautionAmber
                        )
                    }
                    Switch(
                        checked = prefs.ttsEnabled,
                        onCheckedChange = { viewModel.setTtsEnabled(it) },
                        modifier = Modifier.testTag("tts_toggle_switch")
                    )
                }

                Spacer(Modifier.height(10.dp))
                Text(
                    text = "Voice Speech Rate: ${"%.1f".format(prefs.ttsSpeechRate)}x",
                    style = MaterialTheme.typography.bodyMedium
                )
                Slider(
                    value = prefs.ttsSpeechRate,
                    onValueChange = { viewModel.setTtsSpeechRate(it) },
                    valueRange = 0.7f..1.6f
                )

                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Wake-Word Mode (\"Hey Sifat\")", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = if (wakeRunning) {
                                "Foreground service active with visible notification"
                            } else {
                                "Opt-in only. Shows a persistent notification when active."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = prefs.wakeWordEnabled,
                        onCheckedChange = { wantEnable ->
                            if (wantEnable) {
                                val perms = buildList {
                                    add(Manifest.permission.RECORD_AUDIO)
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                        add(Manifest.permission.POST_NOTIFICATIONS)
                                    }
                                }
                                val allGranted = perms.all {
                                    ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
                                }
                                if (allGranted) {
                                    viewModel.toggleWakeWord(true)
                                } else {
                                    wakePermissionLauncher.launch(perms.toTypedArray())
                                }
                            } else {
                                viewModel.toggleWakeWord(false)
                            }
                        },
                        modifier = Modifier.testTag("wake_word_switch")
                    )
                }
            }

            // AI Provider Card
            SettingsSectionCard(title = "AI Provider & Secure Backend") {
                AiProviderMode.entries.forEach { mode ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = prefs.aiProviderMode == mode,
                            onClick = { viewModel.setAiProviderMode(mode) }
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(mode.label, style = MaterialTheme.typography.titleMedium)
                            Text(
                                mode.subtitle,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                }

                if (prefs.aiProviderMode == AiProviderMode.CUSTOM_HTTPS_BACKEND) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = backendUrlInput,
                        onValueChange = {
                            backendUrlInput = it
                            backendSavedFeedback = false
                        },
                        label = { Text("HTTPS Backend URL") },
                        placeholder = { Text("https://sifat-proxy.yourdomain.com") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = backendTokenInput,
                        onValueChange = {
                            backendTokenInput = it
                            backendSavedFeedback = false
                        },
                        label = {
                            Text(
                                if (prefs.hasCustomBackendToken) {
                                    "Bearer Token (Encrypted in Keystore — enter to replace)"
                                } else {
                                    "Bearer Token (Encrypted in Android Keystore)"
                                }
                            )
                        },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = {
                            viewModel.saveCustomBackendConfig(
                                url = backendUrlInput,
                                token = backendTokenInput.ifBlank { null }
                            )
                            backendTokenInput = ""
                            backendSavedFeedback = true
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ElectricCyan,
                            contentColor = Color(0xFF050814)
                        )
                    ) {
                        Text("Save Backend Config")
                    }
                    if (backendSavedFeedback) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Saved securely.",
                            style = MaterialTheme.typography.labelMedium,
                            color = SafeEmerald
                        )
                    }
                }
            }

            // Privacy & Data Controls
            SettingsSectionCard(title = "Privacy & Data Controls") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = { viewModel.clearChatHistory() },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Clear Chat")
                    }
                    OutlinedButton(
                        onClick = { viewModel.clearCommandHistory() },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Clear History")
                    }
                }
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = onOpenPrivacyPolicy,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("View Privacy Policy")
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { showResetConfirm = true },
                    border = BorderStroke(1.dp, DangerCoral.copy(alpha = 0.6f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Reset All Local Data", color = DangerCoral)
                }
            }
        }
    }
}

@Composable
private fun SettingsSectionCard(
    title: String,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = ElectricCyan
            )
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}
