package com.example.ui.screens

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RemoveRedEye
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.automation.ActionType
import com.example.automation.ActionValidator
import com.example.automation.ValidatedAction
import com.example.ui.SifatViewModel
import com.example.ui.components.RiskLevelBadge
import com.example.ui.theme.CautionAmber
import com.example.ui.theme.DangerCoral
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.NeonViolet
import com.example.ui.theme.SafeEmerald
import org.json.JSONObject

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AutomationScreen(
    viewModel: SifatViewModel,
    onBack: () -> Unit,
    onOpenPermissions: () -> Unit,
    modifier: Modifier = Modifier
) {
    val prefs by viewModel.preferences.collectAsStateWithLifecycle()
    val accessibilityConnected by viewModel.accessibilityConnected.collectAsStateWithLifecycle()
    val installedApps by viewModel.installedApps.collectAsStateWithLifecycle()
    val screenSnapshot by viewModel.latestScreenSnapshot.collectAsStateWithLifecycle()
    val pendingCatalogAction by viewModel.pendingCatalogAction.collectAsStateWithLifecycle()

    var activeParameterDialog by remember { mutableStateOf<ActionType?>(null) }
    var paramPrimary by remember { mutableStateOf("") }
    var paramSecondary by remember { mutableStateOf("") }
    var brightnessSlider by remember { mutableFloatStateOf(70f) }

    // Confirmation preview dialog for sensitive actions
    pendingCatalogAction?.let { action ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissPendingCatalogAction() },
            title = { Text("Confirm ${action.actionType.displayName}") },
            text = {
                Column {
                    RiskLevelBadge(action.riskLevel)
                    Spacer(Modifier.height(10.dp))
                    Text(action.previewSummary, style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Permission: ${action.permissionRequirement.label}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.executeCatalogActionConfirmed(action) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ElectricCyan,
                        contentColor = Color(0xFF050814)
                    )
                ) {
                    Text("Confirm & Run")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissPendingCatalogAction() }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Parameter input modal for catalog testing
    activeParameterDialog?.let { type ->
        AlertDialog(
            onDismissRequest = { activeParameterDialog = null },
            title = { Text(type.displayName) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    when (type) {
                        ActionType.LAUNCH_APP -> {
                            OutlinedTextField(
                                value = paramPrimary,
                                onValueChange = { paramPrimary = it },
                                label = { Text("Installed App Name") },
                                placeholder = { Text("e.g., Settings, YouTube, Chrome") }
                            )
                        }
                        ActionType.OPEN_SETTINGS -> {
                            OutlinedTextField(
                                value = paramPrimary,
                                onValueChange = { paramPrimary = it },
                                label = { Text("Section (wifi, bluetooth, display, accessibility, general)") }
                            )
                        }
                        ActionType.CLICK_ELEMENT -> {
                            OutlinedTextField(
                                value = paramPrimary,
                                onValueChange = { paramPrimary = it },
                                label = { Text("Visible text / content description") }
                            )
                        }
                        ActionType.SCROLL_SCREEN -> {
                            OutlinedTextField(
                                value = paramPrimary,
                                onValueChange = { paramPrimary = it },
                                label = { Text("Direction (UP or DOWN)") }
                            )
                        }
                        ActionType.OPEN_URL -> {
                            OutlinedTextField(
                                value = paramPrimary,
                                onValueChange = { paramPrimary = it },
                                label = { Text("HTTPS URL") },
                                placeholder = { Text("https://example.com") }
                            )
                        }
                        ActionType.ADJUST_BRIGHTNESS -> {
                            Text("Brightness: ${brightnessSlider.toInt()}%")
                            Slider(
                                value = brightnessSlider,
                                onValueChange = { brightnessSlider = it },
                                valueRange = 10f..100f
                            )
                        }
                        ActionType.DIAL_NUMBER -> {
                            OutlinedTextField(
                                value = paramPrimary,
                                onValueChange = { paramPrimary = it },
                                label = { Text("Phone Number") },
                                placeholder = { Text("+8801700000000") }
                            )
                        }
                        ActionType.COMPOSE_SMS -> {
                            OutlinedTextField(
                                value = paramPrimary,
                                onValueChange = { paramPrimary = it },
                                label = { Text("Recipient Number") },
                                placeholder = { Text("+8801700000000") }
                            )
                            OutlinedTextField(
                                value = paramSecondary,
                                onValueChange = { paramSecondary = it },
                                label = { Text("SMS Message Body") }
                            )
                        }
                        else -> Text(type.description)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val json = JSONObject().apply {
                            put("action", type.wireName)
                            val p = JSONObject()
                            when (type) {
                                ActionType.LAUNCH_APP -> p.put("appName", paramPrimary)
                                ActionType.OPEN_SETTINGS -> p.put("section", paramPrimary.ifBlank { "general" })
                                ActionType.CLICK_ELEMENT -> p.put("targetText", paramPrimary)
                                ActionType.SCROLL_SCREEN -> p.put("direction", paramPrimary.ifBlank { "DOWN" })
                                ActionType.OPEN_URL -> p.put("url", paramPrimary)
                                ActionType.ADJUST_BRIGHTNESS -> p.put("levelPercent", brightnessSlider.toInt().toString())
                                ActionType.DIAL_NUMBER -> p.put("phoneNumber", paramPrimary)
                                ActionType.COMPOSE_SMS -> {
                                    p.put("phoneNumber", paramPrimary)
                                    p.put("messageBody", paramSecondary)
                                }
                                else -> Unit
                            }
                            put("parameters", p)
                        }
                        val validated = ActionValidator.validateSingleStep(json)
                        activeParameterDialog = null
                        if (validated != null) {
                            viewModel.requestCatalogAction(validated)
                        }
                    }
                ) {
                    Text("Validate & Preview")
                }
            },
            dismissButton = {
                TextButton(onClick = { activeParameterDialog = null }) { Text("Cancel") }
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
        Column(modifier = Modifier.widthIn(max = 780.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Column {
                    Text("Safe Automation", style = MaterialTheme.typography.headlineMedium)
                    Text(
                        text = "Allow-listed Android intents & Accessibility controls",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            // Master Automation Kill-Switch
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                ),
                border = BorderStroke(
                    1.dp,
                    if (prefs.automationMasterEnabled) SafeEmerald.copy(alpha = 0.5f) else DangerCoral.copy(alpha = 0.6f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Shield,
                            contentDescription = "Master switch",
                            tint = if (prefs.automationMasterEnabled) SafeEmerald else DangerCoral
                        )
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Master Automation Switch",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = if (prefs.automationMasterEnabled) {
                                    "Allow-listed actions active (confirmations enforced)"
                                } else {
                                    "All phone automation paused immediately"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Switch(
                        checked = prefs.automationMasterEnabled,
                        onCheckedChange = { viewModel.toggleMasterAutomation(it) },
                        modifier = Modifier.testTag("master_automation_switch")
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            // Screen Understanding Preview Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                ),
                border = BorderStroke(1.dp, NeonViolet.copy(alpha = 0.4f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.RemoveRedEye, contentDescription = null, tint = NeonViolet)
                            Spacer(Modifier.width(8.dp))
                            Text("Screen Understanding", style = MaterialTheme.typography.titleMedium)
                        }
                        Surface(
                            color = (if (accessibilityConnected) SafeEmerald else CautionAmber).copy(alpha = 0.16f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = if (accessibilityConnected) "ACCESSIBILITY ON" else "NOT ENABLED",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (accessibilityConnected) SafeEmerald else CautionAmber,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Sifat Agent reads visible UI labels ONLY when you tap Inspect or ask in Chat. Passwords, lock screens, permission dialogs, and banking apps are automatically excluded.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = { viewModel.inspectActiveScreenSafely() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = NeonViolet,
                                contentColor = Color.White
                            ),
                            modifier = Modifier.testTag("inspect_screen_button")
                        ) {
                            Text("Read Active Screen")
                        }
                        if (!accessibilityConnected) {
                            OutlinedButton(onClick = onOpenPermissions) {
                                Text("Enable Service")
                            }
                        }
                    }

                    screenSnapshot?.let { snap ->
                        Spacer(Modifier.height(12.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                if (snap.blockedReason != null) {
                                    Text(
                                        text = snap.blockedReason,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = CautionAmber
                                    )
                                } else {
                                    Text(
                                        text = "Package: ${snap.packageName}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = ElectricCyan
                                    )
                                    if (snap.visibleTexts.isNotEmpty()) {
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            text = "Visible: ${snap.visibleTexts.take(8).joinToString(" • ")}",
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }
                                    if (snap.suggestedNextActions.isNotEmpty()) {
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            text = "Suggested: ${snap.suggestedNextActions.joinToString(", ")}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = SafeEmerald
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (installedApps.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "Quick Launch Installed Apps (${installedApps.size})",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    installedApps.take(10).forEach { app ->
                        AssistChip(
                            onClick = {
                                viewModel.requestCatalogAction(
                                    ValidatedAction(
                                        actionType = ActionType.LAUNCH_APP,
                                        parameters = mapOf("appName" to app.label),
                                        previewSummary = "Launch ${app.label}",
                                        requiresConfirmation = false
                                    )
                                )
                            },
                            label = { Text(app.label) }
                        )
                    }
                }
            }

            Spacer(Modifier.height(18.dp))
            Text(
                text = "Allow-Listed Actions (${ActionType.entries.size})",
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(Modifier.height(10.dp))

            ActionType.entries.forEach { actionType ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
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
                                    Icons.Filled.Bolt,
                                    contentDescription = null,
                                    tint = ElectricCyan,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = actionType.displayName,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            RiskLevelBadge(actionType.riskLevel)
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = actionType.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Requires: ${actionType.permissionRequirement.label}",
                                style = MaterialTheme.typography.labelSmall,
                                color = NeonViolet,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedButton(
                                onClick = {
                                    when (actionType) {
                                        ActionType.GO_HOME,
                                        ActionType.GO_BACK,
                                        ActionType.READ_SCREEN_SUMMARY -> {
                                            viewModel.requestCatalogAction(
                                                ValidatedAction(
                                                    actionType = actionType,
                                                    parameters = emptyMap(),
                                                    previewSummary = actionType.displayName,
                                                    requiresConfirmation = actionType.alwaysConfirm
                                                )
                                            )
                                        }
                                        else -> {
                                            paramPrimary = when (actionType) {
                                                ActionType.OPEN_SETTINGS -> "wifi"
                                                ActionType.SCROLL_SCREEN -> "DOWN"
                                                ActionType.OPEN_URL -> "https://developer.android.com"
                                                else -> ""
                                            }
                                            paramSecondary = ""
                                            activeParameterDialog = actionType
                                        }
                                    }
                                },
                                modifier = Modifier.testTag("run_${actionType.wireName.lowercase()}")
                            ) {
                                Icon(
                                    Icons.Filled.PlayArrow,
                                    contentDescription = "Run ${actionType.displayName}",
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text("Run")
                            }
                        }
                    }
                }
            }
        }
    }
}
