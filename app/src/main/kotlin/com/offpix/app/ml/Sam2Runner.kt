package com.offpix.app.ml

import android.graphics.Bitmap
import android.graphics.Color
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * SAM 2.1 Hiera-Tiny interactive tap-to-segment runner (v2 GPU-fixed models).
 *
 * Uses the exact 3,072-byte `models/prompt_encode_const.bin` constants
 * (`posmat [256]`, `pointEmbed [256]`, `notAPoint [256]`) from
 * `litert-community/SAM2.1-Hiera-Tiny-Mask-Decoder` for bit-exact point prompt encoding.
 */
class Sam2Runner(private val runtime: LiteRtRuntime) {

    companion object {
        private const val PROMPT_CONST_ASSET = "models/prompt_encode_const.bin"
        private const val IMG_SIZE = 1024f
        private const val EMB_SIZE = 256 * 64 * 64
        private const val FEAT_S1_SIZE = 64 * 128 * 128
        private const val FEAT_S0_SIZE = 32 * 256 * 256
    }

    private data class PromptConstants(
        val posmat: FloatArray,     // 256 floats [2, 128]
        val pointEmbed: FloatArray, // 256 floats
        val notAPoint: FloatArray   // 256 floats
    )

    data class CachedEmbeddings(
        val photoHash: Int,
        val imageEmbeddings: FloatArray, // [1, 256, 64, 64]
        val featS1: FloatArray,          // [1, 64, 128, 128]
        val featS0: FloatArray           // [1, 32, 256, 256]
    )

    data class TapMaskResult(
        val mask256: BooleanArray, // 256x256 foreground mask
        val bestIoU: Float,
        val elapsedMs: Long
    )

    private var cache: CachedEmbeddings? = null
    private var promptConsts: PromptConstants? = null

    fun isEncodedFor(bitmap: Bitmap): Boolean = cache?.photoHash == bitmap.hashCode()

    fun clearCache() {
        cache = null
    }

    private fun loadPromptConstants(): PromptConstants {
        promptConsts?.let { return it }
        val bytes = runtime.readAssetBytes(PROMPT_CONST_ASSET)
        require(bytes.size == 3072) { "Expected prompt_encode_const.bin to be 3072 bytes, got ${bytes.size}" }
        val fb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer()
        val all = FloatArray(768)
        fb.get(all)
        return PromptConstants(
            posmat = all.copyOfRange(0, 256),
            pointEmbed = all.copyOfRange(256, 512),
            notAPoint = all.copyOfRange(512, 768)
        ).also { promptConsts = it }
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

        val outs = outputBuffers.map { it.readFloat() }

        // Match outputs by exact tensor size so encoder output slot ordering never mismatches
        val imgEmb = outs.firstOrNull { it.size == EMB_SIZE } ?: outs.last()
        val featS1 = outs.firstOrNull { it.size == FEAT_S1_SIZE }
            ?: outs.getOrElse(1) { FloatArray(FEAT_S1_SIZE) }.copyOf(FEAT_S1_SIZE)
        val featS0 = outs.firstOrNull { it.size == FEAT_S0_SIZE }
            ?: outs.first().copyOf(FEAT_S0_SIZE)

        cache = CachedEmbeddings(
            photoHash = source.hashCode(),
            imageEmbeddings = imgEmb,
            featS1 = featS1,
            featS0 = featS0
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

        val sparsePrompt = encodePointPrompt(normX, normY)

        val inputBuffers = model.createInputBuffers()
        val outputBuffers = model.createOutputBuffers()

        // Verified sam2_tiny_mask_decoder_v2_fp16.tflite input order:
        //   [0] image_embeddings [1,256,64,64]
        //   [1] sparse_prompt    [1,2,256]
        //   [2] feat_s1          [1,64,128,128]
        //   [3] feat_s0          [1,32,256,256]
        inputBuffers[0].writeFloat(emb.imageEmbeddings)
        inputBuffers[1].writeFloat(sparsePrompt)
        if (inputBuffers.size > 2) inputBuffers[2].writeFloat(emb.featS1)
        if (inputBuffers.size > 3) inputBuffers[3].writeFloat(emb.featS0)

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
     * Bit-exact host-side SAM 2.1 point prompt encoder matching `sam2-1-tiny-click-to-segment`:
     *   a = 2 * ((px + 0.5) / 1024) - 1
     *   b = 2 * ((py + 0.5) / 1024) - 1
     *   token0[l]     = sin(2*pi*(a*posmat[l] + b*posmat[128+l])) + pointEmbed[l]
     *   token0[128+l] = cos(2*pi*(a*posmat[l] + b*posmat[128+l])) + pointEmbed[128+l]
     *   token1[0..255] = notAPoint[0..255]
     */
    private fun encodePointPrompt(normX: Float, normY: Float): FloatArray {
        val c = loadPromptConstants()
        val px = normX.coerceIn(0f, 1f) * IMG_SIZE
        val py = normY.coerceIn(0f, 1f) * IMG_SIZE
        val a = 2f * ((px + 0.5f) / IMG_SIZE) - 1f
        val b = 2f * ((py + 0.5f) / IMG_SIZE) - 1f

        val out = FloatArray(512)
        val twoPi = 2.0 * PI
        for (l in 0 until 128) {
            val u = (twoPi * (a * c.posmat[l] + b * c.posmat[128 + l])).toFloat()
            out[l] = sin(u) + c.pointEmbed[l]
            out[128 + l] = cos(u) + c.pointEmbed[128 + l]
        }
        System.arraycopy(c.notAPoint, 0, out, 256, 256)
        return out
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
}
