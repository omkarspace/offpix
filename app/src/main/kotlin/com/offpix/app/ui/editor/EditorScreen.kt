package com.offpix.app.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Redo
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.FaceRetouchingNatural
import androidx.compose.material.icons.rounded.Hd
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.offpix.app.ui.theme.StudioColors
import com.offpix.app.viewmodel.EraserTool
import com.offpix.app.viewmodel.StudioTab
import com.offpix.app.viewmodel.StudioUiState
import com.offpix.app.viewmodel.UnblurMode
import kotlin.math.roundToInt

@Composable
fun EditorScreen(
    state: StudioUiState,
    onBackHome: () -> Unit,
    onPickAnotherPhoto: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onOpenEngineSheet: () -> Unit,
    onOpenExportSheet: () -> Unit,
    onSelectTab: (StudioTab) -> Unit,
    onScaleFactorChange: (Int) -> Unit,
    onUpscaleSharpnessChange: (Float) -> Unit,
    onRunUpscale: () -> Unit,
    onUnblurModeChange: (UnblurMode) -> Unit,
    onUnblurStrengthChange: (Float) -> Unit,
    onRunUnblur: () -> Unit,
    onEraserToolChange: (EraserTool) -> Unit,
    onBrushSizeChange: (Float) -> Unit,
    onTapMask: (Float, Float) -> Unit,
    onBrushStroke: (Float, Float, Float, Float) -> Unit,
    onClearMask: () -> Unit,
    onRunErase: () -> Unit,
    onEraseAnother: () -> Unit,
    onCancelProcessing: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(StudioColors.AmbientGlowBrush)
    ) {
        // Glassmorphic Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TopGlassCircleBtn(Icons.AutoMirrored.Rounded.ArrowBack, "Back", onClick = onBackHome)
                TopGlassCircleBtn(Icons.Rounded.PhotoLibrary, "Pick Photo", onClick = onPickAnotherPhoto)
                TopGlassCircleBtn(Icons.AutoMirrored.Rounded.Undo, "Undo", enabled = state.canUndo, onClick = onUndo)
                TopGlassCircleBtn(Icons.AutoMirrored.Rounded.Redo, "Redo", enabled = state.canRedo, onClick = onRedo)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                TopGlassCircleBtn(Icons.Rounded.Bolt, "Engine Status", onClick = onOpenEngineSheet)
                Row(
                    modifier = Modifier
                        .height(36.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(StudioColors.PrimaryGradient)
                        .clickable(onClick = onOpenExportSheet)
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Icon(Icons.Rounded.Download, null, tint = Color.White, modifier = Modifier.size(15.dp))
                    Text("Save", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 12.5.sp)
                }
            }
        }

        // Interactive Canvas Stage
        BeforeAfterStage(
            beforeBitmap = state.beforeBitmap,
            afterBitmap = state.afterBitmap,
            maskBitmap = state.maskBitmap,
            activeTab = state.activeTab,
            eraserTool = state.eraserTool,
            detectedFaces = state.detectedFaces,
            afterBadgeLabel = state.afterBadgeLabel,
            isProcessing = state.isProcessing,
            hudTitle = state.hudTitle,
            hudSub = state.hudSub,
            hudProgress = state.hudProgress,
            statusPill = state.statusPill,
            onTapMask = onTapMask,
            onBrushStroke = onBrushStroke,
            onCancelProcessing = onCancelProcessing,
            modifier = Modifier.weight(1f)
        )

        // Frosted Glass Context Controls Card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(StudioColors.Surface)
                .border(1.dp, StudioColors.Border, RoundedCornerShape(24.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            when (state.activeTab) {
                StudioTab.UPSCALE -> {
                    GradientPillPair(
                        leftLabel = "2x Fast HD",
                        rightLabel = "4x Ultra HD",
                        leftSelected = state.upscaleFactor == 2,
                        onSelectLeft = { onScaleFactorChange(2) },
                        onSelectRight = { onScaleFactorChange(4) }
                    )
                    LabeledSlider(
                        label = "Detail Sharpness",
                        value = state.upscaleSharpness,
                        valueRange = 0.3f..1f,
                        valueText = "${(state.upscaleSharpness * 100).roundToInt()}%",
                        onValueChange = onUpscaleSharpnessChange
                    )
                    GradientCtaButton(
                        text = "Enhance ${state.upscaleFactor}x HD (Real-ESRGAN)",
                        enabled = !state.isProcessing,
                        onClick = onRunUpscale
                    )
                }

                StudioTab.UNBLUR -> {
                    GradientPillPair(
                        leftLabel = "Quick Fix (NAFNet)",
                        rightLabel = "Portrait Restore",
                        leftSelected = state.unblurMode == UnblurMode.QUICK_FIX,
                        onSelectLeft = { onUnblurModeChange(UnblurMode.QUICK_FIX) },
                        onSelectRight = { onUnblurModeChange(UnblurMode.PORTRAIT_RESTORE) }
                    )
                    LabeledSlider(
                        label = "Restoration Strength",
                        value = state.unblurStrength,
                        valueRange = 0.4f..1f,
                        valueText = "${(state.unblurStrength * 100).roundToInt()}%",
                        onValueChange = onUnblurStrengthChange
                    )
                    if (state.noFaceDetected && state.unblurMode == UnblurMode.PORTRAIT_RESTORE) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(StudioColors.Violet.copy(alpha = 0.16f))
                                .border(1.dp, StudioColors.Magenta.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                                .padding(horizontal = 10.dp, vertical = 7.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("No face detected in photo", color = StudioColors.TextPrimary, fontSize = 11.5.sp)
                            Text(
                                "Use Quick Fix →",
                                color = StudioColors.Coral,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.clickable { onUnblurModeChange(UnblurMode.QUICK_FIX) }
                            )
                        }
                    }
                    GradientCtaButton(
                        text = if (state.unblurMode == UnblurMode.QUICK_FIX) {
                            "Fix Motion Blur (NAFNet)"
                        } else {
                            "Detect & Restore Face (YuNet + GFPGAN)"
                        },
                        enabled = !state.isProcessing,
                        onClick = onRunUnblur
                    )
                }

                StudioTab.ERASER -> {
                    GradientPillPair(
                        leftLabel = "Tap Object (SAM 2.1)",
                        rightLabel = "Brush Mask",
                        leftSelected = state.eraserTool == EraserTool.TAP_OBJECT,
                        onSelectLeft = { onEraserToolChange(EraserTool.TAP_OBJECT) },
                        onSelectRight = { onEraserToolChange(EraserTool.BRUSH) }
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            if (state.eraserTool == EraserTool.TAP_OBJECT) "Tap any object to auto-mask" else "Paint over marks to erase",
                            color = StudioColors.TextSecondary,
                            fontSize = 11.5.sp
                        )
                        Text(state.maskStatusText, color = StudioColors.Coral, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                    }
                    if (state.eraserTool == EraserTool.BRUSH) {
                        LabeledSlider(
                            label = "Brush Size",
                            value = state.brushSizePx,
                            valueRange = 12f..54f,
                            valueText = "${state.brushSizePx.roundToInt()} px",
                            onValueChange = onBrushSizeChange
                        )
                    }
                    if (state.afterBitmap != null) {
                        GradientCtaButton(
                            text = "Erase Another Object",
                            enabled = true,
                            onClick = onEraseAnother
                        )
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = onClearMask,
                                shape = RoundedCornerShape(999.dp),
                                modifier = Modifier.height(44.dp)
                            ) {
                                Text("Clear", color = StudioColors.TextPrimary, fontWeight = FontWeight.Bold)
                            }
                            GradientCtaButton(
                                text = "Erase Selection (MI-GAN)",
                                enabled = state.hasMask && !state.isProcessing,
                                onClick = onRunErase,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }

        // Floating Frosted-Glass Pill Dock for the 3 Core Tabs
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(StudioColors.GlassDark)
                    .border(1.dp, StudioColors.Border, RoundedCornerShape(999.dp))
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                EditorDockPill("Upscale", Icons.Rounded.Hd, state.activeTab == StudioTab.UPSCALE) {
                    onSelectTab(StudioTab.UPSCALE)
                }
                EditorDockPill("Unblur", Icons.Rounded.FaceRetouchingNatural, state.activeTab == StudioTab.UNBLUR) {
                    onSelectTab(StudioTab.UNBLUR)
                }
                EditorDockPill("Eraser", Icons.Rounded.AutoFixHigh, state.activeTab == StudioTab.ERASER) {
                    onSelectTab(StudioTab.ERASER)
                }
            }
        }
    }
}

