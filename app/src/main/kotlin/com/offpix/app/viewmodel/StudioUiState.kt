package com.offpix.app.viewmodel

import android.graphics.Bitmap
import android.graphics.RectF
import android.net.Uri
import com.offpix.app.io.ExportFormat

enum class StudioScreen { HOME, EDITOR }

enum class StudioTab(val label: String) {
    UPSCALE("Upscale"),
    UNBLUR("Unblur & Face"),
    ERASER("Magic Eraser")
}

enum class UnblurMode { QUICK_FIX, PORTRAIT_RESTORE }

enum class EraserTool { TAP_OBJECT, BRUSH }

data class DetectedFaceOverlay(
    val box: RectF,
    val landmarks: List<Pair<Float, Float>>,
    val score: Float
)

data class RecentRescue(
    val uri: Uri,
    val label: String,
    val thumbnail: Bitmap
)

data class StudioUiState(
    val screen: StudioScreen = StudioScreen.HOME,
    val activeTab: StudioTab = StudioTab.UPSCALE,

    // Active bitmaps
    val beforeBitmap: Bitmap? = null,
    val afterBitmap: Bitmap? = null,
    val maskBitmap: Bitmap? = null,
    val sourceUri: Uri? = null,
    val exifTags: Map<String, String> = emptyMap(),

    // Tab 1: Upscale
    val upscaleFactor: Int = 4,
    val upscaleSharpness: Float = 0.80f,

    // Tab 2: Unblur & Face
    val unblurMode: UnblurMode = UnblurMode.QUICK_FIX,
    val unblurStrength: Float = 0.85f,
    val detectedFaces: List<DetectedFaceOverlay> = emptyList(),

    // Tab 3: Magic Eraser
    val eraserTool: EraserTool = EraserTool.TAP_OBJECT,
    val brushSizePx: Float = 26f,
    val samEncoded: Boolean = false,
    val hasMask: Boolean = false,
    val maskStatusText: String = "No mask",

    // Processing HUD & Telemetry
    val isProcessing: Boolean = false,
    val hudTitle: String = "",
    val hudSub: String = "",
    val hudProgress: Float = 0f,
    val activeAccelerator: String = "GPU (ML Drift)",
    val statusPill: String = "100% Offline â€¢ Ready",
    val afterBadgeLabel: String = "AFTER",

    // Sheets & Feedback
    val showExportSheet: Boolean = false,
    val showEngineSheet: Boolean = false,
    val exportFormat: ExportFormat = ExportFormat.JPEG_95,
    val preserveExif: Boolean = true,
    val toastMessage: String? = null,
    val recentRescues: List<RecentRescue> = emptyList()
)
