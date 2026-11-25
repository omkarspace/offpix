package com.offpix.app.ui.home

import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Deblur
import androidx.compose.material.icons.rounded.FaceRetouchingNatural
import androidx.compose.material.icons.rounded.Hd
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.offpix.app.ui.theme.StudioColors
import com.offpix.app.viewmodel.RecentRescue
import com.offpix.app.viewmodel.StudioTab

private enum class HomeFilter(val label: String) {
    ALL("Trending"),
    UPSCALE("4x Upscale"),
    PORTRAIT("Unblur & Face"),
    ERASER("Magic Eraser")
}

@Composable
fun HomeScreen(
    recentRescues: List<RecentRescue>,
    onPickPhoto: (StudioTab) -> Unit,
    onOpenRecent: (Uri) -> Unit,
    onClearRecents: () -> Unit,
    onOpenEngineSheet: () -> Unit
) {
    var selectedFilter by remember { mutableStateOf(HomeFilter.ALL) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0B090F))
    ) {
        // True Radial Plum-Magenta Top Spotlight (matching reference image glow)
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    colorStops = arrayOf(
                        0.0f to Color(0x996B2B9E),
                        0.42f to Color(0x443B155C),
                        1.0f to Color.Transparent
                    ),
                    center = Offset(size.width * 0.5f, size.height * 0.04f),
                    radius = size.width * 0.95f
                ),
                radius = size.width * 0.95f,
                center = Offset(size.width * 0.5f, size.height * 0.04f)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(top = 12.dp, bottom = 104.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // Top Bar: Brand Avatar + Glowing Engine Circle + Offline Pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(StudioColors.PrimaryGradient),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("O", color = Color.White, fontWeight = FontWeight.Black, fontSize = 18.sp)
                    }
                    Column {
                        Text("OffPix", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
                        Text("Offline AI Studio", color = StudioColors.TextSecondary, fontSize = 11.sp)
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(listOf(Color(0xFF9333EA), Color(0xFFEC4899)))
                            )
                            .clickable(onClick = onOpenEngineSheet),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.Bolt, contentDescription = "GPU Engine", tint = Color.White, modifier = Modifier.size(19.dp))
                    }
                    Row(
                        modifier = Modifier
                            .height(38.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(StudioColors.Glass)
                            .border(1.dp, StudioColors.Border, RoundedCornerShape(999.dp))
                            .clickable(onClick = onOpenEngineSheet)
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(StudioColors.Mint)
                        )
                        Text("100% Free", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // 5 Circular Frosted Glass Quick-Action Tools (Reference Signature)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                GlassQuickCircle("4x HD", Icons.Rounded.Hd) { onPickPhoto(StudioTab.UPSCALE) }
                GlassQuickCircle("Unblur", Icons.Rounded.Deblur) { onPickPhoto(StudioTab.UNBLUR) }
                GlassQuickCircle("Face Fix", Icons.Rounded.FaceRetouchingNatural) { onPickPhoto(StudioTab.UNBLUR) }
                GlassQuickCircle("Eraser", Icons.Rounded.AutoFixHigh) { onPickPhoto(StudioTab.ERASER) }
                GlassQuickCircle("Gallery", Icons.Rounded.PhotoLibrary) { onPickPhoto(StudioTab.UPSCALE) }
            }

            // Dark Frosted Search / Pick-a-Photo Bar + Filter Icon (Reference Signature)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(Color(0x18FFFFFF))
                        .border(1.dp, Color(0x1AFFFFFF), RoundedCornerShape(999.dp))
                        .clickable { onPickPhoto(StudioTab.UPSCALE) }
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(Icons.Rounded.Search, contentDescription = null, tint = StudioColors.TextSecondary, modifier = Modifier.size(19.dp))
                    Text("Pick a photo to rescue...", color = StudioColors.TextSecondary, fontSize = 13.sp)
                }
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color(0x18FFFFFF))
                        .border(1.dp, Color(0x1AFFFFFF), CircleShape)
                        .clickable(onClick = onOpenEngineSheet),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.Tune, contentDescription = "Engine Settings", tint = Color.White, modifier = Modifier.size(20.dp))
                }
            }

            // Horizontal Category Filter Pills (Active = Purple -> Coral Gradient)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                HomeFilter.entries.forEach { filter ->
                    val active = selectedFilter == filter
                    Box(
                        modifier = Modifier
                            .height(36.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .then(
                                if (active) Modifier.background(StudioColors.PrimaryGradient)
                                else Modifier
                                    .background(Color(0x18FFFFFF))
                                    .border(1.dp, Color(0x14FFFFFF), RoundedCornerShape(999.dp))
                            )
                            .clickable { selectedFilter = filter }
                            .padding(horizontal = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = filter.label,
                            color = if (active) Color.White else StudioColors.TextSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Section 1: Enhance & Upscale Visual Cards
            if (selectedFilter == HomeFilter.ALL || selectedFilter == HomeFilter.UPSCALE) {
                SectionRowHeader("Upscale & Sharpen", "Pick Photo") { onPickPhoto(StudioTab.UPSCALE) }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    VisualPreviewCard(
                        title = "4x Ultra HD",
                        subtitle = "Real-ESRGAN x4v3",
                        artworkType = CardArtwork.SPLIT_UPSCALE,
                        onClick = { onPickPhoto(StudioTab.UPSCALE) }
                    )
                    VisualPreviewCard(
                        title = "Motion Unblur",
                        subtitle = "NAFNet GoPro",
                        artworkType = CardArtwork.DEBLUR_WAVES,
                        onClick = { onPickPhoto(StudioTab.UNBLUR) }
                    )
                    VisualPreviewCard(
                        title = "2x Fast HD",
                        subtitle = "Instant Crisp",
                        artworkType = CardArtwork.SPLIT_UPSCALE,
                        onClick = { onPickPhoto(StudioTab.UPSCALE) }
                    )
                }
            }

            // Section 2: Magic Eraser & Portrait Rescue Visual Cards
            if (selectedFilter == HomeFilter.ALL || selectedFilter == HomeFilter.ERASER || selectedFilter == HomeFilter.PORTRAIT) {
                SectionRowHeader("Eraser & Portrait Rescue", "Try Now") { onPickPhoto(StudioTab.ERASER) }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    VisualPreviewCard(
                        title = "Magic Eraser",
                        subtitle = "SAM 2.1 + MI-GAN",
                        artworkType = CardArtwork.CHECKER_ERASER,
                        onClick = { onPickPhoto(StudioTab.ERASER) }
                    )
                    VisualPreviewCard(
                        title = "Portrait Restore",
                        subtitle = "YuNet + GFPGAN",
                        artworkType = CardArtwork.FACE_LANDMARKS,
                        onClick = { onPickPhoto(StudioTab.UNBLUR) }
                    )
                }
            }

            // Section 3: Recent Rescues (Local History)
            if (recentRescues.isNotEmpty()) {
                SectionRowHeader("Recent Rescues", "Clear", onAction = onClearRecents)
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 8.dp)
                ) {
                    items(recentRescues) { item ->
                        Column(
                            modifier = Modifier
                                .width(98.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .background(Color(0xFF171122))
                                .border(1.dp, StudioColors.Border, RoundedCornerShape(18.dp))
                                .clickable { onOpenRecent(item.uri) }
                        ) {
                            Image(
                                bitmap = item.thumbnail.asImageBitmap(),
                                contentDescription = item.label,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(92.dp)
                            )
                            Text(
                                text = item.label,
                                color = StudioColors.TextSecondary,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                            )
                        }
                    }
                }
            }
        }

        // Floating Frosted-Glass Pill Dock (exact 4-circle reference dock)
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 18.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(Color(0xDB171122))
                .border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(999.dp))
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FloatingDockCircle(Icons.Rounded.Home, "Home", active = true) {}
            FloatingDockCircle(Icons.Rounded.Hd, "Upscale", active = false) { onPickPhoto(StudioTab.UPSCALE) }
            FloatingDockCircle(Icons.Rounded.AutoFixHigh, "Eraser", active = false) { onPickPhoto(StudioTab.ERASER) }
            FloatingDockCircle(Icons.Rounded.Settings, "Engine", active = false, onClick = onOpenEngineSheet)
        }
    }
}

