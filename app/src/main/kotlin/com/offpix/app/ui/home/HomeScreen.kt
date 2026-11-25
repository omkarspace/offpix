package com.offpix.app.ui.home

import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.offpix.app.ui.theme.StudioColors
import com.offpix.app.viewmodel.RecentRescue
import com.offpix.app.viewmodel.StudioTab

@Composable
fun HomeScreen(
    recentRescues: List<RecentRescue>,
    onPickPhoto: (StudioTab) -> Unit,
    onOpenRecent: (Uri) -> Unit,
    onClearRecents: () -> Unit,
    onOpenEngineSheet: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(StudioColors.AmbientGlowBrush)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(top = 12.dp, bottom = 104.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Top Bar: Gradient Brand Wordmark + Offline Free Pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "OffPix",
                    style = TextStyle(
                        brush = StudioColors.PrimaryGradient,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-0.5).sp
                    )
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(StudioColors.Glass)
                            .border(1.dp, StudioColors.Border, RoundedCornerShape(999.dp))
                            .clickable(onClick = onOpenEngineSheet)
                            .padding(horizontal = 11.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(StudioColors.Mint)
                        )
                        Text(
                            "100% Offline • Free",
                            color = StudioColors.TextPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(StudioColors.PrimaryGradient)
                            .clickable(onClick = onOpenEngineSheet),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.Bolt, contentDescription = "AI Engine", tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                }
            }

            // Circular Frosted Glass Quick-Action Row (Reference Signature)
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

            // Primary Hero Action Pill
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(StudioColors.PrimaryGradient)
                    .clickable { onPickPhoto(StudioTab.UPSCALE) },
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Rounded.PhotoLibrary, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                    Text(
                        "Pick a Photo to Rescue",
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 15.sp
                    )
                }
            }

            // Featured Studio Cards (2x2 Vibrant Rounded Visual Grid)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("AI Rescue Studio", color = StudioColors.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text("Zero Cloud", color = StudioColors.TextSecondary, fontSize = 11.5.sp)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                VibrantToolTile(
                    title = "4x Ultra HD",
                    subtitle = "Real-ESRGAN x4v3",
                    badge = "3.4 MB",
                    gradient = Brush.linearGradient(listOf(Color(0xFF7C3AED), Color(0xFFEC4899))),
                    icon = Icons.Rounded.Hd,
                    modifier = Modifier.weight(1f),
                    onClick = { onPickPhoto(StudioTab.UPSCALE) }
                )
                VibrantToolTile(
                    title = "Magic Eraser",
                    subtitle = "SAM 2.1 + MI-GAN",
                    badge = "Tap & Brush",
                    gradient = Brush.linearGradient(listOf(Color(0xFFDB2777), Color(0xFFFB923C))),
                    icon = Icons.Rounded.AutoFixHigh,
                    modifier = Modifier.weight(1f),
                    onClick = { onPickPhoto(StudioTab.ERASER) }
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                VibrantToolTile(
                    title = "Motion Unblur",
                    subtitle = "NAFNet GoPro",
                    badge = "Quick Fix",
                    gradient = Brush.linearGradient(listOf(Color(0xFF4F46E5), Color(0xFF9333EA))),
                    icon = Icons.Rounded.Deblur,
                    modifier = Modifier.weight(1f),
                    onClick = { onPickPhoto(StudioTab.UNBLUR) }
                )
                VibrantToolTile(
                    title = "Face Restore",
                    subtitle = "YuNet + GFPGAN",
                    badge = "5-Pt Align",
                    gradient = Brush.linearGradient(listOf(Color(0xFF9333EA), Color(0xFFF43F5E))),
                    icon = Icons.Rounded.FaceRetouchingNatural,
                    modifier = Modifier.weight(1f),
                    onClick = { onPickPhoto(StudioTab.UNBLUR) }
                )
            }

            // Recent Rescues Strip
            if (recentRescues.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Recent Rescues", color = StudioColors.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text(
                        "Clear",
                        color = StudioColors.TextSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clickable(onClick = onClearRecents)
                    )
                }
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 8.dp)
                ) {
                    items(recentRescues) { item ->
                        Column(
                            modifier = Modifier
                                .width(92.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .background(StudioColors.Surface)
                                .border(1.dp, StudioColors.Border, RoundedCornerShape(18.dp))
                                .clickable { onOpenRecent(item.uri) }
                        ) {
                            Image(
                                bitmap = item.thumbnail.asImageBitmap(),
                                contentDescription = item.label,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(86.dp)
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

        // Floating Frosted-Glass Pill Bottom Navigation Dock (Reference Signature)
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 18.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(StudioColors.GlassDark)
                .border(1.dp, StudioColors.Border, RoundedCornerShape(999.dp))
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FloatingDockItem(Icons.Rounded.Home, "Home", active = true) {}
            FloatingDockItem(Icons.Rounded.Hd, "Upscale", active = false) { onPickPhoto(StudioTab.UPSCALE) }
            FloatingDockItem(Icons.Rounded.AutoFixHigh, "Eraser", active = false) { onPickPhoto(StudioTab.ERASER) }
            FloatingDockItem(Icons.Rounded.Settings, "Engine", active = false, onClick = onOpenEngineSheet)
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
                .background(StudioColors.Glass)
                .border(1.dp, StudioColors.Border, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = label, tint = StudioColors.TextPrimary, modifier = Modifier.size(23.dp))
        }
        Text(label, color = StudioColors.TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun VibrantToolTile(
    title: String,
    subtitle: String,
    badge: String,
    gradient: Brush,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .height(132.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(gradient)
            .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(22.dp))
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.24f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
            }
            Text(
                badge,
                color = Color.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color.Black.copy(alpha = 0.24f))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            )
        }
        Column {
            Text(title, color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 14.5.sp)
            Text(subtitle, color = Color.White.copy(alpha = 0.85f), fontSize = 11.sp)
        }
    }
}

@Composable
private fun FloatingDockItem(icon: ImageVector, desc: String, active: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .then(
                if (active) Modifier.background(StudioColors.PrimaryGradient)
                else Modifier.background(StudioColors.Glass)
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
