package com.offpix.app.io

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.offpix.app.viewmodel.RecentRescue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Local-only SharedPreferences store persisting recently saved MediaStore URIs
 * across app launches (zero network, zero external tracking).
 */
object RecentRescuesStore {
    private const val PREFS_NAME = "offpix_recents"
    private const val KEY_ITEMS = "recent_items_v1"
    private const val MAX_ITEMS = 8

    fun record(context: Context, uri: Uri, label: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val existing = runCatching { JSONArray(prefs.getString(KEY_ITEMS, "[]")) }
            .getOrDefault(JSONArray())

        val updated = JSONArray()
        updated.put(JSONObject().apply {
            put("uri", uri.toString())
            put("label", label)
        })

        for (i in 0 until existing.length()) {
            if (updated.length() >= MAX_ITEMS) break
            val obj = existing.optJSONObject(i) ?: continue
            if (obj.optString("uri") != uri.toString()) {
                updated.put(obj)
            }
        }
        prefs.edit().putString(KEY_ITEMS, updated.toString()).apply()
    }

    suspend fun loadAll(context: Context): List<RecentRescue> = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val arr = runCatching { JSONArray(prefs.getString(KEY_ITEMS, "[]")) }
            .getOrDefault(JSONArray())

        val results = mutableListOf<RecentRescue>()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val uriStr = obj.optString("uri")
            val label = obj.optString("label", "Rescued")
            val uri = runCatching { Uri.parse(uriStr) }.getOrNull() ?: continue
            val thumb = decodeThumbnail(context, uri) ?: continue
            results.add(RecentRescue(uri = uri, label = label, thumbnail = thumb))
        }
        results
    }

    private fun decodeThumbnail(context: Context, uri: Uri): Bitmap? = runCatching {
        val opts = BitmapFactory.Options().apply { inSampleSize = 4 }
        context.contentResolver.openInputStream(uri)?.use { stream ->
            val raw = BitmapFactory.decodeStream(stream, null, opts) ?: return@runCatching null
            Bitmap.createScaledBitmap(raw, 180, 180, true).also {
                if (it !== raw) raw.recycle()
            }
        }
    }.getOrNull()
}
