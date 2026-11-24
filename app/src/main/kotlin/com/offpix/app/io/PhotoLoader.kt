package com.offpix.app.io

import android.app.ActivityManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class LoadedPhoto(
    val bitmap: Bitmap,
    val sourceUri: Uri,
    val originalWidth: Int,
    val originalHeight: Int,
    val exifTags: Map<String, String>
)

object PhotoLoader {

    private val PRESERVED_EXIF_TAGS = listOf(
        ExifInterface.TAG_DATETIME,
        ExifInterface.TAG_DATETIME_ORIGINAL,
        ExifInterface.TAG_MAKE,
        ExifInterface.TAG_MODEL,
        ExifInterface.TAG_FOCAL_LENGTH,
        ExifInterface.TAG_EXPOSURE_TIME,
        ExifInterface.TAG_F_NUMBER,
        ExifInterface.TAG_ISO_SPEED_RATINGS
    )

    /**
     * Returns the safe maximum input edge for 4x upscaling based on device RAM,
     * preventing OOM crashes on 4-6 GB budget Android phones.
     */
    fun maxUpscaleInputEdge(context: Context): Int {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        am.getMemoryInfo(memInfo)
        val totalGb = memInfo.totalMem / (1024L * 1024L * 1024L)
        return if (totalGb <= 6L) 1024 else 1536
    }

    suspend fun loadUpright(
        context: Context,
        uri: Uri,
        maxEdge: Int = 2048
    ): LoadedPhoto = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver

        // 1. Read EXIF orientation & safe metadata tags
        var orientation = ExifInterface.ORIENTATION_NORMAL
        val savedTags = mutableMapOf<String, String>()
        resolver.openInputStream(uri)?.use { stream ->
            val exif = ExifInterface(stream)
            orientation = exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
            for (tag in PRESERVED_EXIF_TAGS) {
                exif.getAttribute(tag)?.let { savedTags[tag] = it }
            }
        }

        // 2. Measure bounds without allocating pixels
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }

        val origW = bounds.outWidth.coerceAtLeast(1)
        val origH = bounds.outHeight.coerceAtLeast(1)
        var sampleSize = 1
        while ((origW / sampleSize) > maxEdge * 2 || (origH / sampleSize) > maxEdge * 2) {
            sampleSize *= 2
        }

        // 3. Decode ARGB_8888 bitmap
        val decodeOpts = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val raw = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, decodeOpts)
        } ?: error("Unable to decode selected image.")

        // 4. Rotate upright per EXIF and clamp to maxEdge
        val upright = applyExifOrientation(raw, orientation)
        val finalBitmap = clampMaxEdge(upright, maxEdge)

        LoadedPhoto(
            bitmap = finalBitmap,
            sourceUri = uri,
            originalWidth = origW,
            originalHeight = origH,
            exifTags = savedTags
        )
    }

    private fun applyExifOrientation(src: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            else -> return src
        }
        val rotated = Bitmap.createBitmap(src, 0, 0, src.width, src.height, matrix, true)
        if (rotated !== src) src.recycle()
        return rotated
    }

    private fun clampMaxEdge(src: Bitmap, maxEdge: Int): Bitmap {
        val longest = maxOf(src.width, src.height)
        if (longest <= maxEdge) return src
        val scale = maxEdge.toFloat() / longest.toFloat()
        val w = (src.width * scale).toInt().coerceAtLeast(1)
        val h = (src.height * scale).toInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(src, w, h, true)
        if (scaled !== src) src.recycle()
        return scaled
    }
}
