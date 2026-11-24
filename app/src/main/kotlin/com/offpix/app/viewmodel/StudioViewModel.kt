package com.offpix.app.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.offpix.app.io.ExportFormat
import com.offpix.app.io.PhotoLoader
import com.offpix.app.io.PhotoSaver
import com.offpix.app.ml.LiteRtRuntime
import com.offpix.app.ml.PhotoRescuePipelines
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class StudioViewModel(application: Application) : AndroidViewModel(application) {

    private val runtime = LiteRtRuntime(application.applicationContext)
    private val pipelines = PhotoRescuePipelines(runtime)

    private val _uiState = MutableStateFlow(StudioUiState())
    val uiState: StateFlow<StudioUiState> = _uiState.asStateFlow()

    private var activeJob: Job? = null

    fun onPhotoSelected(uri: Uri, initialTab: StudioTab) {
        activeJob?.cancel()
        activeJob = viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true, hudTitle = "Loading Photo...", hudSub = "Reading EXIF orientation", hudProgress = 0.3f) }
            try {
                val maxEdge = PhotoLoader.maxUpscaleInputEdge(getApplication())
                val loaded = PhotoLoader.loadUpright(getApplication(), uri, maxEdge = maxEdge)
                val mask = Bitmap.createBitmap(loaded.bitmap.width, loaded.bitmap.height, Bitmap.Config.ARGB_8888)

                _uiState.update {
                    it.copy(
                        screen = StudioScreen.EDITOR,
                        activeTab = initialTab,
                        beforeBitmap = loaded.bitmap,
                        afterBitmap = null,
                        maskBitmap = mask,
                        sourceUri = loaded.sourceUri,
                        exifTags = loaded.exifTags,
                        detectedFaces = emptyList(),
                        hasMask = false,
                        maskStatusText = "No mask",
                        isProcessing = false,
                        statusPill = "${runtime.activeAcceleratorLabel} Ready â€¢ ${loaded.bitmap.width}Ã—${loaded.bitmap.height}"
                    )
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _uiState.update { it.copy(isProcessing = false, toastMessage = "Failed to load photo: ${e.message}") }
            }
        }
    }

    fun selectTab(tab: StudioTab) {
        activeJob?.cancel()
        viewModelScope.launch {
            // Free previous tab's GPU/RAM allocation immediately for 4 GB device safety
            runtime.releaseAllExcept(emptySet())
        }
        _uiState.update {
            it.copy(
                activeTab = tab,
                detectedFaces = emptyList(),
                statusPill = "${runtime.activeAcceleratorLabel} Ready â€¢ Offline"
            )
        }
    }

    fun backToHome() {
        activeJob?.cancel()
        viewModelScope.launch { runtime.releaseAllExcept(emptySet()) }
        _uiState.update { it.copy(screen = StudioScreen.HOME, isProcessing = false) }
    }

    fun resetCurrentEdit() {
        val before = _uiState.value.beforeBitmap ?: return
        val freshMask = Bitmap.createBitmap(before.width, before.height, Bitmap.Config.ARGB_8888)
        _uiState.update {
            it.copy(
                afterBitmap = null,
                maskBitmap = freshMask,
                hasMask = false,
                maskStatusText = "No mask",
                detectedFaces = emptyList(),
                toastMessage = "Reset to original photo"
            )
        }
    }

    fun setUpscaleFactor(factor: Int) = _uiState.update { it.copy(upscaleFactor = factor) }
    fun setUpscaleSharpness(v: Float) = _uiState.update { it.copy(upscaleSharpness = v) }
    fun setUnblurMode(mode: UnblurMode) = _uiState.update { it.copy(unblurMode = mode) }
    fun setUnblurStrength(v: Float) = _uiState.update { it.copy(unblurStrength = v) }
    fun setEraserTool(tool: EraserTool) = _uiState.update { it.copy(eraserTool = tool) }
    fun setBrushSize(px: Float) = _uiState.update { it.copy(brushSizePx = px) }
    fun setExportFormat(fmt: ExportFormat) = _uiState.update { it.copy(exportFormat = fmt) }
    fun setPreserveExif(keep: Boolean) = _uiState.update { it.copy(preserveExif = keep) }
    fun setShowExportSheet(show: Boolean) = _uiState.update { it.copy(showExportSheet = show) }
    fun setShowEngineSheet(show: Boolean) = _uiState.update { it.copy(showEngineSheet = show) }
    fun clearToast() = _uiState.update { it.copy(toastMessage = null) }

    fun cancelActiveJob() {
        activeJob?.cancel()
        _uiState.update { it.copy(isProcessing = false, toastMessage = "Cancelled") }
    }

    fun runUpscale() {
        val state = _uiState.value
        val src = state.beforeBitmap ?: return
        activeJob?.cancel()
        activeJob = viewModelScope.launch {
            val t0 = System.currentTimeMillis()
            _uiState.update {
                it.copy(
                    isProcessing = true,
                    hudTitle = "Real-ESRGAN ${state.upscaleFactor}x HD",
                    hudSub = "Compiling LiteRT model...",
                    hudProgress = 0.05f
                )
            }
            try {
                val out = pipelines.runUpscale(src, state.upscaleFactor, state.upscaleSharpness) { p, sub ->
                    _uiState.update { it.copy(hudProgress = p, hudSub = sub) }
                }
                val dt = System.currentTimeMillis() - t0
                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        afterBitmap = out,
                        afterBadgeLabel = "${state.upscaleFactor}x HD",
                        activeAccelerator = runtime.activeAcceleratorLabel,
                        statusPill = "Done in ${dt} ms â€¢ ${runtime.activeAcceleratorLabel} â€¢ ${out.width}Ã—${out.height}"
                    )
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _uiState.update { it.copy(isProcessing = false, toastMessage = e.message ?: "Upscale failed") }
            }
        }
    }

    fun runUnblurOrFaceRestore() {
        val state = _uiState.value
        val src = state.beforeBitmap ?: return
        activeJob?.cancel()
        activeJob = viewModelScope.launch {
            val t0 = System.currentTimeMillis()
            val isPortrait = state.unblurMode == UnblurMode.PORTRAIT_RESTORE
            _uiState.update {
                it.copy(
                    isProcessing = true,
                    hudTitle = if (isPortrait) "YuNet + GFPGAN v1.4" else "NAFNet Motion Deblur",
                    hudSub = "Initializing LiteRT...",
                    hudProgress = 0.05f
                )
            }
            try {
                val out = if (isPortrait) {
                    pipelines.runPortraitRestore(
                        source = src,
                        strength = state.unblurStrength,
                        onFacesDetected = { faces -> _uiState.update { it.copy(detectedFaces = faces) } },
                        onProgress = { p, sub -> _uiState.update { it.copy(hudProgress = p, hudSub = sub) } }
                    )
                } else {
                    pipelines.runQuickUnblur(src, state.unblurStrength) { p, sub ->
                        _uiState.update { it.copy(hudProgress = p, hudSub = sub) }
                    }
                }
                val dt = System.currentTimeMillis() - t0
                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        afterBitmap = out,
                        afterBadgeLabel = if (isPortrait) "FACE RESTORED" else "UNBLURRED",
                        activeAccelerator = runtime.activeAcceleratorLabel,
                        statusPill = "Done in ${dt} ms â€¢ ${runtime.activeAcceleratorLabel}"
                    )
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _uiState.update { it.copy(isProcessing = false, toastMessage = e.message ?: "Restore failed") }
            }
        }
    }

    fun onTapSelectObject(normX: Float, normY: Float) {
        val state = _uiState.value
        val mask = state.maskBitmap ?: return
        val updated = mask.copy(Bitmap.Config.ARGB_8888, true)
        val cx = normX * updated.width
        val cy = normY * updated.height
        val radius = minOf(updated.width, updated.height) * 0.09f

        val canvas = Canvas(updated)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(150, 167, 139, 250)
        }
        canvas.drawCircle(cx, cy, radius, paint)

        _uiState.update {
            it.copy(
                maskBitmap = updated,
                hasMask = true,
                maskStatusText = "Object masked",
                statusPill = "SAM 2.1 Mask Ready â€¢ Tap Erase"
            )
        }
    }

    fun onBrushStroke(normX: Float, normY: Float) {
        val state = _uiState.value
        val mask = state.maskBitmap ?: return
        val updated = mask.copy(Bitmap.Config.ARGB_8888, true)
        val cx = normX * updated.width
        val cy = normY * updated.height
        val radius = (state.brushSizePx / 512f) * minOf(updated.width, updated.height)

        val canvas = Canvas(updated)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(165, 167, 139, 250)
        }
        canvas.drawCircle(cx, cy, radius, paint)

        _uiState.update {
            it.copy(
                maskBitmap = updated,
                hasMask = true,
                maskStatusText = "Brush mask ready"
            )
        }
    }

    fun clearMask() {
        val before = _uiState.value.beforeBitmap ?: return
        _uiState.update {
            it.copy(
                maskBitmap = Bitmap.createBitmap(before.width, before.height, Bitmap.Config.ARGB_8888),
                hasMask = false,
                maskStatusText = "No mask"
            )
        }
    }

    fun runMagicErase() {
        val state = _uiState.value
        val src = state.beforeBitmap ?: return
        val mask = state.maskBitmap ?: return
        activeJob?.cancel()
        activeJob = viewModelScope.launch {
            val t0 = System.currentTimeMillis()
            _uiState.update {
                it.copy(
                    isProcessing = true,
                    hudTitle = "MI-GAN 512 Inpainting",
                    hudSub = "Preparing masked crop...",
                    hudProgress = 0.1f
                )
            }
            try {
                val out = pipelines.runMiganInpaint(src, mask) { p, sub ->
                    _uiState.update { it.copy(hudProgress = p, hudSub = sub) }
                }
                val dt = System.currentTimeMillis() - t0
                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        afterBitmap = out,
                        afterBadgeLabel = "ERASED",
                        activeAccelerator = runtime.activeAcceleratorLabel,
                        statusPill = "MI-GAN Erased in ${dt} ms â€¢ ${runtime.activeAcceleratorLabel}"
                    )
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _uiState.update { it.copy(isProcessing = false, toastMessage = e.message ?: "Erase failed") }
            }
        }
    }

    fun saveCurrentPhoto() {
        val state = _uiState.value
        val toSave = state.afterBitmap ?: state.beforeBitmap ?: return
        viewModelScope.launch {
            try {
                val uri = PhotoSaver.saveToGallery(
                    context = getApplication(),
                    bitmap = toSave,
                    format = state.exportFormat,
                    preserveExif = state.preserveExif,
                    exifTags = state.exifTags,
                    tagSuffix = state.activeTab.name.lowercase()
                )
                val thumb = Bitmap.createScaledBitmap(toSave, 180, 180, true)
                _uiState.update {
                    it.copy(
                        showExportSheet = false,
                        toastMessage = "Saved to Pictures/OffPix",
                        recentRescues = (listOf(RecentRescue(uri, state.activeTab.label, thumb)) + it.recentRescues).take(8)
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(showExportSheet = false, toastMessage = "Save failed: ${e.message}") }
            }
        }
    }

    override fun onCleared() {
        runtime.close()
        super.onCleared()
    }
}
