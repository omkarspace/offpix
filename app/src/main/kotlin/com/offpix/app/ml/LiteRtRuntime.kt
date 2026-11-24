package com.offpix.app.ml

import android.content.Context
import com.google.ai.edge.litert.Accelerator
import com.google.ai.edge.litert.CompiledModel
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.util.concurrent.Executors

enum class ModelId(val assetPath: String, val displayName: String) {
    REAL_ESRGAN("models/realesrgan_x4v3_fp16.tflite", "Real-ESRGAN x4v3"),
    NAFNET("models/nafnet_fp16.tflite", "NAFNet-GoPro-w32"),
    YUNET("models/yunet_fp16.tflite", "YuNet Face Detector"),
    GFPGAN("models/gfpgan_v14_opt.tflite", "GFPGAN v1.4"),
    SAM2_ENCODER("models/sam2_tiny_image_encoder_v2_fp16.tflite", "SAM 2.1 Encoder"),
    SAM2_DECODER("models/sam2_tiny_mask_decoder_v2_fp16.tflite", "SAM 2.1 Decoder"),
    MIGAN("models/migan_512_fp16.tflite", "MI-GAN 512 Places2")
}

data class LoadedModel(
    val id: ModelId,
    val model: CompiledModel,
    val accelerator: Accelerator
) : Closeable {
    override fun close() {
        model.close()
    }
}

/**
 * Single-thread confined LiteRT CompiledModel manager.
 *
 * - Enforces the universal hardware priority chain: NPU -> GPU (ML Drift) -> CPU (XNNPACK).
 * - Confines all OpenCL/Vulkan/NPU calls to one dedicated thread to prevent GPU context races.
 * - Explicitly closes inactive tab models so 4 GB budget Android devices never OOM.
 */
class LiteRtRuntime(private val appContext: Context) : Closeable {

    val inferenceDispatcher: ExecutorCoroutineDispatcher =
        Executors.newSingleThreadExecutor { r ->
            Thread(r, "LiteRtInferenceThread").apply { priority = Thread.NORM_PRIORITY }
        }.asCoroutineDispatcher()

    private val loaded = mutableMapOf<ModelId, LoadedModel>()

    var activeAcceleratorLabel: String = "GPU (ML Drift)"
        private set

    fun isModelAssetPresent(id: ModelId): Boolean = runCatching {
        appContext.assets.openFd(id.assetPath).use { true }
    }.getOrElse {
        runCatching { appContext.assets.open(id.assetPath).use { true } }.getOrDefault(false)
    }

    suspend fun getOrLoad(id: ModelId): LoadedModel = withContext(inferenceDispatcher) {
        loaded[id]?.let { return@withContext it }

        require(isModelAssetPresent(id)) {
            "Model file missing: ${id.assetPath}. Run `python scripts/fetch_models.py` before building."
        }

        val candidates = listOf(Accelerator.NPU, Accelerator.GPU, Accelerator.CPU)
        var lastError: Throwable? = null
        for (acc in candidates) {
            try {
                val compiled = CompiledModel.create(
                    appContext.assets,
                    id.assetPath,
                    CompiledModel.Options(acc)
                )
                val entry = LoadedModel(id, compiled, acc)
                loaded[id] = entry
                activeAcceleratorLabel = when (acc) {
                    Accelerator.NPU -> "NPU"
                    Accelerator.GPU -> "GPU (ML Drift)"
                    Accelerator.CPU -> "CPU (XNNPACK)"
                    else -> "CPU (XNNPACK)"
                }
                return@withContext entry
            } catch (t: Throwable) {
                lastError = t
            }
        }
        throw IllegalStateException("Failed to compile ${id.displayName} on NPU/GPU/CPU", lastError)
    }

    /**
     * Releases all loaded models except those in [keep], freeing GPU/RAM immediately on tab switch.
     */
    suspend fun releaseAllExcept(keep: Set<ModelId> = emptySet()) = withContext(inferenceDispatcher) {
        val toRemove = loaded.keys.filter { it !in keep }
        for (id in toRemove) {
            runCatching { loaded.remove(id)?.close() }
        }
    }

    override fun close() {
        loaded.values.forEach { runCatching { it.close() } }
        loaded.clear()
        inferenceDispatcher.close()
    }
}
