package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = RoseLight,
    onPrimary = Color(0xFF5E0020),
    primaryContainer = Color(0xFF7E1B32),
    onPrimaryContainer = Color(0xFFFFD9DF),
    secondary = CoralSecondary,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF772820),
    onSecondaryContainer = Color(0xFFFFDAD4),
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
    background = WarmBackground,
    surface = WarmSurface,
    surfaceVariant = WarmSurfaceVariant,
    onBackground = Color(0xFF201A1B),
    onSurface = Color(0xFF201A1B)
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Keep consistent branded couple palette
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
