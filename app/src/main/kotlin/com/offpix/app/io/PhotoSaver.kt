package com.offpix.app.io

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class ExportFormat(val mimeType: String, val ext: String, val compressFormat: Bitmap.CompressFormat) {
    JPEG_95("image/jpeg", "jpg", Bitmap.CompressFormat.JPEG),
    PNG_LOSSLESS("image/png", "png", Bitmap.CompressFormat.PNG)
}

object PhotoSaver {

    suspend fun saveToGallery(
        context: Context,
        bitmap: Bitmap,
        format: ExportFormat,
        preserveExif: Boolean,
        exifTags: Map<String, String>,
        tagSuffix: String
    ): Uri = withContext(Dispatchers.IO) {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val fileName = "OffPix_${tagSuffix}_$stamp.${format.ext}"

        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, format.mimeType)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/OffPix")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }

        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: error("Could not create MediaStore entry.")

        resolver.openOutputStream(uri)?.use { out ->
            val quality = if (format == ExportFormat.JPEG_95) 95 else 100
            if (!bitmap.compress(format.compressFormat, quality, out)) {
                error("Failed to encode bitmap.")
            }
        }

        // Copy preserved EXIF tags onto JPEG exports (orientation is already upright)
        if (preserveExif && format == ExportFormat.JPEG_95 && exifTags.isNotEmpty()) {
            resolver.openFileDescriptor(uri, "rw")?.use { pfd ->
                val exif = ExifInterface(pfd.fileDescriptor)
                exifTags.forEach { (k, v) -> exif.setAttribute(k, v) }
                exif.setAttribute(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL.toString()
                )
                exif.saveAttributes()
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        }

        uri
    }

    /**
     * Writes the rescued bitmap to app-private cache and returns a FileProvider URI
     * for offline Android Share Sheet intents (zero cloud upload).
     */
    suspend fun prepareShareUri(
        context: Context,
        bitmap: Bitmap,
        tagSuffix: String
    ): Uri = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "shared").apply { mkdirs() }
        // Keep cache tidy by removing old temporary share files
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, "OffPix_${tagSuffix}.jpg")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
        }
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }
}