@Composable
private fun TopGlassCircleBtn(icon: ImageVector, desc: String, enabled: Boolean = true, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(StudioColors.Glass)
            .border(1.dp, StudioColors.Border, CircleShape)
    ) {
        Icon(
            icon,
            contentDescription = desc,
            tint = if (enabled) StudioColors.TextPrimary else StudioColors.TextMuted,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun GradientPillPair(
    leftLabel: String,
    rightLabel: String,
    leftSelected: Boolean,
    onSelectLeft: () -> Unit,
    onSelectRight: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(999.dp))
            .background(StudioColors.Bg)
            .border(1.dp, StudioColors.Border, RoundedCornerShape(999.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        PillOption(leftLabel, leftSelected, Modifier.weight(1f), onSelectLeft)
        PillOption(rightLabel, !leftSelected, Modifier.weight(1f), onSelectRight)
    }
}

@Composable
private fun PillOption(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .height(34.dp)
            .clip(RoundedCornerShape(999.dp))
            .then(if (selected) Modifier.background(StudioColors.PrimaryGradient) else Modifier)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (selected) Color.White else StudioColors.TextSecondary,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun GradientCtaButton(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth()
) {
    Box(
        modifier = modifier
            .height(44.dp)
            .clip(RoundedCornerShape(999.dp))
            .then(
                if (enabled) Modifier.background(StudioColors.PrimaryGradient)
                else Modifier.background(StudioColors.Surface3)
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (enabled) Color.White else StudioColors.TextMuted,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 13.sp
        )
    }
}

@Composable
private fun LabeledSlider(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    valueText: String,
    onValueChange: (Float) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(label, color = StudioColors.TextSecondary, fontSize = 12.sp)
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            colors = SliderDefaults.colors(
                thumbColor = StudioColors.Coral,
                activeTrackColor = StudioColors.Magenta,
                inactiveTrackColor = StudioColors.Surface3
            ),
            modifier = Modifier.weight(1f)
        )
        Text(valueText, color = StudioColors.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
    }
}

@Composable
private fun EditorDockPill(label: String, icon: ImageVector, active: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .height(40.dp)
            .clip(RoundedCornerShape(999.dp))
            .then(
                if (active) Modifier.background(StudioColors.PrimaryGradient)
                else Modifier.background(Color.Transparent)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(icon, contentDescription = label, tint = if (active) Color.White else StudioColors.TextSecondary, modifier = Modifier.size(18.dp))
        Text(label, color = if (active) Color.White else StudioColors.TextSecondary, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
    }
}
