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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
            .background(StudioColors.Bg)
    ) {
        // Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TopIconBtn(Icons.AutoMirrored.Rounded.ArrowBack, "Back", onClick = onBackHome)
                TopIconBtn(Icons.Rounded.PhotoLibrary, "Pick Photo", onClick = onPickAnotherPhoto)
                TopIconBtn(
                    Icons.AutoMirrored.Rounded.Undo,
                    "Undo",
                    enabled = state.canUndo,
                    onClick = onUndo
                )
                TopIconBtn(
                    Icons.AutoMirrored.Rounded.Redo,
                    "Redo",
                    enabled = state.canRedo,
                    onClick = onRedo
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                TopIconBtn(Icons.Rounded.Bolt, "Engine Status", onClick = onOpenEngineSheet)
                Button(
                    onClick = onOpenExportSheet,
                    shape = RoundedCornerShape(999.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = StudioColors.Mint,
                        contentColor = StudioColors.OnMint
                    ),
                    modifier = Modifier.height(36.dp)
                ) {
                    Icon(Icons.Rounded.Download, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("Save", fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
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

        // Context Controls Drawer
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(StudioColors.Surface)
                .border(1.dp, StudioColors.Border)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            when (state.activeTab) {
                StudioTab.UPSCALE -> {
                    SegmentedPair(
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
                        tint = StudioColors.Mint,
                        onValueChange = onUpscaleSharpnessChange
                    )
                    Button(
                        onClick = onRunUpscale,
                        enabled = !state.isProcessing,
                        modifier = Modifier.fillMaxWidth().height(44.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = StudioColors.Mint,
                            contentColor = StudioColors.OnMint
                        )
                    ) {
                        Text("Enhance ${state.upscaleFactor}x HD (Real-ESRGAN)", fontWeight = FontWeight.ExtraBold)
                    }
                }

                StudioTab.UNBLUR -> {
                    SegmentedPair(
                        leftLabel = "Quick Fix (NAFNet)",
                        rightLabel = "Portrait Restore (GFPGAN)",
                        leftSelected = state.unblurMode == UnblurMode.QUICK_FIX,
                        onSelectLeft = { onUnblurModeChange(UnblurMode.QUICK_FIX) },
                        onSelectRight = { onUnblurModeChange(UnblurMode.PORTRAIT_RESTORE) }
                    )
                    LabeledSlider(
                        label = "Restoration Strength",
                        value = state.unblurStrength,
                        valueRange = 0.4f..1f,
                        valueText = "${(state.unblurStrength * 100).roundToInt()}%",
                        tint = StudioColors.Sky,
                        onValueChange = onUnblurStrengthChange
                    )
                    if (state.noFaceDetected && state.unblurMode == UnblurMode.PORTRAIT_RESTORE) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(StudioColors.Sky.copy(alpha = 0.12f))
                                .border(1.dp, StudioColors.Sky.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                                .padding(horizontal = 10.dp, vertical = 7.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("No face detected in photo", color = StudioColors.TextPrimary, fontSize = 11.5.sp)
                            Text(
                                "Use Quick Fix →",
                                color = StudioColors.Sky,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.clickable { onUnblurModeChange(UnblurMode.QUICK_FIX) }
                            )
                        }
                    }
                    Button(
                        onClick = onRunUnblur,
                        enabled = !state.isProcessing,
                        modifier = Modifier.fillMaxWidth().height(44.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = StudioColors.Sky,
                            contentColor = StudioColors.OnSky
                        )
                    ) {
                        val cta = if (state.unblurMode == UnblurMode.QUICK_FIX) {
                            "Fix Motion Blur (NAFNet)"
                        } else {
                            "Detect & Restore Face (YuNet + GFPGAN)"
                        }
                        Text(cta, fontWeight = FontWeight.ExtraBold)
                    }
                }

                StudioTab.ERASER -> {
                    SegmentedPair(
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
                        Text(state.maskStatusText, color = StudioColors.Purple, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                    }
                    if (state.eraserTool == EraserTool.BRUSH) {
                        LabeledSlider(
                            label = "Brush Size",
                            value = state.brushSizePx,
                            valueRange = 12f..54f,
                            valueText = "${state.brushSizePx.roundToInt()} px",
                            tint = StudioColors.Purple,
                            onValueChange = onBrushSizeChange
                        )
                    }
                    if (state.afterBitmap != null) {
                        Button(
                            onClick = onEraseAnother,
                            modifier = Modifier.fillMaxWidth().height(44.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = StudioColors.Purple,
                                contentColor = StudioColors.OnPurple
                            )
                        ) {
                            Text("Erase Another Object", fontWeight = FontWeight.ExtraBold)
                        }
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = onClearMask,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.height(44.dp)
                            ) {
                                Text("Clear", color = StudioColors.TextPrimary, fontWeight = FontWeight.Bold)
                            }
                            Button(
                                onClick = onRunErase,
                                enabled = state.hasMask && !state.isProcessing,
                                modifier = Modifier.weight(1f).height(44.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = StudioColors.Purple,
                                    contentColor = StudioColors.OnPurple
                                )
                            ) {
                                Text("Erase Selection (MI-GAN)", fontWeight = FontWeight.ExtraBold)
                            }
                        }
                    }
                }
            }
        }

        // Material 3 3-Tab Bottom Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(68.dp)
                .background(Color(0xFF0E1118))
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            BottomNavTab(
                label = "Upscale",
                icon = Icons.Rounded.Hd,
                active = state.activeTab == StudioTab.UPSCALE,
                accent = StudioColors.Mint,
                onClick = { onSelectTab(StudioTab.UPSCALE) }
            )
            BottomNavTab(
                label = "Unblur & Face",
                icon = Icons.Rounded.FaceRetouchingNatural,
                active = state.activeTab == StudioTab.UNBLUR,
                accent = StudioColors.Sky,
                onClick = { onSelectTab(StudioTab.UNBLUR) }
            )
            BottomNavTab(
                label = "Magic Eraser",
                icon = Icons.Rounded.AutoFixHigh,
                active = state.activeTab == StudioTab.ERASER,
                accent = StudioColors.Purple,
                onClick = { onSelectTab(StudioTab.ERASER) }
            )
        }
    }
}