private enum class CardArtwork { SPLIT_UPSCALE, CHECKER_ERASER, FACE_LANDMARKS, DEBLUR_WAVES }

@Composable
private fun SectionRowHeader(title: String, actionText: String, onAction: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        Text(
            actionText,
            color = StudioColors.TextSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.clickable(onClick = onAction)
        )
    }
}

@Composable
private fun VisualPreviewCard(
    title: String,
    subtitle: String,
    artworkType: CardArtwork,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(154.dp)
            .clickable(onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(142.dp)
                .clip(RoundedCornerShape(22.dp))
                .border(1.dp, Color(0x26FFFFFF), RoundedCornerShape(22.dp))
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                when (artworkType) {
                    CardArtwork.SPLIT_UPSCALE -> {
                        // Left blurred dusk tone, right crisp vibrant neon-coral skyline + split line
                        drawRect(
                            brush = Brush.linearGradient(listOf(Color(0xFF2B1B4D), Color(0xFF4C1D95))),
                            size = Size(size.width * 0.5f, size.height)
                        )
                        drawRect(
                            brush = Brush.linearGradient(listOf(Color(0xFF9333EA), Color(0xFFEC4899), Color(0xFFFB923C))),
                            topLeft = Offset(size.width * 0.5f, 0f),
                            size = Size(size.width * 0.5f, size.height)
                        )
                        // Geometric skyline silhouette
                        drawRoundRect(
                            color = Color(0x550E0A14),
                            topLeft = Offset(size.width * 0.16f, size.height * 0.44f),
                            size = Size(size.width * 0.24f, size.height * 0.56f),
                            cornerRadius = CornerRadius(12f, 12f)
                        )
                        drawRoundRect(
                            color = Color(0xAA0E0A14),
                            topLeft = Offset(size.width * 0.56f, size.height * 0.30f),
                            size = Size(size.width * 0.28f, size.height * 0.70f),
                            cornerRadius = CornerRadius(12f, 12f)
                        )
                        // Split divider & handle
                        drawLine(Color.White, Offset(size.width * 0.5f, 0f), Offset(size.width * 0.5f, size.height), strokeWidth = 4f)
                        drawCircle(Color.White, radius = 16f, center = Offset(size.width * 0.5f, size.height * 0.5f))
                    }

                    CardArtwork.CHECKER_ERASER -> {
                        // Reference-style pink/plum checkerboard cutout + glowing wand selection
                        val cell = size.width / 8f
                        for (r in 0..7) {
                            for (c in 0..7) {
                                drawRect(
                                    color = if ((r + c) % 2 == 0) Color(0xFFFCE7F3) else Color(0xFFE9D5FF),
                                    topLeft = Offset(c * cell, r * cell),
                                    size = Size(cell, cell)
                                )
                            }
                        }
                        // Glowing violet-coral selection silhouette
                        drawRoundRect(
                            brush = Brush.linearGradient(listOf(Color(0xCC9333EA), Color(0xCCEC4899), Color(0xCCFB923C))),
                            topLeft = Offset(size.width * 0.22f, size.height * 0.22f),
                            size = Size(size.width * 0.56f, size.height * 0.56f),
                            cornerRadius = CornerRadius(28f, 28f)
                        )
                        drawRoundRect(
                            color = Color.White,
                            topLeft = Offset(size.width * 0.22f, size.height * 0.22f),
                            size = Size(size.width * 0.56f, size.height * 0.56f),
                            cornerRadius = CornerRadius(28f, 28f),
                            style = Stroke(width = 4f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f)))
                        )
                    }

                    CardArtwork.FACE_LANDMARKS -> {
                        // Warm coral-purple portrait backdrop + YuNet 5-point landmark reticle
                        drawRect(brush = Brush.linearGradient(listOf(Color(0xFF7E22CE), Color(0xFFDB2777), Color(0xFFFB923C))))
                        drawCircle(Color(0x33FFFFFF), radius = size.minDimension * 0.28f, center = Offset(size.width * 0.5f, size.height * 0.46f))
                        drawRoundRect(
                            color = Color(0xFF38BDF8),
                            topLeft = Offset(size.width * 0.22f, size.height * 0.18f),
                            size = Size(size.width * 0.56f, size.height * 0.58f),
                            cornerRadius = CornerRadius(20f, 20f),
                            style = Stroke(width = 4f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)))
                        )
                        val pts = listOf(0.39f to 0.42f, 0.61f to 0.42f, 0.50f to 0.52f, 0.42f to 0.63f, 0.58f to 0.63f)
                        pts.forEach { (px, py) ->
                            drawCircle(StudioColors.Mint, radius = 6f, center = Offset(size.width * px, size.height * py))
                        }
                    }

                    CardArtwork.DEBLUR_WAVES -> {
                        // Deep indigo-to-magenta motion focus rings
                        drawRect(brush = Brush.linearGradient(listOf(Color(0xFF312E81), Color(0xFF7C3AED), Color(0xFFEC4899))))
                        listOf(0.42f, 0.30f, 0.18f).forEachIndexed { idx, frac ->
                            drawCircle(
                                color = Color.White.copy(alpha = 0.22f + idx * 0.2f),
                                radius = size.minDimension * frac,
                                center = Offset(size.width * 0.5f, size.height * 0.5f),
                                style = Stroke(width = 5f)
                            )
                        }
                    }
                }
            }
        }
        Column(modifier = Modifier.padding(horizontal = 2.dp)) {
            Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Text(subtitle, color = StudioColors.TextSecondary, fontSize = 11.sp)
        }
    }
}

@Composable
private fun GlassQuickCircle(label: String, icon: ImageVector, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(Color(0x1FFFFFFF))
                .border(1.dp, Color(0x1EFFFFFF), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(23.dp))
        }
        Text(label, color = StudioColors.TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun FloatingDockCircle(icon: ImageVector, desc: String, active: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .then(
                if (active) Modifier.background(StudioColors.PrimaryGradient)
                else Modifier.background(Color(0x18FFFFFF))
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon,
            contentDescription = desc,
            tint = if (active) Color.White else StudioColors.TextSecondary,
            modifier = Modifier.size(20.dp)
        )
    }
}
