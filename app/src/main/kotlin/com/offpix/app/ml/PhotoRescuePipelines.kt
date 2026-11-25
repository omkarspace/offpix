package com.offpix.app.ml

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import com.offpix.app.viewmodel.DetectedFaceOverlay
import kotlinx.coroutines.withContext

/**
 * High-level on-device AI pipelines backed by LiteRtRuntime (CompiledModel API).
 * Each runner reuses its TensorBuffers across tiles and executes strictly on
 * LiteRtRuntime.inferenceDispatcher.
 */
class PhotoRescuePipelines(private val runtime: LiteRtRuntime) {

    private val yuNetDetector = YuNetDetector(runtime)
    val sam2Runner = Sam2Runner(runtime)

    /** Tab 1: Real-ESRGAN General x4v3 (NHWC [1,128,128,3] -> NCHW [1,3,512,512]). */
    suspend fun runUpscale(
        source: Bitmap,
        scaleFactor: Int,
        sharpnessBlend: Float,
        onProgress: (Float, String) -> Unit
    ): Bitmap = withContext(runtime.inferenceDispatcher) {
        runtime.releaseAllExcept(setOf(ModelId.REAL_ESRGAN))
        val loaded = runtime.getOrLoad(ModelId.REAL_ESRGAN)
        val model = loaded.model

        val inputBuffers = model.createInputBuffers()
        val outputBuffers = model.createOutputBuffers()
        val inFloats = FloatArray(128 * 128 * 3)

        // Cap Real-ESRGAN 4x input longest edge to 576px (outputs crisp 2304px Ultra HD
        // in ~25 tiles = ~1-2s with 4x lower RAM instead of 100 tiles / 268 MB accumulators)
        val maxInEdge = if (scaleFactor == 4) 576 else 768
        val workSrc = if (maxOf(source.width, source.height) > maxInEdge) {
            val s = maxInEdge.toFloat() / maxOf(source.width, source.height).toFloat()
            Bitmap.createScaledBitmap(
                source,
                (source.width * s).toInt().coerceAtLeast(64),
                (source.height * s).toInt().coerceAtLeast(64),
                true
            )
        } else {
            source
        }

        val upscaled4x = TilingEngine.processTiled(
            source = workSrc,
            tileIn = 128,
            overlapIn = 16,
            scale = 4,
            onProgress = { done, total ->
                onProgress(done.toFloat() / total, "Tile $done/$total • 128×128 → 512×512")
            }
        ) { tile ->
            TensorUtils.bitmapToNhwcRgb01(tile, inFloats)
            inputBuffers[0].writeFloat(inFloats)
            model.run(inputBuffers, outputBuffers)
            val outFloats = outputBuffers[0].readFloat()
            TensorUtils.nchwRgb01ToBitmap(outFloats, 512, 512)
        }
        if (workSrc !== source) workSrc.recycle()

        val target = if (scaleFactor == 2) {
            Bitmap.createScaledBitmap(upscaled4x, workSrc.width * 2, workSrc.height * 2, true).also {
                if (it !== upscaled4x) upscaled4x.recycle()
            }
        } else {
            upscaled4x
        }

        TensorUtils.blendBitmaps(source, target, sharpnessBlend)
    }

    /** Tab 2 (Quick Fix): NAFNet-GoPro-width32 (NCHW [1,3,256,256] -> [1,3,256,256]). */
    suspend fun runQuickUnblur(
        source: Bitmap,
        strength: Float,
        onProgress: (Float, String) -> Unit
    ): Bitmap = withContext(runtime.inferenceDispatcher) {
        runtime.releaseAllExcept(setOf(ModelId.NAFNET))
        val loaded = runtime.getOrLoad(ModelId.NAFNET)
        val model = loaded.model

        val inputBuffers = model.createInputBuffers()
        val outputBuffers = model.createOutputBuffers()
        val inFloats = FloatArray(3 * 256 * 256)

        val deblurred = TilingEngine.processTiled(
            source = source,
            tileIn = 256,
            overlapIn = 32,
            scale = 1,
            onProgress = { done, total ->
                onProgress(done.toFloat() / total, "NAFNet tile $done/$total • 256×256")
            }
        ) { tile ->
            TensorUtils.bitmapToNchwRgb01(tile, inFloats)
            inputBuffers[0].writeFloat(inFloats)
            model.run(inputBuffers, outputBuffers)
            val outFloats = outputBuffers[0].readFloat()
            TensorUtils.nchwRgb01ToBitmap(outFloats, 256, 256)
        }

        TensorUtils.blendBitmaps(source, deblurred, strength)
    }

