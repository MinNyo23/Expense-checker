package com.example.ui.theme

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = RoseLight,
    onPrimary = Color(0xFF5E0020),
    primaryContainer = Color(0xFF7E1B32),
    onPrimaryContainer = Color(0xFFFFD9DF),
    secondary = CoralSecondary,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF772820),
    onSecondaryContainer = Color(0xFFFFDAD4),
    tertiary = VioletAccent,
    onTertiary = Color.White,
    background = DarkBackground,
    surface = DarkSurface,
    surfaceVariant = DarkSurfaceVariant,
    onBackground = Color(0xFFEDE0DE),
    onSurface = Color(0xFFEDE0DE)
)

private val LightColorScheme = lightColorScheme(
    primary = RosePrimary,
    onPrimary = Color.White,
    primaryContainer = RoseContainer,
    onPrimaryContainer = OnRoseContainer,
    secondary = CoralSecondary,
    onSecondary = Color.White,
    secondaryContainer = CoralContainer,
    onSecondaryContainer = OnCoralContainer,
    tertiary = VioletAccent,
    onTertiary = Color.White,
    tertiaryContainer = VioletContainer,
    onTertiaryContainer = OnVioletContainer,
    background = ModernLightBackground,
    surface = ModernLightSurface,
    surfaceVariant = ModernLightSurfaceVariant,
    outline = ModernOutline,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    onSurfaceVariant = TextSecondary
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = false, // Pure romantic light aesthetic by default
    dynamicColor: Boolean = false, // Keep consistent curated couple palette
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

/**
 * Cohesive romantic porcelain-rose ambient background shared across app launch/entry
 * screens and core feature tabs so cards, gradient banners, and map UI harmonize visually.
 */
@Composable
fun RomanticAppBackground(
    isEntryScreen: Boolean = false,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val baseBrush = if (isEntryScreen) RomanticEntryBackgroundBrush else RomanticScreenBackgroundBrush
    Box(
        modifier = modifier
            .fillMaxSize()
            .drawBehind {
                drawRect(brush = baseBrush)
                val maxDim = size.maxDimension.coerceAtLeast(10f)

                // Soft upper-right rose glow
                val topCenter = Offset(x = size.width * 0.88f, y = size.height * 0.10f)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(AmbientRoseGlow, Color.Transparent),
                        center = topCenter,
                        radius = maxDim * 0.52f
                    ),
                    radius = maxDim * 0.52f,
                    center = topCenter
                )

                // Warm mid-left coral-peach glow
                val midLeftCenter = Offset(x = size.width * 0.10f, y = size.height * 0.44f)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(AmbientPeachGlow, Color.Transparent),
                        center = midLeftCenter,
                        radius = maxDim * 0.44f
                    ),
                    radius = maxDim * 0.44f,
                    center = midLeftCenter
                )

                // Subtle bottom-right couple-violet glow
                val bottomCenter = Offset(x = size.width * 0.82f, y = size.height * 0.86f)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(AmbientVioletGlow, Color.Transparent),
                        center = bottomCenter,
                        radius = maxDim * 0.46f
                    ),
                    radius = maxDim * 0.46f,
                    center = bottomCenter
                )
            },
        content = content
    )
}
