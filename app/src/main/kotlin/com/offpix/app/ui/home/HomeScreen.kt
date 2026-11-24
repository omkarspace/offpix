package com.offpix.app.ui.home

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.FaceRetouchingNatural
import androidx.compose.material.icons.rounded.Hd
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
    onOpenRecent: (android.net.Uri) -> Unit,
    onOpenEngineSheet: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(StudioColors.Bg)
    ) {
        // Top App Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(
                            Brush.linearGradient(listOf(StudioColors.Mint, StudioColors.Sky))
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text("O", color = StudioColors.OnMint, fontWeight = FontWeight.Black, fontSize = 15.sp)
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "OffPix",
                    color = StudioColors.TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }
            IconButton(
                onClick = onOpenEngineSheet,
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(StudioColors.Surface)
                    .border(1.dp, StudioColors.Border, RoundedCornerShape(11.dp))
            ) {
                Icon(Icons.Rounded.Settings, contentDescription = "Engine & Licenses", tint = StudioColors.TextPrimary)
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // Hero Card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFF162235), Color(0xFF111724), Color(0xFF0F1B1D))
                        )
                    )
                    .border(1.dp, StudioColors.Mint.copy(alpha = 0.25f), RoundedCornerShape(22.dp))
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(StudioColors.Mint.copy(alpha = 0.14f))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.Lock, null, tint = StudioColors.Mint, modifier = Modifier.size(13.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "100% On-Device • Zero Cloud • Zero Ads",
                        color = StudioColors.Mint,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    "Rescue any photo in seconds.",
                    color = StudioColors.TextPrimary,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                    lineHeight = 26.sp
                )
                Text(
                    "Flagship 4x upscaling, motion unblur, face restore, and magic object removalâ€”running locally on your phone's GPU.",
                    color = StudioColors.TextSecondary,
                    fontSize = 13.sp,
                    lineHeight = 19.sp
                )
                Button(
                    onClick = { onPickPhoto(StudioTab.UPSCALE) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(15.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = StudioColors.Mint,
                        contentColor = StudioColors.OnMint
                    )
                ) {
                    Icon(Icons.Rounded.PhotoLibrary, null, modifier = Modifier.size(19.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Pick a Photo from Device", fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                }
            }

            // 3 Offline Studio Tools
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("3 OFFLINE STUDIO TOOLS", color = StudioColors.TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Text("100% FREE", color = StudioColors.Mint, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ToolCard(
                    title = "4x HD Upscale",
                    subtitle = "Real-ESRGAN x4v3 sharpens low-res WhatsApp forwards & old scans.",
                    icon = Icons.Rounded.Hd,
                    tint = StudioColors.Mint,
                    onClick = { onPickPhoto(StudioTab.UPSCALE) }
                )
                ToolCard(
                    title = "Unblur & Face Rescue",
                    subtitle = "NAFNet fixes camera shake; YuNet + GFPGAN reconstructs faces.",
                    icon = Icons.Rounded.FaceRetouchingNatural,
                    tint = StudioColors.Sky,
                    onClick = { onPickPhoto(StudioTab.UNBLUR) }
                )
                ToolCard(
                    title = "Magic Eraser",
                    subtitle = "SAM 2.1 tap-to-select + MI-GAN removes photobombers in milliseconds.",
                    icon = Icons.Rounded.AutoFixHigh,
                    tint = StudioColors.Purple,
                    onClick = { onPickPhoto(StudioTab.ERASER) }
                )
            }

            // Recent Rescues Strip
            if (recentRescues.isNotEmpty()) {
                Text("RECENT RESCUES (LOCAL)", color = StudioColors.TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 12.dp)
                ) {
                    items(recentRescues) { item ->
                        Column(
                            modifier = Modifier
                                .width(88.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(StudioColors.Surface)
                                .border(1.dp, StudioColors.Border, RoundedCornerShape(12.dp))
                                .clickable { onOpenRecent(item.uri) }
                        ) {
                            Image(
                                bitmap = item.thumbnail.asImageBitmap(),
                                contentDescription = item.label,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(84.dp)
                            )
                            Text(
                                text = item.label,
                                color = StudioColors.TextSecondary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ToolCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    tint: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(15.dp))
            .background(StudioColors.Surface)
            .border(1.dp, StudioColors.Border, RoundedCornerShape(15.dp))
            .clickable(onClick = onClick)
            .padding(13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(tint.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, color = StudioColors.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Spacer(Modifier.height(2.dp))
            Text(subtitle, color = StudioColors.TextSecondary, fontSize = 12.sp, lineHeight = 16.sp)
        }
    }
}
