package com.example.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.SifatViewModel
import com.example.ui.theme.CautionAmber
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.SafeEmerald

@Composable
fun PermissionSetupScreen(
    viewModel: SifatViewModel,
    onBack: () -> Unit,
    onOpenPrivacyPolicy: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val status by viewModel.permissionSnapshot.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.refreshPermissions()
    }

    val micLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { viewModel.refreshPermissions() }

    val notifLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { viewModel.refreshPermissions() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(modifier = Modifier.widthIn(max = 760.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Column {
                    Text("Permissions & Safety Setup", style = MaterialTheme.typography.headlineMedium)
                    Text(
                        text = "Every permission is opt-in and explained transparently",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            PermissionCard(
                title = "Microphone (RECORD_AUDIO)",
                icon = Icons.Filled.Mic,
                granted = status.microphoneGranted,
                explanation = "Required only when you tap the microphone or explicitly opt into Wake-Word listening. Sifat Agent never records audio secretly.",
                actionLabel = if (status.microphoneGranted) "Granted" else "Grant Microphone",
                testTag = "grant_mic_button",
                onAction = { micLauncher.launch(Manifest.permission.RECORD_AUDIO) }
            )

            PermissionCard(
                title = "Foreground Notification (POST_NOTIFICATIONS)",
                icon = Icons.Filled.Notifications,
                granted = status.notificationsGranted,
                explanation = "Required on Android 13+ so opt-in 'Hey Sifat' wake-word listening always displays a visible notification with a Stop button.",
                actionLabel = if (status.notificationsGranted) "Granted" else "Allow Notifications",
                testTag = "grant_notif_button",
                onAction = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
            )

            PermissionCard(
                title = "Accessibility Service (Manual Opt-In)",
                icon = Icons.Filled.AccessibilityNew,
                granted = status.accessibilityConnected,
                explanation = "Allows Go Home, Go Back, scrolling, clicking visible buttons, and reading non-sensitive screen labels when requested. Android requires you to enable this manually in System Settings—we never enable it automatically, and banking/password screens are blocked.",
                actionLabel = if (status.accessibilityConnected) "Manage in Settings" else "Open Accessibility Settings",
                testTag = "open_accessibility_settings_button",
                forceEnabled = true,
                onAction = {
                    context.startActivity(viewModel.automationExecutor.openAccessibilitySettingsIntent())
                }
            )

            PermissionCard(
                title = "Modify System Settings (Brightness)",
                icon = Icons.Filled.Brightness6,
                granted = status.writeSettingsGranted,
                explanation = "Optional special permission used solely to adjust display brightness when you ask Sifat Agent to change brightness.",
                actionLabel = if (status.writeSettingsGranted) "Manage Brightness Access" else "Grant Write Settings",
                testTag = "open_write_settings_button",
                forceEnabled = true,
                onAction = {
                    context.startActivity(viewModel.automationExecutor.openWriteSettingsIntent())
                }
            )

            Spacer(Modifier.height(8.dp))

            OutlinedButton(
                onClick = onOpenPrivacyPolicy,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("permissions_privacy_button"),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Filled.PrivacyTip, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Read Full Privacy & Security Policy")
            }
        }
    }
}

@Composable
private fun PermissionCard(
    title: String,
    icon: ImageVector,
    granted: Boolean,
    explanation: String,
    actionLabel: String,
    testTag: String,
    forceEnabled: Boolean = false,
    onAction: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        border = BorderStroke(
            1.dp,
            if (granted) SafeEmerald.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (granted) SafeEmerald else ElectricCyan
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(title, style = MaterialTheme.typography.titleMedium)
                }
                Surface(
                    color = (if (granted) SafeEmerald else CautionAmber).copy(alpha = 0.16f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        if (granted) {
                            Icon(
                                Icons.Filled.CheckCircle,
                                contentDescription = null,
                                tint = SafeEmerald,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                        }
                        Text(
                            text = if (granted) "ACTIVE" else "REQUIRED",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (granted) SafeEmerald else CautionAmber
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            Text(
                text = explanation,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onAction,
                enabled = !granted || forceEnabled,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (granted) MaterialTheme.colorScheme.surfaceContainerHigh else ElectricCyan,
                    contentColor = if (granted) MaterialTheme.colorScheme.onSurface else Color(0xFF050814)
                ),
                modifier = Modifier.testTag(testTag)
            ) {
                Text(actionLabel)
            }
        }
    }
}
