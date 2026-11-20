package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.ui.theme.ElectricCyan

@Composable
fun PrivacyPolicyScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
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
                Text("Privacy & Security Policy", style = MaterialTheme.typography.headlineMedium)
            }

            Spacer(Modifier.height(14.dp))

            val sections = listOf(
                "1. Microphone & Speech Recognition" to
                    "Sifat Agent activates your microphone only when you press Tap-to-Talk or explicitly opt into 'Hey Sifat' wake-word mode. Opt-in wake-word listening always runs as an Android Foreground Service with a visible ongoing notification and a one-tap Stop button. Raw audio is never silently recorded or stored by Sifat Agent.",
                "2. Accessibility Service Boundaries" to
                    "You must enable Sifat Accessibility Service manually in Android Settings. Sifat Agent reads visible UI nodes ONLY when you explicitly request screen reading or element interaction. Password fields (isPassword), lock screens, permission controllers, and sensitive banking/wallet apps are automatically skipped and blocked.",
                "3. Network & AI Processing" to
                    "Prompts are sent over encrypted HTTPS either through Firebase AI Logic (attested via Firebase App Check, with zero API keys inside the APK) or your self-hosted HTTPS backend proxy configured in Settings. Sifat Agent does not collect analytics, sell data, or upload screenshots automatically.",
                "4. Local Storage & Credential Protection" to
                    "Chat messages and command audit logs are stored locally on your device using Room SQLite and can be deleted individually or cleared completely at any time. Any optional custom backend token is encrypted with AES-256-GCM via Android Keystore.",
                "5. Allow-Listed Safe Automation" to
                    "Sifat Agent enforces a strict 11-action allow-list. Calls, SMS, URLs, brightness changes, and UI clicks require explicit user preview and confirmation. Financial transactions, arbitrary code execution, file wiping, and security bypasses are blocked."
            )

            sections.forEach { (heading, body) ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(heading, style = MaterialTheme.typography.titleMedium, color = ElectricCyan)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            body,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
