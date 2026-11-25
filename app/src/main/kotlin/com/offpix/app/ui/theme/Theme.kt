package com.offpix.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * OffPix Obsidian-Plum Glassmorphic Palette:
 * Deep obsidian-violet base with ambient purple top glow, frosted glass surfaces,
 * and signature Violet -> Magenta -> Coral sunset gradients.
 */
object StudioColors {
    val Bg = Color(0xFF0E0A14)
    val BgGlowTop = Color(0xFF3E1F5E)
    val BgGlowMid = Color(0xFF1A1026)

    val Surface = Color(0xFF171122)
    val Surface2 = Color(0xFF211830)
    val Surface3 = Color(0xFF2C2040)
    val Glass = Color(0x1FFFFFFF)       // ~12% white frosted fill
    val GlassDark = Color(0xCC161022)   // floating dock translucent dark glass
    val Border = Color(0x1FFFFFFF)

    val TextPrimary = Color(0xFFF8F5FF)
    val TextSecondary = Color(0xFFB7ABC9)
    val TextMuted = Color(0xFF837599)

    // Signature Violet -> Magenta -> Coral accents
    val Violet = Color(0xFF9333EA)
    val Magenta = Color(0xFFEC4899)
    val Coral = Color(0xFFFB923C)
    val Mint = Color(0xFF6EE7B7) // Kept for subtle online/offline status dot & landmarks
    val Sky = Color(0xFF38BDF8)
    val Purple = Color(0xFFA855F7)

    val OnAccent = Color(0xFFFFFFFF)
    val OnMint = Color(0xFF041510)
    val OnSky = Color(0xFF051320)
    val OnPurple = Color(0xFFFFFFFF)

    val PrimaryGradient = Brush.horizontalGradient(listOf(Violet, Magenta, Coral))
    val AmbientGlowBrush = Brush.verticalGradient(
        colorStops = arrayOf(
            0.0f to Color(0xFF431E69),
            0.24f to Color(0xFF1E122E),
            0.55f to Bg,
            1.0f to Bg
        )
    )
}

private val StudioDarkColorScheme = darkColorScheme(
    primary = StudioColors.Magenta,
    onPrimary = StudioColors.OnAccent,
    secondary = StudioColors.Violet,
    onSecondary = StudioColors.OnAccent,
    tertiary = StudioColors.Coral,
    onTertiary = StudioColors.OnAccent,
    background = StudioColors.Bg,
    onBackground = StudioColors.TextPrimary,
    surface = StudioColors.Surface,
    onSurface = StudioColors.TextPrimary,
    surfaceVariant = StudioColors.Surface2,
    onSurfaceVariant = StudioColors.TextSecondary,
    outline = StudioColors.Border
)

@Composable
fun OffPixTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = StudioDarkColorScheme,
        content = content
    )
}
