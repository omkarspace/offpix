package com.offpix.app.ml

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.PI

/**
 * Overlapped 2D tile processor with raised-cosine feathering so Real-ESRGAN (128->512)
 * and NAFNet (256->256) produce zero visible grid seams on smooth skies or skin tones.
 */
object TilingEngine {

    suspend fun processTiled(
        source: Bitmap,
        tileIn: Int,
        overlapIn: Int,
        scale: Int,
        onProgress: (done: Int, total: Int) -> Unit,
        processTile: (tileBitmap: Bitmap) -> Bitmap
    ): Bitmap {
        val strideIn = tileIn - overlapIn
        val tileOut = tileIn * scale
        val overlapOut = overlapIn * scale

        val outW = source.width * scale
        val outH = source.height * scale

        val cols = maxOf(1, ceil((source.width - overlapIn).toFloat() / strideIn).toInt())
        val rows = maxOf(1, ceil((source.height - overlapIn).toFloat() / strideIn).toInt())
        val totalTiles = rows * cols

        // Float accumulators for seamless weighted blending
        val accR = FloatArray(outW * outH)
        val accG = FloatArray(outW * outH)
        val accB = FloatArray(outW * outH)
        val accW = FloatArray(outW * outH)

        val weight1D = FloatArray(tileOut) { i ->
            when {
                overlapOut <= 0 -> 1f
                i < overlapOut -> 0.5f * (1f - cos(PI * (i + 1) / (overlapOut + 1)).toFloat())
                i >= tileOut - overlapOut -> {
                    val d = tileOut - i
                    0.5f * (1f - cos(PI * d / (overlapOut + 1)).toFloat())
                }
                else -> 1f
            }.coerceAtLeast(0.001f)
        }

        val tileBuf = Bitmap.createBitmap(tileIn, tileIn, Bitmap.Config.ARGB_8888)
        val tileCanvas = Canvas(tileBuf)
        val tilePixels = IntArray(tileOut * tileOut)
        val copyPaint = Paint(Paint.FILTER_BITMAP_FLAG)

        var done = 0
        for (r in 0 until rows) {
            val y0 = (r * strideIn).coerceAtMost((source.height - tileIn).coerceAtLeast(0))
            val srcH = minOf(tileIn, source.height - y0)

            for (c in 0 until cols) {
                currentCoroutineContext().ensureActive()

                val x0 = (c * strideIn).coerceAtMost((source.width - tileIn).coerceAtLeast(0))
                val srcW = minOf(tileIn, source.width - x0)

                tileCanvas.drawBitmap(
                    source,
                    Rect(x0, y0, x0 + srcW, y0 + srcH),
                    Rect(0, 0, srcW, srcH),
                    copyPaint
                )

                val outTile = processTile(tileBuf)
                outTile.getPixels(tilePixels, 0, tileOut, 0, 0, tileOut, tileOut)

                val ox0 = x0 * scale
                val oy0 = y0 * scale
                val validW = minOf(srcW * scale, outW - ox0)
                val validH = minOf(srcH * scale, outH - oy0)

                for (ty in 0 until validH) {
                    val wy = if ((r == 0 && ty < overlapOut) || (r == rows - 1 && ty >= validH - overlapOut)) 1f else weight1D[ty]
                    val dstRow = (oy0 + ty) * outW + ox0
                    val srcRow = ty * tileOut
                    for (tx in 0 until validW) {
                        val wx = if ((c == 0 && tx < overlapOut) || (c == cols - 1 && tx >= validW - overlapOut)) 1f else weight1D[tx]
                        val w = wx * wy
                        val px = tilePixels[srcRow + tx]
                        val di = dstRow + tx
                        accR[di] += ((px shr 16) and 0xFF) * w
                        accG[di] += ((px shr 8) and 0xFF) * w
                        accB[di] += (px and 0xFF) * w
                        accW[di] += w
                    }
                }
                outTile.recycle()
                done++
                onProgress(done, totalTiles)
            }
        }
        tileBuf.recycle()

        val finalPixels = IntArray(outW * outH)
        for (i in finalPixels.indices) {
            val w = accW[i].coerceAtLeast(1e-5f)
            val r = (accR[i] / w + 0.5f).toInt().coerceIn(0, 255)
            val g = (accG[i] / w + 0.5f).toInt().coerceIn(0, 255)
            val b = (accB[i] / w + 0.5f).toInt().coerceIn(0, 255)
            finalPixels[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }
        return Bitmap.createBitmap(finalPixels, outW, outH, Bitmap.Config.ARGB_8888)
    }
}
