package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.automation.ActionPlan
import com.example.automation.RiskLevel
import com.example.automation.ValidatedAction
import com.example.ui.theme.CautionAmber
import com.example.ui.theme.DangerCoral
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.NeonViolet
import com.example.ui.theme.SafeEmerald

@Composable
fun RiskLevelBadge(riskLevel: RiskLevel, modifier: Modifier = Modifier) {
    val (bg, fg) = when (riskLevel) {
        RiskLevel.LOW -> SafeEmerald.copy(alpha = 0.16f) to SafeEmerald
        RiskLevel.MEDIUM -> CautionAmber.copy(alpha = 0.18f) to CautionAmber
        RiskLevel.HIGH -> DangerCoral.copy(alpha = 0.18f) to DangerCoral
    }
    Surface(
        color = bg,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, fg.copy(alpha = 0.4f)),
        modifier = modifier
    ) {
        Text(
            text = riskLevel.label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = fg,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

@Composable
fun ActionPlanConfirmationCard(
    plan: ActionPlan,
    status: String?,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isPending = status == "PENDING_CONFIRMATION"
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        border = BorderStroke(
            1.dp,
            when (status) {
                "COMPLETED" -> SafeEmerald.copy(alpha = 0.6f)
                "CANCELLED", "FAILED" -> DangerCoral.copy(alpha = 0.5f)
                else -> ElectricCyan.copy(alpha = 0.5f)
            }
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Security,
                        contentDescription = "Action authorization",
                        tint = ElectricCyan,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Action Plan (${plan.steps.size} step${if (plan.steps.size > 1) "s" else ""})",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                if (status != null) {
                    StatusChip(status = status)
                }
            }

            Spacer(Modifier.height(10.dp))

            plan.steps.forEachIndexed { idx, step ->
                StepPreviewRow(index = idx + 1, step = step)
                if (idx < plan.steps.lastIndex) {
                    Spacer(Modifier.height(8.dp))
                }
            }

            if (isPending) {
                Spacer(Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onCancel,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("cancel_action_button"),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = "Cancel",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Cancel")
                    }
                    Button(
                        onClick = onConfirm,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("confirm_action_button"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ElectricCyan,
                            contentColor = Color(0xFF050814)
                        )
                    ) {
                        Icon(
                            Icons.Filled.CheckCircle,
                            contentDescription = "Confirm action",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Confirm", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun StepPreviewRow(index: Int, step: ValidatedAction) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.surface.copy(alpha = 0.65f),
                RoundedCornerShape(12.dp)
            )
            .padding(10.dp)
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "$index. ${step.actionType.displayName}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                RiskLevelBadge(step.riskLevel)
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = step.previewSummary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Requires: ${step.permissionRequirement.label}",
                style = MaterialTheme.typography.labelSmall,
                color = NeonViolet
            )
        }
    }
}

@Composable
fun StatusChip(status: String) {
    val (color, icon) = when (status) {
        "COMPLETED", "SUCCESS" -> SafeEmerald to Icons.Filled.CheckCircle
        "PENDING_CONFIRMATION" -> CautionAmber to Icons.Filled.Warning
        "CANCELLED", "DENIED", "FAILED" -> DangerCoral to Icons.Filled.Shield
        else -> ElectricCyan to Icons.Filled.Security
    }
    Surface(
        color = color.copy(alpha = 0.16f),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(12.dp))
            Spacer(Modifier.width(4.dp))
            Text(
                text = status.replace("_", " "),
                style = MaterialTheme.typography.labelSmall,
                color = color
            )
        }
    }
}
