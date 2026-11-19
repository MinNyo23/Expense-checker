package com.example.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Modern Vibrant Romantic Couple Palette (Light Aesthetic)
val RosePrimary = Color(0xFFF43F5E)      // Modern vibrant rose (rose-500)
val RoseDark = Color(0xFFE11D48)         // Deep romantic rose (rose-600)
val RoseLight = Color(0xFFFB7185)        // Soft blushing rose
val RoseContainer = Color(0xFFFFF1F2)    // Soft luminous pastel rose
val OnRoseContainer = Color(0xFF881337)

val CoralSecondary = Color(0xFFFB923C)   // Warm sunrise peach
val CoralContainer = Color(0xFFFFF7ED)
val OnCoralContainer = Color(0xFF7C2D12)

val VioletAccent = Color(0xFF8B5CF6)     // Couple together / shared violet
val VioletContainer = Color(0xFFF5F3FF)
val OnVioletContainer = Color(0xFF4C1D95)

// Partner Distinction Colors (Sweet Rose for Her, Sky Azure for Him)
val PartnerAColor = Color(0xFFF43F5E)    // Vibrant Rose Pink (Her / Partner A)
val PartnerBColor = Color(0xFF2563EB)    // Clear Sky Blue (Him / Partner B)
val PartnerSharedColor = Color(0xFF8B5CF6) // Royal Together Violet

// Modern Light Aesthetic Surfaces
val ModernLightBackground = Color(0xFFFAF7F8) // Clean warm porcelain
val ModernLightSurface = Color(0xFFFFFFFF)    // Crisp white
val ModernLightSurfaceVariant = Color(0xFFF3ECEE)
val ModernOutline = Color(0xFFE5DADE)

// Slate & Legacy Dark Theme Support
val DarkBackground = Color(0xFF141218)
val DarkSurface = Color(0xFF1E1A22)
val DarkSurfaceVariant = Color(0xFF2C2733)

// Text Colors
val TextPrimary = Color(0xFF1E1418)
val TextSecondary = Color(0xFF6B5860)
val TextTertiary = Color(0xFF9E8B94)

// Romantic Gradients for Hero & Cards
val RomanticLoveBrush = Brush.horizontalGradient(
    colors = listOf(Color(0xFFF43F5E), Color(0xFFFB7185), Color(0xFFFDA4AF))
)
val CoupleCardBrush = Brush.linearGradient(
    colors = listOf(Color(0xFFFFFFFF), Color(0xFFFFF5F7))
)
val SunsetPeachBrush = Brush.horizontalGradient(
    colors = listOf(Color(0xFFF43F5E), Color(0xFFFB923C))
)
val VioletLoveBrush = Brush.horizontalGradient(
    colors = listOf(Color(0xFF8B5CF6), Color(0xFFEC4899))
)

// Cohesive Entry & Ambient Background Theme Palette
val RomanticBlushSurface = Color(0xFFFEDADE)
val AmbientRoseGlow = Color(0x42FDA4AF)
val AmbientPeachGlow = Color(0x33FDBA74)
val AmbientVioletGlow = Color(0x28C4B5FD)

val RomanticEntryBackgroundBrush = Brush.verticalGradient(
    colors = listOf(
        Color(0xFFFEDADE),
        Color(0xFFFFF1F2),
        Color(0xFFFAF7F8)
    )
)

val RomanticScreenBackgroundBrush = Brush.verticalGradient(
    colors = listOf(
        Color(0xFFFFF1F2),
        Color(0xFFFAF7F8),
        Color(0xFFFFF8F5)
    )
)
