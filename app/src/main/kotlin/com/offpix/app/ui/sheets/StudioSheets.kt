package com.offpix.app.ui.sheets

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.offpix.app.io.ExportFormat
import com.offpix.app.ui.theme.StudioColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportBottomSheet(
    width: Int,
    height: Int,
    selectedFormat: ExportFormat,
    preserveExif: Boolean,
    onFormatChange: (ExportFormat) -> Unit,
    onPreserveExifChange: (Boolean) -> Unit,
    onConfirmSave: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = StudioColors.Surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("Save Rescued Photo", color = StudioColors.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(StudioColors.Surface2)
                    .border(1.dp, StudioColors.Border, RoundedCornerShape(12.dp))
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Output Resolution", color = StudioColors.TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Text("$width × $height px • Ready Offline", color = StudioColors.TextSecondary, fontSize = 11.5.sp)
                }
                Text(
                    "0 Watermarks",
                    color = StudioColors.Mint,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(StudioColors.Mint.copy(alpha = 0.14f))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(StudioColors.Bg)
                    .border(1.dp, StudioColors.Border, RoundedCornerShape(12.dp))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FormatChip(
                    label = "JPEG 95% (Fast)",
                    selected = selectedFormat == ExportFormat.JPEG_95,
                    modifier = Modifier.weight(1f),
                    onClick = { onFormatChange(ExportFormat.JPEG_95) }
                )
                FormatChip(
                    label = "PNG Lossless",
                    selected = selectedFormat == ExportFormat.PNG_LOSSLESS,
                    modifier = Modifier.weight(1f),
                    onClick = { onFormatChange(ExportFormat.PNG_LOSSLESS) }
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(StudioColors.Surface2)
                    .clickable { onPreserveExifChange(!preserveExif) }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Preserve EXIF Metadata", color = StudioColors.TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Text("Keep original date, orientation & camera info", color = StudioColors.TextSecondary, fontSize = 11.5.sp)
                }
                Checkbox(
                    checked = preserveExif,
                    onCheckedChange = onPreserveExifChange,
                    colors = CheckboxDefaults.colors(checkedColor = StudioColors.Mint, checkmarkColor = StudioColors.OnMint)
                )
            }

            Button(
                onClick = onConfirmSave,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = StudioColors.Mint, contentColor = StudioColors.OnMint)
            ) {
                Text("Save to Gallery", fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
            }
            Spacer(Modifier.height(14.dp))
        }
    }
}

@Composable
private fun FormatChip(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val bg = if (selected) StudioColors.Surface3 else StudioColors.Bg
    val fg = if (selected) StudioColors.TextPrimary else StudioColors.TextSecondary
    Row(
        modifier = modifier
            .height(36.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(bg)
            .clickable(onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = fg, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EngineLicensesSheet(
    activeAccelerator: String,
    onDismiss: () -> Unit
) {
    val models = listOf(
        Triple("Real-ESRGAN General x4v3", "3.5 MB • FP16 • 4x Super-Resolution", "BSD-3"),
        Triple("NAFNet-GoPro-width32", "38.3 MB • FP16 • Motion Deblur", "MIT"),
        Triple("YuNet + GFPGAN v1.4", "0.3 MB + Quantized • Face Align & Restore", "MIT / Apache 2.0"),
        Triple("SAM 2.1 Hiera-Tiny (Enc + Dec)", "80 MB + 17 MB • Tap-to-Select Mask", "Apache 2.0"),
        Triple("MI-GAN 512 Places2", "16.3 MB • FP16 • Object Inpainting", "MIT")
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = StudioColors.Surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("On-Device AI Engine", color = StudioColors.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(StudioColors.Mint.copy(alpha = 0.08f))
                    .border(1.dp, StudioColors.Mint.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("LiteRT CompiledModel • $activeAccelerator", color = StudioColors.Mint, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text("Priority: NPU → GPU (ML Drift) → CPU (XNNPACK)", color = StudioColors.TextSecondary, fontSize = 11.sp)
                }
                Text("0 KB Sent", color = StudioColors.Mint, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }

            models.forEach { (name, detail, lic) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(StudioColors.Surface2)
                        .border(1.dp, StudioColors.Border, RoundedCornerShape(12.dp))
                        .padding(11.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(name, color = StudioColors.TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp)
                        Text(detail, color = StudioColors.TextSecondary, fontSize = 11.sp)
                    }
                    Text(
                        lic,
                        color = StudioColors.Mint,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(StudioColors.Mint.copy(alpha = 0.14f))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Text(
                "Zero internet permission, zero analytics, zero accounts, zero ads. All models are commercial-safe open source.",
                color = StudioColors.TextMuted,
                fontSize = 11.sp,
                lineHeight = 16.sp
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}
