package com.offpix.app.ml

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect

/**
 * Exact bitmap <-> tensor conversion helpers matching every model's verified I/O contract
 * in docs/MODEL_PIPELINE_SPEC.md.
 */
object TensorUtils {

    private val IMAGENET_MEAN = floatArrayOf(0.485f, 0.456f, 0.406f)
    private val IMAGENET_STD = floatArrayOf(0.229f, 0.224f, 0.225f)

    /** Real-ESRGAN input: [1, H, W, 3] NHWC, RGB in [0, 1]. */
    fun bitmapToNhwcRgb01(bmp: Bitmap, out: FloatArray) {
        val w = bmp.width
        val h = bmp.height
        val pixels = IntArray(w * h)
        bmp.getPixels(pixels, 0, w, 0, 0, w, h)
        var j = 0
        for (p in pixels) {
            out[j++] = ((p shr 16) and 0xFF) / 255f
            out[j++] = ((p shr 8) and 0xFF) / 255f
            out[j++] = (p and 0xFF) / 255f
        }
    }

    /** NAFNet input: [1, 3, H, W] NCHW, RGB in [0, 1]. */
    fun bitmapToNchwRgb01(bmp: Bitmap, out: FloatArray) {
        val w = bmp.width
        val h = bmp.height
        val hw = w * h
        val pixels = IntArray(hw)
        bmp.getPixels(pixels, 0, w, 0, 0, w, h)
        for (i in 0 until hw) {
            val p = pixels[i]
            out[i] = ((p shr 16) and 0xFF) / 255f
            out[hw + i] = ((p shr 8) and 0xFF) / 255f
            out[2 * hw + i] = (p and 0xFF) / 255f
        }
    }

    /** Real-ESRGAN & NAFNet output: [1, 3, H, W] NCHW in [0, 1] -> ARGB_8888 Bitmap. */
    fun nchwRgb01ToBitmap(data: FloatArray, w: Int, h: Int): Bitmap {
        val hw = w * h
        val pixels = IntArray(hw)
        for (i in 0 until hw) {
            val r = (data[i].coerceIn(0f, 1f) * 255f + 0.5f).toInt()
            val g = (data[hw + i].coerceIn(0f, 1f) * 255f + 0.5f).toInt()
            val b = (data[2 * hw + i].coerceIn(0f, 1f) * 255f + 0.5f).toInt()
            pixels[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }
        return Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888)
    }

    /** GFPGAN input: [1, 3, 512, 512] NCHW, RGB in [-1, 1]. */
    fun bitmapToNchwRgbNeg1To1(bmp: Bitmap, out: FloatArray) {
        val w = bmp.width
        val h = bmp.height
        val hw = w * h
        val pixels = IntArray(hw)
        bmp.getPixels(pixels, 0, w, 0, 0, w, h)
        for (i in 0 until hw) {
            val p = pixels[i]
            out[i] = (((p shr 16) and 0xFF) / 127.5f) - 1f
            out[hw + i] = (((p shr 8) and 0xFF) / 127.5f) - 1f
            out[2 * hw + i] = ((p and 0xFF) / 127.5f) - 1f
        }
    }

    /** GFPGAN & MI-GAN output: [1, 3, H, W] NCHW in [-1, 1] -> ARGB_8888 Bitmap. */
    fun nchwRgbNeg1To1ToBitmap(data: FloatArray, w: Int, h: Int): Bitmap {
        val hw = w * h
        val pixels = IntArray(hw)
        for (i in 0 until hw) {
            val r = ((data[i].coerceIn(-1f, 1f) + 1f) * 127.5f + 0.5f).toInt().coerceIn(0, 255)
            val g = ((data[hw + i].coerceIn(-1f, 1f) + 1f) * 127.5f + 0.5f).toInt().coerceIn(0, 255)
            val b = ((data[2 * hw + i].coerceIn(-1f, 1f) + 1f) * 127.5f + 0.5f).toInt().coerceIn(0, 255)
            pixels[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }
        return Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888)
    }

    /**
     * MI-GAN 512 input: [1, 4, 512, 512] NCHW = concat(mask - 0.5, rgb * mask)
     * Mask convention: 1.0 = keep (known), 0.0 = hole (erase).
     */
    fun packMigan4ChannelNchw(rgb512: Bitmap, mask512: Bitmap, out: FloatArray) {
        val hw = 512 * 512
        val rgbPx = IntArray(hw)
        val maskPx = IntArray(hw)
        rgb512.getPixels(rgbPx, 0, 512, 0, 0, 512, 512)
        mask512.getPixels(maskPx, 0, 512, 0, 0, 512, 512)

        for (i in 0 until hw) {
            // Alpha > 30 in UI mask bitmap means user marked this pixel to ERASE -> hole (0f)
            val keep = if (Color.alpha(maskPx[i]) > 30) 0f else 1f
            val p = rgbPx[i]
            val r = (((p shr 16) and 0xFF) / 127.5f) - 1f
            val g = (((p shr 8) and 0xFF) / 127.5f) - 1f
            val b = ((p and 0xFF) / 127.5f) - 1f

            out[i] = keep - 0.5f
            out[hw + i] = r * keep
            out[2 * hw + i] = g * keep
            out[3 * hw + i] = b * keep
        }
    }

    /** SAM 2.1 Encoder input: [1, 3, 1024, 1024] NCHW, ImageNet normalized RGB. */
    fun bitmapToImageNetNchw(bmp1024: Bitmap, out: FloatArray) {
        val hw = 1024 * 1024
        val pixels = IntArray(hw)
        bmp1024.getPixels(pixels, 0, 1024, 0, 0, 1024, 1024)
        for (i in 0 until hw) {
            val p = pixels[i]
            val r = ((p shr 16) and 0xFF) / 255f
            val g = ((p shr 8) and 0xFF) / 255f
            val b = (p and 0xFF) / 255f
            out[i] = (r - IMAGENET_MEAN[0]) / IMAGENET_STD[0]
            out[hw + i] = (g - IMAGENET_MEAN[1]) / IMAGENET_STD[1]
            out[2 * hw + i] = (b - IMAGENET_MEAN[2]) / IMAGENET_STD[2]
        }
    }

    /** Blends [enhanced] over [base] using [alpha] in [0, 1] (for Sharpness / Strength sliders). */
    fun blendBitmaps(base: Bitmap, enhanced: Bitmap, alpha: Float): Bitmap {
        if (alpha >= 0.99f) return enhanced
        val out = Bitmap.createBitmap(enhanced.width, enhanced.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawBitmap(base, null, Rect(0, 0, out.width, out.height), Paint(Paint.FILTER_BITMAP_FLAG))
        val p = Paint(Paint.FILTER_BITMAP_FLAG).apply { this.alpha = (alpha.coerceIn(0f, 1f) * 255f).toInt() }
        canvas.drawBitmap(enhanced, 0f, 0f, p)
        return out
    }
}
