package com.offpix.app.ui.editor

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.ZoomIn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.offpix.app.ui.theme.StudioColors
import com.offpix.app.viewmodel.DetectedFaceOverlay
import com.offpix.app.viewmodel.EraserTool
import com.offpix.app.viewmodel.StudioTab
import kotlin.math.roundToInt

@Composable
fun BeforeAfterStage(
    beforeBitmap: Bitmap?,
    afterBitmap: Bitmap?,
    maskBitmap: Bitmap?,
    activeTab: StudioTab,
    eraserTool: EraserTool,
    detectedFaces: List<DetectedFaceOverlay>,
    afterBadgeLabel: String,
    isProcessing: Boolean,
    hudTitle: String,
    hudSub: String,
    hudProgress: Float,
    statusPill: String,
    onTapMask: (normX: Float, normY: Float) -> Unit,
    onBrushStroke: (fromX: Float, fromY: Float, toX: Float, toY: Float) -> Unit,
    onCancelProcessing: () -> Unit,
    modifier: Modifier = Modifier
) {
    var splitFraction by remember(afterBitmap) { mutableFloatStateOf(0.5f) }
    var zoomScale by remember(beforeBitmap) { mutableFloatStateOf(1f) }
    var holdingOriginal by remember { mutableStateOf(false) }
    var boxSize by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current

    val hasAfter = afterBitmap != null
    // Match exact photo aspect ratio so tap & brush coordinates are 100% pixel-accurate
    val aspect = if (beforeBitmap != null && beforeBitmap.height > 0) {
        (beforeBitmap.width.toFloat() / beforeBitmap.height.toFloat()).coerceIn(0.45f, 2.2f)
    } else 1f

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Transparent)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .aspectRatio(aspect)
                .clip(RoundedCornerShape(22.dp))
                .background(Color(0xFF120C1A))
                .border(1.dp, StudioColors.Border, RoundedCornerShape(22.dp))
                .onSizeChanged { boxSize = it }
                // Single non-conflicting gesture handler per mode so Before/After drag & Magic Eraser never fight!
                .pointerInput(activeTab, eraserTool, hasAfter, boxSize) {
                    if (boxSize.width <= 0 || boxSize.height <= 0) return@pointerInput
                    if (activeTab == StudioTab.ERASER && !hasAfter) {
                        if (eraserTool == EraserTool.TAP_OBJECT) {
                            detectTapGestures { offset ->
                                onTapMask(
                                    (offset.x / boxSize.width).coerceIn(0f, 1f),
                                    (offset.y / boxSize.height).coerceIn(0f, 1f)
                                )
                            }
                        } else {
                            var prevNorm: Offset? = null
                            detectDragGestures(
                                onDragStart = { start ->
                                    val nx = (start.x / boxSize.width).coerceIn(0f, 1f)
                                    val ny = (start.y / boxSize.height).coerceIn(0f, 1f)
                                    prevNorm = Offset(nx, ny)
                                    onBrushStroke(nx, ny, nx, ny)
                                },
                                onDragEnd = { prevNorm = null },
                                onDragCancel = { prevNorm = null }
                            ) { change, _ ->
                                change.consume()
                                val nx = (change.position.x / boxSize.width).coerceIn(0f, 1f)
                                val ny = (change.position.y / boxSize.height).coerceIn(0f, 1f)
                                val prev = prevNorm ?: Offset(nx, ny)
                                onBrushStroke(prev.x, prev.y, nx, ny)
                                prevNorm = Offset(nx, ny)
                            }
                        }
                    } else if (hasAfter) {
                        detectDragGestures { change, _ ->
                            change.consume()
                            splitFraction = (change.position.x / boxSize.width).coerceIn(0.04f, 0.96f)
                        }
                    }
                }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = zoomScale
                        scaleY = zoomScale
                    }
            ) {
                // Base: BEFORE image
                if (beforeBitmap != null) {
                    Image(
                        bitmap = beforeBitmap.asImageBitmap(),
                        contentDescription = "Before",
                        contentScale = ContentScale.FillBounds,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Clipped: AFTER image (right side of divider)
                if (afterBitmap != null && !holdingOriginal && boxSize.width > 0) {
                    val clipWidthDp = with(density) {
                        (boxSize.width * (1f - splitFraction)).toDp()
                    }
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .width(clipWidthDp)
                            .fillMaxHeight()
                            .clipToBounds()
                    ) {
                        Image(
                            bitmap = afterBitmap.asImageBitmap(),
                            contentDescription = "After",
                            contentScale = ContentScale.FillBounds,
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .width(with(density) { boxSize.width.toDp() })
                                .fillMaxHeight()
                        )
                    }
                }

                // Magic Eraser Mask Overlay
                if (maskBitmap != null && activeTab == StudioTab.ERASER && !hasAfter) {
                    Image(
                        bitmap = maskBitmap.asImageBitmap(),
                        contentDescription = "Mask Overlay",
                        contentScale = ContentScale.FillBounds,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // YuNet Face Box & 5-Point Landmarks Overlay
                if (detectedFaces.isNotEmpty() && beforeBitmap != null) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val sx = size.width / beforeBitmap.width.toFloat()
                        val sy = size.height / beforeBitmap.height.toFloat()
                        val dash = PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f)
                        detectedFaces.forEach { face ->
                            drawRect(
                                color = StudioColors.Sky,
                                topLeft = Offset(face.box.left * sx, face.box.top * sy),
                                size = Size(face.box.width() * sx, face.box.height() * sy),
                                style = Stroke(width = 2.5.dp.toPx(), pathEffect = dash)
                            )
                            face.landmarks.forEach { (lx, ly) ->
                                drawCircle(
                                    color = StudioColors.Mint,
                                    radius = 4.dp.toPx(),
                                    center = Offset(lx * sx, ly * sy)
                                )
                            }
                        }
                    }
                }
            }

            // Before/After Divider, Properly Centered Knob, Zoom & Hold-to-Compare
            if (hasAfter && boxSize.width > 0) {
                if (holdingOriginal) {
                    TagBadge("ORIGINAL (HOLDING)", Color.White, Modifier.align(Alignment.TopStart).padding(10.dp))
                } else {
                    TagBadge("BEFORE", Color.White, Modifier.align(Alignment.TopStart).padding(10.dp))
                    TagBadge(afterBadgeLabel, StudioColors.Coral, Modifier.align(Alignment.TopEnd).padding(10.dp))

                    val lineX = (boxSize.width * splitFraction).roundToInt()
                    val halfKnobPx = with(density) { 17.dp.roundToPx() }
                    Box(
                        modifier = Modifier
                            .offset { IntOffset(lineX - 1, 0) }
                            .width(2.dp)
                            .fillMaxHeight()
                            .background(Color.White)
                    )
                    Box(
                        modifier = Modifier
                            .offset { IntOffset(lineX - halfKnobPx, (boxSize.height / 2) - halfKnobPx) }
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(Color.White),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("⇄", color = StudioColors.Bg, fontWeight = FontWeight.Black, fontSize = 14.sp)
                    }
                }

                // Bottom-left: Quick 1x / 2x / 3x Zoom toggle for inspecting 4x HD detail
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(10.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(StudioColors.GlassDark)
                        .border(1.dp, StudioColors.Border, RoundedCornerShape(999.dp))
                        .pointerInput(Unit) {
                            detectTapGestures {
                                zoomScale = when {
                                    zoomScale < 1.5f -> 2f
                                    zoomScale < 2.5f -> 3f
                                    else -> 1f
                                }
                            }
                        }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Rounded.ZoomIn, contentDescription = "Zoom", tint = StudioColors.TextPrimary, modifier = Modifier.size(14.dp))
                        Text("${zoomScale.roundToInt()}×", color = StudioColors.TextPrimary, fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // Bottom-right: Hold-to-Compare Eye Pill
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(10.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(StudioColors.GlassDark)
                        .border(1.dp, StudioColors.Border, RoundedCornerShape(999.dp))
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onPress = {
                                    holdingOriginal = true
                                    tryAwaitRelease()
                                    holdingOriginal = false
                                }
                            )
                        }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        Icon(Icons.Rounded.Visibility, contentDescription = "Hold to compare", tint = StudioColors.TextPrimary, modifier = Modifier.size(14.dp))
                        Text("Hold", color = StudioColors.TextPrimary, fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Live Processing HUD
            if (isProcessing) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xD00E0A14))
                        .padding(20.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(
                        color = StudioColors.Magenta,
                        strokeWidth = 3.dp,
                        modifier = Modifier.size(36.dp)
                    )
                    Text(
                        text = hudTitle,
                        color = StudioColors.TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                    Text(
                        text = hudSub,
                        color = StudioColors.TextSecondary,
                        fontSize = 11.5.sp,
                        modifier = Modifier.padding(top = 4.dp, bottom = 10.dp)
                    )
                    LinearProgressIndicator(
                        progress = { hudProgress.coerceIn(0f, 1f) },
                        color = StudioColors.Coral,
                        trackColor = Color.White.copy(alpha = 0.14f),
                        modifier = Modifier
                            .width(210.dp)
                            .height(6.dp)
                            .clip(RoundedCornerShape(99.dp))
                    )
                    TextButton(onClick = onCancelProcessing) {
                        Text("Cancel", color = StudioColors.TextSecondary, fontSize = 12.sp)
                    }
                }
            }
        }

        // Bottom Telemetry Pill
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .clip(RoundedCornerShape(999.dp))
                .background(StudioColors.GlassDark)
                .border(1.dp, StudioColors.Border, RoundedCornerShape(999.dp))
                .padding(horizontal = 12.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(StudioColors.Mint)
            )
            Text(statusPill, color = StudioColors.TextSecondary, fontSize = 11.sp)
        }
    }
}

@Composable
private fun TagBadge(text: String, tint: Color, modifier: Modifier) {
    Text(
        text = text,
        color = tint,
        fontSize = 10.sp,
        fontWeight = FontWeight.ExtraBold,
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(Color(0xB80E0A14))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    )
}
