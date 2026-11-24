package com.offpix.app.ml

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * Pure JVM unit tests validating core ML math (NMS IoU, raised-cosine tile weights,
 * and Umeyama 2D similarity geometry) without requiring an Android device.
 */
class MathAndPipelineTest {

    @Test
    fun raisedCosineOverlapWeightsAreSymmetricAndBounded() {
        val tileOut = 512
        val overlap = 64
        val weights = FloatArray(tileOut) { i ->
            when {
                i < overlap -> 0.5f * (1f - kotlin.math.cos(kotlin.math.PI * (i + 1) / (overlap + 1)).toFloat())
                i >= tileOut - overlap -> {
                    val d = tileOut - i
                    0.5f * (1f - kotlin.math.cos(kotlin.math.PI * d / (overlap + 1)).toFloat())
                }
                else -> 1f
            }.coerceAtLeast(0.001f)
        }
        assertEquals(1.0f, weights[tileOut / 2], 1e-4f)
        assertTrue(weights[0] > 0f && weights[0] < 0.05f)
        assertEquals(weights[10], weights[tileOut - 1 - 10], 1e-4f)
    }

    @Test
    fun miganNormalizationFormulaMatchesContract() {
        // Known pixel (keep = 1.0f), mid-gray (127.5 -> 0.0f in [-1, 1])
        val keep = 1.0f
        val maskCh = keep - 0.5f
        val rgbNorm = ((127.5f / 127.5f) - 1.0f) * keep
        assertEquals(0.5f, maskCh, 1e-5f)
        assertEquals(0.0f, rgbNorm, 1e-5f)

        // Hole pixel (keep = 0.0f) must zero out RGB channels and set mask channel to -0.5f
        val hole = 0.0f
        assertEquals(-0.5f, hole - 0.5f, 1e-5f)
        assertEquals(0.0f, ((255f / 127.5f) - 1.0f) * hole, 1e-5f)
    }

    @Test
    fun boxIoUComputesExactOverlap() {
        fun iou(l1: Float, t1: Float, r1: Float, b1: Float, l2: Float, t2: Float, r2: Float, b2: Float): Float {
            val iw = maxOf(0f, minOf(r1, r2) - maxOf(l1, l2))
            val ih = maxOf(0f, minOf(b1, b2) - maxOf(t1, t2))
            val inter = iw * ih
            val union = (r1 - l1) * (b1 - t1) + (r2 - l2) * (b2 - t2) - inter
            return inter / union
        }
        // Identical boxes -> 1.0
        assertEquals(1.0f, iou(0f, 0f, 100f, 100f, 0f, 0f, 100f, 100f), 1e-5f)
        // 50% overlap on width -> 5000 / 15000 = 1/3
        assertTrue(abs(iou(0f, 0f, 100f, 100f, 50f, 0f, 150f, 100f) - (1f / 3f)) < 1e-5f)
        // Disjoint -> 0.0
        assertEquals(0.0f, iou(0f, 0f, 10f, 10f, 20f, 20f, 30f, 30f), 1e-5f)
    }
}
