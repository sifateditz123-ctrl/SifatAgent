package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.NeonViolet
import com.example.ui.theme.RadiantViolet
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

enum class OrbVisualMode {
    IDLE,
    LISTENING,
    PROCESSING,
    SPEAKING
}

@Composable
fun AnimatedAiOrb(
    mode: OrbVisualMode,
    rmsLevel: Float = 0.2f,
    size: Dp = 210.dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "sifat_orb")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when (mode) {
                    OrbVisualMode.PROCESSING -> 2600
                    OrbVisualMode.LISTENING -> 4500
                    OrbVisualMode.SPEAKING -> 3800
                    OrbVisualMode.IDLE -> 9000
                },
                easing = LinearEasing
            )
        ),
        label = "orb_rotation"
    )
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "orb_pulse"
    )

    val dynamicScale = when (mode) {
        OrbVisualMode.LISTENING -> 1f + (rmsLevel.coerceIn(0f, 1f) * 0.22f)
        OrbVisualMode.SPEAKING -> pulse
        OrbVisualMode.PROCESSING -> 1.03f
        OrbVisualMode.IDLE -> 0.96f + (pulse - 0.92f) * 0.35f
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .testTag("ai_orb_button")
            .clip(CircleShape)
            .clickable(onClick = onClick)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(this.size.width / 2f, this.size.height / 2f)
            val baseRadius = (this.size.minDimension / 2f) * 0.62f * dynamicScale

            // Outer ambient radial halo
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        ElectricCyan.copy(alpha = if (mode == OrbVisualMode.LISTENING) 0.40f else 0.24f),
                        NeonViolet.copy(alpha = 0.14f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = this.size.minDimension / 2f
                ),
                radius = this.size.minDimension / 2f,
                center = center
            )

            // Rotating plasma orbital rings
            val ringRadius = baseRadius * 1.16f
            val arcAngle = rotation
            drawArc(
                brush = Brush.sweepGradient(
                    colors = listOf(ElectricCyan, NeonViolet, RadiantViolet, Color.Transparent, ElectricCyan),
                    center = center
                ),
                startAngle = arcAngle,
                sweepAngle = 220f,
                useCenter = false,
                topLeft = Offset(center.x - ringRadius, center.y - ringRadius),
                size = Size(ringRadius * 2f, ringRadius * 2f),
                style = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round)
            )

            val counterRadius = baseRadius * 1.04f
            drawArc(
                brush = Brush.sweepGradient(
                    colors = listOf(NeonViolet.copy(alpha = 0.7f), ElectricBlue, Color.Transparent, NeonViolet),
                    center = center
                ),
                startAngle = -arcAngle * 1.3f,
                sweepAngle = 170f,
                useCenter = false,
                topLeft = Offset(center.x - counterRadius, center.y - counterRadius),
                size = Size(counterRadius * 2f, counterRadius * 2f),
                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
            )

            // Core glowing neural sphere
            drawCircle(
                brush = Brush.linearGradient(
                    colors = listOf(ElectricCyan, ElectricBlue, NeonViolet),
                    start = Offset(center.x - baseRadius, center.y - baseRadius),
                    end = Offset(center.x + baseRadius, center.y + baseRadius)
                ),
                radius = baseRadius * 0.86f,
                center = center
            )

            // Orbiting energy particles
            for (i in 0 until 6) {
                val theta = ((rotation + i * 60f) * PI / 180.0)
                val orbitR = baseRadius * 1.24f
                val px = center.x + (cos(theta) * orbitR).toFloat()
                val py = center.y + (sin(theta) * orbitR).toFloat()
                drawCircle(
                    color = if (i % 2 == 0) ElectricCyan else RadiantViolet,
                    radius = if (mode == OrbVisualMode.LISTENING) 4.dp.toPx() else 2.6.dp.toPx(),
                    center = Offset(px, py)
                )
            }
        }

        // Center crisp icon
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(64.dp)
                .minimumInteractiveComponentSize()
                .shadow(12.dp, CircleShape, ambientColor = ElectricCyan, spotColor = NeonViolet)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        listOf(
                            Color(0xFF0A142D).copy(alpha = 0.85f),
                            Color(0xFF1A103D).copy(alpha = 0.85f)
                        )
                    )
                )
                .border(1.2.dp, ElectricCyan.copy(alpha = 0.7f), CircleShape)
        ) {
            Icon(
                imageVector = when (mode) {
                    OrbVisualMode.LISTENING -> Icons.Filled.Stop
                    OrbVisualMode.SPEAKING -> Icons.Filled.GraphicEq
                    OrbVisualMode.PROCESSING -> Icons.Filled.GraphicEq
                    OrbVisualMode.IDLE -> Icons.Filled.Mic
                },
                contentDescription = when (mode) {
                    OrbVisualMode.LISTENING -> "Stop listening"
                    OrbVisualMode.SPEAKING -> "Sifat Agent speaking"
                    OrbVisualMode.PROCESSING -> "Sifat Agent processing"
                    OrbVisualMode.IDLE -> "Tap to speak to Sifat Agent"
                },
                tint = Color.White,
                modifier = Modifier.size(30.dp)
            )
        }
    }
}

@Composable
fun VoiceWaveformBars(
    active: Boolean,
    rmsLevel: Float,
    modifier: Modifier = Modifier,
    barCount: Int = 22
) {
    val transition = rememberInfiniteTransition(label = "waveform")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = LinearEasing)
        ),
        label = "wave_phase"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val totalWidth = size.width
            val spacing = totalWidth / (barCount * 1.6f)
            val barWidth = spacing * 0.75f
            val startX = (totalWidth - (barCount * spacing * 1.4f)) / 2f
            val centerY = size.height / 2f

            for (i in 0 until barCount) {
                val normPos = i.toFloat() / (barCount - 1)
                val envelope = sin(normPos * PI).toFloat()
                val wave = if (active) {
                    0.25f + 0.75f * kotlin.math.abs(sin(phase + i * 0.45f)) * (0.4f + rmsLevel * 0.8f)
                } else {
                    0.15f
                }
                val barHeight = (size.height * 0.85f * envelope * wave).coerceAtLeast(6.dp.toPx())
                val x = startX + i * spacing * 1.4f
                val brush = Brush.verticalGradient(
                    colors = listOf(ElectricCyan, NeonViolet)
                )
                drawRoundRect(
                    brush = brush,
                    topLeft = Offset(x, centerY - barHeight / 2f),
                    size = Size(barWidth, barHeight),
                    cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
                )
            }
        }
    }
}
