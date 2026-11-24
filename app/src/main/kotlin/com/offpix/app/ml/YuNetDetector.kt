package com.offpix.app.ml

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import com.offpix.app.viewmodel.DetectedFaceOverlay
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

/**
 * Real OpenCV Zoo / litert-community YuNet (`yunet_fp16.tflite`) face + 5-landmark detector.
 *
 * Input contract:
 *   - 640x640 letterboxed BGR in [0, 255] float32.
 * Output contract (strides 8, 16, 32):
 *   - cls_8/16/32, obj_8/16/32, bbox_8/16/32, kps_8/16/32 (12 tensors total in standard YuNet export).
 *   - Decodes bounding boxes + 5 facial landmarks (right eye, left eye, nose, right mouth, left mouth)
 *     and applies IoU Non-Maximum Suppression (NMS).
 */
class YuNetDetector(private val runtime: LiteRtRuntime) {

    companion object {
        const val INPUT_SIZE = 640
        private val STRIDES = intArrayOf(8, 16, 32)
        const val DEFAULT_SCORE_THRESHOLD = 0.60f
        const val DEFAULT_NMS_THRESHOLD = 0.30f
    }

    data class LetterboxResult(
        val bitmap640: Bitmap,
        val scale: Float,
        val padX: Float,
        val padY: Float
    )

    suspend fun detect(
        source: Bitmap,
        scoreThreshold: Float = DEFAULT_SCORE_THRESHOLD,
        nmsThreshold: Float = DEFAULT_NMS_THRESHOLD
    ): List<DetectedFaceOverlay> {
        val loaded = runtime.getOrLoad(ModelId.YUNET)
        val model = loaded.model

        val lb = letterboxTo640(source)
        val inFloats = FloatArray(3 * INPUT_SIZE * INPUT_SIZE)
        packBgr255Nchw(lb.bitmap640, inFloats)

        val inputBuffers = model.createInputBuffers()
        val outputBuffers = model.createOutputBuffers()
        inputBuffers[0].writeFloat(inFloats)
        model.run(inputBuffers, outputBuffers)

        val outputs = outputBuffers.map { it.readFloat() }
        lb.bitmap640.recycle()

        val rawCandidates = if (outputs.size >= 12) {
            decodeMultiStrideOutputs(outputs, lb, scoreThreshold)
        } else {
            emptyList()
        }

        return applyNms(rawCandidates, nmsThreshold)
    }

    fun letterboxTo640(source: Bitmap): LetterboxResult {
        val scale = min(
            INPUT_SIZE.toFloat() / source.width.toFloat(),
            INPUT_SIZE.toFloat() / source.height.toFloat()
        )
        val newW = (source.width * scale).toInt().coerceAtLeast(1)
        val newH = (source.height * scale).toInt().coerceAtLeast(1)
        val padX = (INPUT_SIZE - newW) / 2f
        val padY = (INPUT_SIZE - newH) / 2f

        val canvasBmp = Bitmap.createBitmap(INPUT_SIZE, INPUT_SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(canvasBmp)
        canvas.drawColor(Color.BLACK)
        canvas.drawBitmap(
            source,
            Rect(0, 0, source.width, source.height),
            Rect(padX.toInt(), padY.toInt(), padX.toInt() + newW, padY.toInt() + newH),
            Paint(Paint.FILTER_BITMAP_FLAG)
        )
        return LetterboxResult(canvasBmp, scale, padX, padY)
    }

    /** Verified yunet_fp16.tflite input shape: [1, 3, 640, 640] NCHW BGR in [0, 255]. */
    private fun packBgr255Nchw(bmp: Bitmap, out: FloatArray) {
        val hw = INPUT_SIZE * INPUT_SIZE
        val pixels = IntArray(hw)
        bmp.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)
        for (i in 0 until hw) {
            val p = pixels[i]
            out[i] = (p and 0xFF).toFloat()                 // B plane
            out[hw + i] = ((p shr 8) and 0xFF).toFloat()    // G plane
            out[2 * hw + i] = ((p shr 16) and 0xFF).toFloat() // R plane
        }
    }

