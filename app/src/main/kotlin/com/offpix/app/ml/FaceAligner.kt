package com.offpix.app.ml

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.Shader

/**
 * Umeyama 2D similarity alignment mapping YuNet's 5 detected facial landmarks
 * to the standard 512x512 FFHQ template required by GFPGAN v1.4, plus soft
 * elliptical inverse paste-back.
 */
object FaceAligner {

    // Standard FFHQ 512x512 5-point reference landmarks:
    // [right_eye, left_eye, nose_tip, right_mouth, left_mouth]
    private val FFHQ_512 = arrayOf(
        192.98138f to 239.94708f,
        318.90277f to 240.19360f,
        256.63416f to 314.01935f,
        201.26117f to 371.41043f,
        313.08905f to 371.15118f
    )

    data class AlignedFace(
        val crop512: Bitmap,
        val forwardMatrix: Matrix
    )

    fun alignToFfhq512(source: Bitmap, landmarks5: List<Pair<Float, Float>>): AlignedFace {
        require(landmarks5.size == 5) { "Expected exactly 5 facial landmarks from YuNet." }
        val matrix = estimateSimilarityMatrix(landmarks5, FFHQ_512.toList())
        val crop = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(crop)
        canvas.drawBitmap(source, matrix, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
        return AlignedFace(crop, matrix)
    }

    fun pasteBackSoftly(
        targetCanvas: Canvas,
        restored512: Bitmap,
        forwardMatrix: Matrix,
        strength: Float
    ) {
        val inverse = Matrix()
        if (!forwardMatrix.invert(inverse)) return

        // Build feathered elliptical mask so jawline/hair blend with zero square seams
        val feathered = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
        val fCanvas = Canvas(feathered)
        fCanvas.drawBitmap(restored512, 0f, 0f, null)

        val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
            shader = RadialGradient(
                256f, 256f, 245f,
                intArrayOf(Color.WHITE, Color.WHITE, Color.TRANSPARENT),
                floatArrayOf(0f, 0.78f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        fCanvas.drawRect(0f, 0f, 512f, 512f, maskPaint)

        val pastePaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG).apply {
            alpha = (strength.coerceIn(0f, 1f) * 255f).toInt()
        }
        targetCanvas.drawBitmap(feathered, inverse, pastePaint)
        feathered.recycle()
    }

    /**
     * Closed-form 2D similarity transform (scale, rotation, translation) minimizing
     * squared error between [src] and [dst] points.
     */
    private fun estimateSimilarityMatrix(
        src: List<Pair<Float, Float>>,
        dst: List<Pair<Float, Float>>
    ): Matrix {
        val n = src.size.toFloat()
        var meanSx = 0f; var meanSy = 0f
        var meanDx = 0f; var meanDy = 0f
        for (i in src.indices) {
            meanSx += src[i].first; meanSy += src[i].second
            meanDx += dst[i].first; meanDy += dst[i].second
        }
        meanSx /= n; meanSy /= n
        meanDx /= n; meanDy /= n

        var varS = 0f
        var a = 0f
        var b = 0f
        for (i in src.indices) {
            val sx = src[i].first - meanSx
            val sy = src[i].second - meanSy
            val dx = dst[i].first - meanDx
            val dy = dst[i].second - meanDy
            varS += sx * sx + sy * sy
            a += sx * dx + sy * dy
            b += sx * dy - sy * dx
        }
        val denom = varS.coerceAtLeast(1e-6f)
        val cosS = a / denom
        val sinS = b / denom
        val tx = meanDx - (cosS * meanSx - sinS * meanSy)
        val ty = meanDy - (sinS * meanSx + cosS * meanSy)

        return Matrix().apply {
            setValues(
                floatArrayOf(
                    cosS, -sinS, tx,
                    sinS, cosS, ty,
                    0f, 0f, 1f
                )
            )
        }
    }
}
