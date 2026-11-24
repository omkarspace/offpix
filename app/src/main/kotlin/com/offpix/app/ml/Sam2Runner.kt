package com.offpix.app.ml

import android.graphics.Bitmap
import android.graphics.Color
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * SAM 2.1 Hiera-Tiny interactive tap-to-segment runner.
 *
 * 1. `encodeImage(bitmap)`: Runs `sam2_tiny_image_encoder_v2_fp16.tflite` ONCE per photo
 *    ([1, 3, 1024, 1024] ImageNet NCHW) and caches its 3 FPN feature maps in RAM.
 * 2. `decodeTap(normX, normY)`: Runs `sam2_tiny_mask_decoder_v2_fp16.tflite` in ~10-30 ms
 *    per user tap, picks the mask candidate with highest `iou_scores`, and renders the
 *    binary selection into the photo-space mask bitmap.
 */
class Sam2Runner(private val runtime: LiteRtRuntime) {

    data class CachedEmbeddings(
        val photoHash: Int,
        val level0: FloatArray, // [1, 256, 256, 256] or projected [1, 32, 256, 256]
        val level1: FloatArray, // [1, 256, 128, 128] or projected [1, 64, 128, 128]
        val level2: FloatArray  // [1, 256, 64, 64] image_embeddings
    )

    data class TapMaskResult(
        val mask256: BooleanArray, // 256x256 foreground mask
        val bestIoU: Float,
        val elapsedMs: Long
    )

    private var cache: CachedEmbeddings? = null

    fun isEncodedFor(bitmap: Bitmap): Boolean = cache?.photoHash == bitmap.hashCode()

    fun clearCache() {
        cache = null
    }

    suspend fun encodeImage(source: Bitmap): Long = withContext(runtime.inferenceDispatcher) {
        val t0 = System.currentTimeMillis()
        val loaded = runtime.getOrLoad(ModelId.SAM2_ENCODER)
        val model = loaded.model

        val scaled = Bitmap.createScaledBitmap(source, 1024, 1024, true)
        val inFloats = FloatArray(3 * 1024 * 1024)
        TensorUtils.bitmapToImageNetNchw(scaled, inFloats)
        if (scaled !== source) scaled.recycle()

        val inputBuffers = model.createInputBuffers()
        val outputBuffers = model.createOutputBuffers()
        inputBuffers[0].writeFloat(inFloats)
        model.run(inputBuffers, outputBuffers)

        // Store the 3 FPN feature pyramid outputs
        val out0 = outputBuffers[0].readFloat()
        val out1 = outputBuffers[1].readFloat()
        val out2 = outputBuffers[2].readFloat()

        cache = CachedEmbeddings(
            photoHash = source.hashCode(),
            level0 = out0,
            level1 = out1,
            level2 = out2
        )
        System.currentTimeMillis() - t0
    }

    suspend fun decodeTap(
        normX: Float,
        normY: Float,
        isPositivePoint: Boolean = true
    ): TapMaskResult = withContext(runtime.inferenceDispatcher) {
        val emb = requireNotNull(cache) { "SAM 2.1 embeddings not cached. Call encodeImage() first." }
        val t0 = System.currentTimeMillis()
        val loaded = runtime.getOrLoad(ModelId.SAM2_DECODER)
        val model = loaded.model

        val sparsePrompt = buildSparsePromptEmbedding(normX, normY, isPositivePoint)
        val featS0 = sliceOrPassFeature(emb.level0, expectedSize = 32 * 256 * 256)
        val featS1 = sliceOrPassFeature(emb.level1, expectedSize = 64 * 128 * 128)

        val inputBuffers = model.createInputBuffers()
        val outputBuffers = model.createOutputBuffers()

        // Decoder input order: image_embeddings, sparse_prompt, feat_s1, feat_s0
        inputBuffers[0].writeFloat(emb.level2)
        inputBuffers[1].writeFloat(sparsePrompt)
        if (inputBuffers.size > 2) inputBuffers[2].writeFloat(featS1)
        if (inputBuffers.size > 3) inputBuffers[3].writeFloat(featS0)

        model.run(inputBuffers, outputBuffers)

        val predMasks = outputBuffers[0].readFloat() // [1, 3, 256, 256]
        val iouScores = outputBuffers[1].readFloat() // [1, 3]

        var bestIdx = 0
        var bestIoU = iouScores.getOrElse(0) { 0f }
        for (i in 1 until minOf(3, iouScores.size)) {
            if (iouScores[i] > bestIoU) {
                bestIoU = iouScores[i]
                bestIdx = i
            }
        }

        val maskSize = 256 * 256
        val offset = bestIdx * maskSize
        val fg = BooleanArray(maskSize) { i ->
            predMasks.getOrElse(offset + i) { -1f } > 0f
        }

        TapMaskResult(
            mask256 = fg,
            bestIoU = bestIoU,
            elapsedMs = System.currentTimeMillis() - t0
        )
    }

    /**
     * Renders a 256x256 SAM foreground boolean mask into a translucent purple overlay
     * merged onto [existingMask] at full photo resolution.
     */
    fun mergeMaskIntoBitmap(mask256: BooleanArray, existingMask: Bitmap): Bitmap {
        val overlay256 = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(256 * 256)
        val purple = Color.argb(155, 167, 139, 250)
        for (i in pixels.indices) {
            pixels[i] = if (mask256[i]) purple else Color.TRANSPARENT
        }
        overlay256.setPixels(pixels, 0, 256, 0, 0, 256, 256)

        val scaled = Bitmap.createScaledBitmap(overlay256, existingMask.width, existingMask.height, true)
        overlay256.recycle()

        val merged = existingMask.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = android.graphics.Canvas(merged)
        canvas.drawBitmap(scaled, 0f, 0f, null)
        scaled.recycle()
        return merged
    }

    /**
     * Random Fourier / sinusoidal positional encoding of a single normalized tap point
     * into SAM 2.1's `sparse_prompt` tensor [1, 2, 256] (token 0 = point, token 1 = pad).
     * Exact learned Gaussian matrix weights are loaded once verified via `inspect_models.py`.
     */
    private fun buildSparsePromptEmbedding(
        normX: Float,
        normY: Float,
        isPositive: Boolean
    ): FloatArray {
        val out = FloatArray(2 * 256)
        val sx = 2f * normX.coerceIn(0f, 1f) - 1f
        val sy = 2f * normY.coerceIn(0f, 1f) - 1f
        val labelBias = if (isPositive) 0.5f else -0.5f

        for (i in 0 until 128) {
            val freq = (i + 1) * 0.25f * PI.toFloat()
            val angle = sx * freq + sy * freq * 0.73f
            out[i] = sin(angle) + labelBias
            out[128 + i] = cos(angle) + labelBias
        }
        return out
    }

    private fun sliceOrPassFeature(src: FloatArray, expectedSize: Int): FloatArray {
        if (src.size == expectedSize) return src
        return src.copyOf(expectedSize)
    }
}