@Composable
private fun TopIconBtn(icon: ImageVector, desc: String, enabled: Boolean = true, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(StudioColors.Surface)
            .border(1.dp, StudioColors.Border, RoundedCornerShape(11.dp))
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
private fun SegmentedPair(
    leftLabel: String,
    rightLabel: String,
    leftSelected: Boolean,
    onSelectLeft: () -> Unit,
    onSelectRight: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(StudioColors.Bg)
            .border(1.dp, StudioColors.Border, RoundedCornerShape(12.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        SegOption(leftLabel, leftSelected, Modifier.weight(1f), onSelectLeft)
        SegOption(rightLabel, !leftSelected, Modifier.weight(1f), onSelectRight)
    }
}

@Composable
private fun SegOption(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .height(34.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(if (selected) StudioColors.Surface3 else Color.Transparent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (selected) StudioColors.TextPrimary else StudioColors.TextSecondary,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun LabeledSlider(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    valueText: String,
    tint: Color,
    onValueChange: (Float) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(label, color = StudioColors.TextSecondary, fontSize = 12.sp)
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            colors = SliderDefaults.colors(thumbColor = tint, activeTrackColor = tint),
            modifier = Modifier.weight(1f)
        )
        Text(valueText, color = StudioColors.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
    }
}

@Composable
private fun BottomNavTab(
    label: String,
    icon: ImageVector,
    active: Boolean,
    accent: Color,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier.clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .width(56.dp)
                .height(28.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(if (active) accent.copy(alpha = 0.18f) else Color.Transparent),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = label, tint = if (active) accent else StudioColors.TextMuted, modifier = Modifier.size(19.dp))
        }
        Text(
            text = label,
            color = if (active) StudioColors.TextPrimary else StudioColors.TextMuted,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