    /**
     * Tab 2 (Portrait Restore): YuNet face detection -> Umeyama 5-pt FFHQ warp -> GFPGAN v1.4.
     */
    suspend fun runPortraitRestore(
        source: Bitmap,
        strength: Float,
        onFacesDetected: (List<DetectedFaceOverlay>) -> Unit,
        onProgress: (Float, String) -> Unit
    ): Bitmap = withContext(runtime.inferenceDispatcher) {
        runtime.releaseAllExcept(setOf(ModelId.YUNET, ModelId.GFPGAN))

        onProgress(0.15f, "Detecting faces with YuNet (640×640 BGR)...")
        val faces = detectFacesWithYuNet(source)
        onFacesDetected(faces)
        if (faces.isEmpty()) return@withContext source

        val gfpgan = runtime.getOrLoad(ModelId.GFPGAN).model
        val inputBuffers = gfpgan.createInputBuffers()
        val outputBuffers = gfpgan.createOutputBuffers()
        val inFloats = FloatArray(3 * 512 * 512)

        val result = source.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)

        faces.forEachIndexed { index, face ->
            onProgress(
                0.35f + 0.6f * (index.toFloat() / faces.size),
                "Restoring face ${index + 1}/${faces.size} with GFPGAN v1.4..."
            )
            val aligned = FaceAligner.alignToFfhq512(source, face.landmarks)
            TensorUtils.bitmapToNchwRgbNeg1To1(aligned.crop512, inFloats)
            inputBuffers[0].writeFloat(inFloats)
            gfpgan.run(inputBuffers, outputBuffers)
            val outFloats = outputBuffers[0].readFloat()
            val restored512 = TensorUtils.nchwRgbNeg1To1ToBitmap(outFloats, 512, 512)
            FaceAligner.pasteBackSoftly(canvas, restored512, aligned.forwardMatrix, strength)
            aligned.crop512.recycle()
            restored512.recycle()
        }

