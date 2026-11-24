package com.offpix.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object StudioColors {
    val Bg = Color(0xFF0B0D12)
    val Surface = Color(0xFF12161F)
    val Surface2 = Color(0xFF191F2C)
    val Surface3 = Color(0xFF232B3B)
    val Border = Color(0x14FFFFFF)
    val TextPrimary = Color(0xFFF3F6FC)
    val TextSecondary = Color(0xFF9AA6BE)
    val TextMuted = Color(0xFF6C778F)
    val Mint = Color(0xFF6EE7B7)
    val Sky = Color(0xFF38BDF8)
    val Purple = Color(0xFFA78BFA)
    val OnMint = Color(0xFF041510)
    val OnSky = Color(0xFF051320)
    val OnPurple = Color(0xFF120824)
}

private val StudioDarkColorScheme = darkColorScheme(
    primary = StudioColors.Mint,
    onPrimary = StudioColors.OnMint,
    secondary = StudioColors.Sky,
    onSecondary = StudioColors.OnSky,
    tertiary = StudioColors.Purple,
    onTertiary = StudioColors.OnPurple,
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
