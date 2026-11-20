package com.example.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.example.data.local.ThemeMode

private val SifatDarkColorScheme = darkColorScheme(
    primary = ElectricCyan,
    onPrimary = ObsidianVoid,
    primaryContainer = ElectricBlue.copy(alpha = 0.28f),
    onPrimaryContainer = SoftIceText,
    secondary = NeonViolet,
    onSecondary = Color.White,
    secondaryContainer = NeonViolet.copy(alpha = 0.24f),
    onSecondaryContainer = Color(0xFFEDE9FE),
    tertiary = RadiantViolet,
    onTertiary = Color.White,
    background = ObsidianVoid,
    onBackground = SoftIceText,
    surface = DeepNavySurface,
    onSurface = SoftIceText,
    surfaceVariant = NavyCardSurface,
    onSurfaceVariant = MutedSlateText,
    surfaceContainerLow = DeepNavySurface,
    surfaceContainer = NavyCardSurface,
    surfaceContainerHigh = NavyElevatedSurface,
    outline = NavyOutline,
    outlineVariant = NavyOutline.copy(alpha = 0.55f),
    error = DangerCoral,
    onError = Color.White
)

private val SifatLightColorScheme = lightColorScheme(
    primary = LightElectricBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE0F2FE),
    onPrimaryContainer = Color(0xFF0C4A6E),
    secondary = LightViolet,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFEDE9FE),
    onSecondaryContainer = Color(0xFF4C1D95),
    tertiary = NeonViolet,
    onTertiary = Color.White,
    background = LightFrostBackground,
    onBackground = LightDeepText,
    surface = LightPureSurface,
    onSurface = LightDeepText,
    surfaceVariant = LightCardSurface,
    onSurfaceVariant = LightMutedText,
    surfaceContainerLow = LightFrostBackground,
    surfaceContainer = LightPureSurface,
    surfaceContainerHigh = LightCardSurface,
    outline = LightOutline,
    outlineVariant = LightOutline.copy(alpha = 0.6f),
    error = DangerCoral,
    onError = Color.White
)

val SifatShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

@Composable
fun SifatAgentTheme(
    themeMode: ThemeMode = ThemeMode.DARK,
    content: @Composable () -> Unit
) {
    val useDarkTheme = when (themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    val colorScheme = if (useDarkTheme) SifatDarkColorScheme else SifatLightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = !useDarkTheme
                    isAppearanceLightNavigationBars = !useDarkTheme
                }
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = SifatTypography,
        shapes = SifatShapes,
        content = content
    )
}