        onProgress(1f, "Face restoration complete")
        result
    }

    /**
     * Tab 3: MI-GAN 512 crop-and-composite inpainting.
     * Crops 2x context around the user's mask, runs MI-GAN [1,4,512,512], and feathers back
     * so untouched regions keep 100% original resolution.
     */
    suspend fun runMiganInpaint(
        source: Bitmap,
        mask: Bitmap,
        onProgress: (Float, String) -> Unit
    ): Bitmap = withContext(runtime.inferenceDispatcher) {
        runtime.releaseAllExcept(setOf(ModelId.SAM2_ENCODER, ModelId.SAM2_DECODER, ModelId.MIGAN))

        onProgress(0.2f, "Computing 2× context crop around mask...")
        val cropRect = computeContextCropRect(mask, source.width, source.height)
            ?: return@withContext source

        val rgbCrop = Bitmap.createBitmap(
            source, cropRect.left, cropRect.top, cropRect.width(), cropRect.height()
        )
        val maskCrop = Bitmap.createBitmap(
            mask, cropRect.left, cropRect.top, cropRect.width(), cropRect.height()
        )
        val rgb512 = Bitmap.createScaledBitmap(rgbCrop, 512, 512, true)
        val mask512Raw = Bitmap.createScaledBitmap(maskCrop, 512, 512, true)
        val mask512 = TensorUtils.dilateMaskBitmap(mask512Raw, radiusPx = 6)
        mask512Raw.recycle()

        onProgress(0.55f, "Running MI-GAN 512 Places2 on GPU...")
        val migan = runtime.getOrLoad(ModelId.MIGAN).model
        val inputBuffers = migan.createInputBuffers()
        val outputBuffers = migan.createOutputBuffers()
        val inFloats = FloatArray(4 * 512 * 512)

        TensorUtils.packMigan4ChannelNchw(rgb512, mask512, inFloats)
        inputBuffers[0].writeFloat(inFloats)
        migan.run(inputBuffers, outputBuffers)
        val outFloats = outputBuffers[0].readFloat()
        val inpainted512 = TensorUtils.nchwRgbNeg1To1ToBitmap(outFloats, 512, 512)

        onProgress(0.85f, "Compositing patch onto full-res photo...")
        val restoredPatch = Bitmap.createScaledBitmap(inpainted512, cropRect.width(), cropRect.height(), true)
        val output = source.copy(Bitmap.Config.ARGB_8888, true)

        val origPx = IntArray(cropRect.width() * cropRect.height())
        val patchPx = IntArray(cropRect.width() * cropRect.height())
        val maskPx = IntArray(cropRect.width() * cropRect.height())
        output.getPixels(origPx, 0, cropRect.width(), cropRect.left, cropRect.top, cropRect.width(), cropRect.height())
        restoredPatch.getPixels(patchPx, 0, cropRect.width(), 0, 0, cropRect.width(), cropRect.height())
        maskCrop.getPixels(maskPx, 0, cropRect.width(), 0, 0, cropRect.width(), cropRect.height())

        for (i in origPx.indices) {
            val a = Color.alpha(maskPx[i]) / 255f
            if (a > 0.05f) {
                val inv = 1f - a
                val r = (Color.red(origPx[i]) * inv + Color.red(patchPx[i]) * a).toInt()
                val g = (Color.green(origPx[i]) * inv + Color.green(patchPx[i]) * a).toInt()
                val b = (Color.blue(origPx[i]) * inv + Color.blue(patchPx[i]) * a).toInt()
                origPx[i] = Color.argb(255, r, g, b)
            }
        }
        output.setPixels(origPx, 0, cropRect.width(), cropRect.left, cropRect.top, cropRect.width(), cropRect.height())

        rgbCrop.recycle(); maskCrop.recycle(); rgb512.recycle(); mask512.recycle()
        inpainted512.recycle(); restoredPatch.recycle()
        onProgress(1f, "Object removed")
        output
    }

    /**
     * Runs real OpenCV Zoo / litert-community YuNet face + 5-landmark detection
     * (`640x640` letterboxed BGR `[0, 255]`, multi-stride 8/16/32 + NMS).
     */
    private suspend fun detectFacesWithYuNet(source: Bitmap): List<DetectedFaceOverlay> {
        return yuNetDetector.detect(source)
    }

    private fun computeContextCropRect(mask: Bitmap, imgW: Int, imgH: Int): Rect? {
        val pixels = IntArray(mask.width * mask.height)
        mask.getPixels(pixels, 0, mask.width, 0, 0, mask.width, mask.height)
        var minX = mask.width; var minY = mask.height
        var maxX = -1; var maxY = -1

        for (y in 0 until mask.height) {
            val row = y * mask.width
            for (x in 0 until mask.width) {
                if (Color.alpha(pixels[row + x]) > 30) {
                    if (x < minX) minX = x
                    if (y < minY) minY = y
                    if (x > maxX) maxX = x
                    if (y > maxY) maxY = y
                }
            }
        }
        if (maxX < minX || maxY < minY) return null

        val bw = (maxX - minX + 1).coerceAtLeast(64)
        val bh = (maxY - minY + 1).coerceAtLeast(64)
        val side = maxOf(512, maxOf(bw, bh) * 2).coerceAtMost(minOf(imgW, imgH))
        val cx = (minX + maxX) / 2
        val cy = (minY + maxY) / 2

        val left = (cx - side / 2).coerceIn(0, (imgW - side).coerceAtLeast(0))
        val top = (cy - side / 2).coerceIn(0, (imgH - side).coerceAtLeast(0))
        return Rect(left, top, (left + side).coerceAtMost(imgW), (top + side).coerceAtMost(imgH))
    }
}