    /**
     * Decodes standard YuNet 12-tensor head layout:
     *   cls_8, cls_16, cls_32  (indices 0..2)
     *   obj_8, obj_16, obj_32  (indices 3..5)
     *   bbox_8, bbox_16, bbox_32 (indices 6..8)
     *   kps_8, kps_16, kps_32  (indices 9..11)
     */
    internal fun decodeMultiStrideOutputs(
        outputs: List<FloatArray>,
        lb: LetterboxResult,
        scoreThreshold: Float
    ): List<DetectedFaceOverlay> {
        val candidates = mutableListOf<DetectedFaceOverlay>()

        for (sIdx in STRIDES.indices) {
            val stride = STRIDES[sIdx]
            val cols = INPUT_SIZE / stride
            val rows = INPUT_SIZE / stride

            val cls = outputs[sIdx]
            val obj = outputs[3 + sIdx]
            val bbox = outputs[6 + sIdx]
            val kps = outputs[9 + sIdx]

            val cells = rows * cols
            if (cls.size < cells || obj.size < cells || bbox.size < cells * 4 || kps.size < cells * 10) {
                continue
            }

            for (r in 0 until rows) {
                for (c in 0 until cols) {
                    val idx = r * cols + c
                    val clsScore = cls[idx].coerceIn(0f, 1f)
                    val objScore = obj[idx].coerceIn(0f, 1f)
                    val score = kotlin.math.sqrt(clsScore * objScore)
                    if (score < scoreThreshold) continue

                    // Decode center & dimensions in 640x640 letterbox space
                    val cx = (c + bbox[idx * 4 + 0]) * stride
                    val cy = (r + bbox[idx * 4 + 1]) * stride
                    val w = exp(bbox[idx * 4 + 2].coerceIn(-10f, 10f)) * stride
                    val h = exp(bbox[idx * 4 + 3].coerceIn(-10f, 10f)) * stride

                    val left = ((cx - w / 2f) - lb.padX) / lb.scale
                    val top = ((cy - h / 2f) - lb.padY) / lb.scale
                    val right = ((cx + w / 2f) - lb.padX) / lb.scale
                    val bottom = ((cy + h / 2f) - lb.padY) / lb.scale

                    val landmarks = ArrayList<Pair<Float, Float>>(5)
                    for (k in 0 until 5) {
                        val lx640 = (c + kps[idx * 10 + k * 2]) * stride
                        val ly640 = (r + kps[idx * 10 + k * 2 + 1]) * stride
                        val lx = (lx640 - lb.padX) / lb.scale
                        val ly = (ly640 - lb.padY) / lb.scale
                        landmarks.add(lx to ly)
                    }

                    candidates.add(
                        DetectedFaceOverlay(
                            box = RectF(left, top, right, bottom),
                            landmarks = landmarks,
                            score = score
                        )
                    )
                }
            }
        }
        return candidates
    }

    internal fun applyNms(
        detections: List<DetectedFaceOverlay>,
        iouThreshold: Float
    ): List<DetectedFaceOverlay> {
        if (detections.size <= 1) return detections
        val sorted = detections.sortedByDescending { it.score }
        val kept = mutableListOf<DetectedFaceOverlay>()
        val suppressed = BooleanArray(sorted.size)

        for (i in sorted.indices) {
            if (suppressed[i]) continue
            val current = sorted[i]
            kept.add(current)
            for (j in i + 1 until sorted.size) {
                if (suppressed[j]) continue
                if (computeIoU(current.box, sorted[j].box) > iouThreshold) {
                    suppressed[j] = true
                }
            }
        }
        return kept
    }

    internal fun computeIoU(a: RectF, b: RectF): Float {
        val interLeft = max(a.left, b.left)
        val interTop = max(a.top, b.top)
        val interRight = min(a.right, b.right)
        val interBottom = min(a.bottom, b.bottom)
        val interW = max(0f, interRight - interLeft)
        val interH = max(0f, interBottom - interTop)
        val interArea = interW * interH
        if (interArea <= 0f) return 0f
        val areaA = max(0f, a.width()) * max(0f, a.height())
        val areaB = max(0f, b.width()) * max(0f, b.height())
        val union = (areaA + areaB - interArea).coerceAtLeast(1e-6f)
        return interArea / union
    }
}
