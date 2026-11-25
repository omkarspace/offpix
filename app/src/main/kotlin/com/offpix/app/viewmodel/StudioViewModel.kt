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
import com.offpix.app.io.RecentRescuesStore
import com.offpix.app.ml.LiteRtRuntime
import com.offpix.app.ml.ModelId
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
    private val undoStack = ArrayDeque<Bitmap?>()
    private val redoStack = ArrayDeque<Bitmap?>()

    init {
        viewModelScope.launch {
            val saved = RecentRescuesStore.loadAll(getApplication())
            val availability = ModelId.entries.associate { it.displayName to runtime.isModelAssetPresent(it) }
            _uiState.update {
                it.copy(
                    recentRescues = saved,
                    modelAvailability = availability
                )
            }
        }
    }

    fun clearRecentRescues() {
        RecentRescuesStore.clearAll(getApplication())
        _uiState.update { it.copy(recentRescues = emptyList(), toastMessage = "Recent history cleared") }
    }

    fun prepareShare(onUriReady: (Uri) -> Unit) {
        val state = _uiState.value
        val toShare = state.afterBitmap ?: state.beforeBitmap ?: return
        viewModelScope.launch {
            try {
                val uri = PhotoSaver.prepareShareUri(
                    context = getApplication(),
                    bitmap = toShare,
                    tagSuffix = state.activeTab.name.lowercase()
                )
                _uiState.update { it.copy(showExportSheet = false) }
                onUriReady(uri)
            } catch (e: Exception) {
                _uiState.update { it.copy(toastMessage = "Share failed: ${e.message}") }
            }
        }
    }

    private fun pushUndoSnapshot(currentAfter: Bitmap?) {
        undoStack.addLast(currentAfter)
        if (undoStack.size > 6) undoStack.removeFirst()
        redoStack.clear()
    }

    fun undoEdit() {
        if (undoStack.isEmpty()) return
        val current = _uiState.value.afterBitmap
        redoStack.addLast(current)
        val previous = undoStack.removeLast()
        _uiState.update {
            it.copy(
                afterBitmap = previous,
                canUndo = undoStack.isNotEmpty(),
                canRedo = redoStack.isNotEmpty(),
                toastMessage = if (previous == null) "Back to original" else "Undid last edit"
            )
        }
    }

    fun redoEdit() {
        if (redoStack.isEmpty()) return
        val current = _uiState.value.afterBitmap
        undoStack.addLast(current)
        val next = redoStack.removeLast()
        _uiState.update {
            it.copy(
                afterBitmap = next,
                canUndo = undoStack.isNotEmpty(),
                canRedo = redoStack.isNotEmpty(),
                toastMessage = "Redid edit"
            )
        }
    }

    fun onPhotoSelected(uri: Uri, initialTab: StudioTab) {
        activeJob?.cancel()
        pipelines.sam2Runner.clearCache()
        undoStack.clear()
        redoStack.clear()
        activeJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isProcessing = true,
                    hudTitle = "Loading Photo...",
                    hudSub = "Reading EXIF orientation",
                    hudProgress = 0.3f
                )
            }
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
                        samEncoded = false,
                        hasMask = false,
                        maskStatusText = "No mask",
                        isProcessing = false,
                        statusPill = "${runtime.activeAcceleratorLabel} Ready • ${loaded.bitmap.width}×${loaded.bitmap.height}"
                    )
                }
                if (initialTab == StudioTab.ERASER) {
                    preEncodeSamIfAvailable()
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _uiState.update { it.copy(isProcessing = false, toastMessage = "Failed to load photo: ${e.message}") }
            }
        }
    }

    fun selectTab(tab: StudioTab) {
        activeJob?.cancel()
        if (tab != StudioTab.ERASER) {
            pipelines.sam2Runner.clearCache()
        }
        viewModelScope.launch {
            // Free previous tab's GPU/RAM allocation immediately for 4 GB device safety
            runtime.releaseAllExcept(emptySet())
        }
        _uiState.update {
            it.copy(
                activeTab = tab,
                detectedFaces = emptyList(),
                samEncoded = false,
                statusPill = "${runtime.activeAcceleratorLabel} Ready • Offline"
            )
        }
        if (tab == StudioTab.ERASER) {
            preEncodeSamIfAvailable()
        }
    }

    private fun preEncodeSamIfAvailable() {
        val workingBitmap = _uiState.value.afterBitmap ?: _uiState.value.beforeBitmap ?: return
        if (pipelines.sam2Runner.isEncodedFor(workingBitmap)) {
            _uiState.update { it.copy(samEncoded = true, statusPill = "SAM 2.1 Ready • Tap any object") }
            return
        }
        if (!runtime.isModelAssetPresent(ModelId.SAM2_ENCODER)) return

        activeJob?.cancel()
        activeJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isProcessing = true,
                    hudTitle = "SAM 2.1 Image Encoder (1×)",
                    hudSub = "Caching 1024×1024 FPN embeddings...",
                    hudProgress = 0.45f
                )
            }
            try {
                val ms = pipelines.sam2Runner.encodeImage(workingBitmap)
                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        samEncoded = true,
                        activeAccelerator = runtime.activeAcceleratorLabel,
                        statusPill = "SAM 2.1 Ready (${ms} ms) • Tap any object"
                    )
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _uiState.update { it.copy(isProcessing = false, samEncoded = false, toastMessage = e.message) }
            }
        }
    }

    fun backToHome() {
        activeJob?.cancel()
        pipelines.sam2Runner.clearCache()
        viewModelScope.launch { runtime.releaseAllExcept(emptySet()) }
        _uiState.update { it.copy(screen = StudioScreen.HOME, isProcessing = false) }
    }

    fun resetCurrentEdit() {
        val before = _uiState.value.beforeBitmap ?: return
        pipelines.sam2Runner.clearCache()
        val freshMask = Bitmap.createBitmap(before.width, before.height, Bitmap.Config.ARGB_8888)
        _uiState.update {
            it.copy(
                afterBitmap = null,
                maskBitmap = freshMask,
                samEncoded = false,
                hasMask = false,
                maskStatusText = "No mask",
                detectedFaces = emptyList(),
                toastMessage = "Reset to original photo"
            )
        }
    }

    fun setUpscaleFactor(factor: Int) = _uiState.update { it.copy(upscaleFactor = factor) }
    fun setUpscaleSharpness(v: Float) = _uiState.update { it.copy(upscaleSharpness = v) }
    fun setUnblurMode(mode: UnblurMode) = _uiState.update { it.copy(unblurMode = mode, noFaceDetected = false) }
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
                pushUndoSnapshot(state.afterBitmap)
                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        afterBitmap = out,
                        canUndo = undoStack.isNotEmpty(),
                        canRedo = redoStack.isNotEmpty(),
                        afterBadgeLabel = "${state.upscaleFactor}x HD",
                        activeAccelerator = runtime.activeAcceleratorLabel,
                        statusPill = "Done in ${dt} ms • ${runtime.activeAcceleratorLabel} • ${out.width}×${out.height}"
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
                var foundAnyFace = true
                val out = if (isPortrait) {
                    pipelines.runPortraitRestore(
                        source = src,
                        strength = state.unblurStrength,
                        onFacesDetected = { faces ->
                            foundAnyFace = faces.isNotEmpty()
                            _uiState.update { it.copy(detectedFaces = faces, noFaceDetected = faces.isEmpty()) }
                        },
                        onProgress = { p, sub -> _uiState.update { it.copy(hudProgress = p, hudSub = sub) } }
                    )
                } else {
                    pipelines.runQuickUnblur(src, state.unblurStrength) { p, sub ->
                        _uiState.update { it.copy(hudProgress = p, hudSub = sub) }
                    }
                }
                if (isPortrait && !foundAnyFace) {
                    _uiState.update {
                        it.copy(
                            isProcessing = false,
                            noFaceDetected = true,
                            toastMessage = "No face found — try Quick Fix (NAFNet)"
                        )
                    }
                    return@launch
                }
                val dt = System.currentTimeMillis() - t0
                pushUndoSnapshot(state.afterBitmap)
                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        afterBitmap = out,
                        canUndo = undoStack.isNotEmpty(),
                        canRedo = redoStack.isNotEmpty(),
                        afterBadgeLabel = if (isPortrait) "FACE RESTORED" else "UNBLURRED",
                        activeAccelerator = runtime.activeAcceleratorLabel,
                        statusPill = "Done in ${dt} ms • ${runtime.activeAcceleratorLabel}"
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
        val workingBitmap = state.afterBitmap ?: state.beforeBitmap ?: return

        if (pipelines.sam2Runner.isEncodedFor(workingBitmap)) {
            activeJob?.cancel()
            activeJob = viewModelScope.launch {
                try {
                    val res = pipelines.sam2Runner.decodeTap(normX, normY, isPositivePoint = true)
                    val merged = pipelines.sam2Runner.mergeMaskIntoBitmap(res.mask256, mask)
                    _uiState.update {
                        it.copy(
                            maskBitmap = merged,
                            hasMask = true,
                            maskStatusText = "IoU ${"%.2f".format(res.bestIoU)} • ${res.elapsedMs} ms",
                            statusPill = "SAM 2.1 Selected (${res.elapsedMs} ms) • Tap Erase"
                        )
                    }
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    _uiState.update { it.copy(toastMessage = e.message ?: "SAM 2.1 decode failed") }
                }
            }
            return
        }

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
                maskStatusText = "Region masked",
                statusPill = "Mask Ready • Tap Erase"
            )
        }
    }

    fun onBrushStroke(fromX: Float, fromY: Float, toX: Float, toY: Float) {
        val state = _uiState.value
        val mask = state.maskBitmap ?: return
        val updated = mask.copy(Bitmap.Config.ARGB_8888, true)
        val x0 = fromX * updated.width
        val y0 = fromY * updated.height
        val x1 = toX * updated.width
        val y1 = toY * updated.height
        val radius = (state.brushSizePx / 512f) * minOf(updated.width, updated.height)

        val canvas = Canvas(updated)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(165, 167, 139, 250)
            style = Paint.Style.STROKE
            strokeWidth = radius * 2f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        canvas.drawLine(x0, y0, x1, y1, paint)

        _uiState.update {
            it.copy(
                maskBitmap = updated,
                hasMask = true,
                maskStatusText = "Brush mask ready"
            )
        }
    }

    /** Commits the latest erased photo as the new working base so the user can erase more objects. */
    fun commitEraseAndContinue() {
        val erased = _uiState.value.afterBitmap ?: return
        pipelines.sam2Runner.clearCache()
        val freshMask = Bitmap.createBitmap(erased.width, erased.height, Bitmap.Config.ARGB_8888)
        _uiState.update {
            it.copy(
                beforeBitmap = erased,
                afterBitmap = null,
                maskBitmap = freshMask,
                samEncoded = false,
                hasMask = false,
                maskStatusText = "No mask",
                statusPill = "Ready to erase another object"
            )
        }
        preEncodeSamIfAvailable()
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
        // Support iterative erasing on the latest erased photo
        val src = state.afterBitmap ?: state.beforeBitmap ?: return
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
                val freshMask = Bitmap.createBitmap(out.width, out.height, Bitmap.Config.ARGB_8888)
                pushUndoSnapshot(state.afterBitmap)
                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        afterBitmap = out,
                        canUndo = undoStack.isNotEmpty(),
                        canRedo = redoStack.isNotEmpty(),
                        maskBitmap = freshMask,
                        hasMask = false,
                        maskStatusText = "Erased!",
                        afterBadgeLabel = "ERASED",
                        activeAccelerator = runtime.activeAcceleratorLabel,
                        statusPill = "MI-GAN Erased in ${dt} ms • ${runtime.activeAcceleratorLabel}"
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
                RecentRescuesStore.record(getApplication(), uri, state.activeTab.label)
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
        pipelines.sam2Runner.clearCache()
        runtime.close()
        super.onCleared()
    }
}
