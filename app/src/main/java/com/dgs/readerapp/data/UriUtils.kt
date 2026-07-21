package com.dgs.readerapp.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns

/** SAF content URI'sinden gösterilecek dosya adını okur (yoksa son path segmentine düşer). */
fun queryDisplayName(context: Context, uri: Uri): String {
    var name: String? = null
    try {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) name = cursor.getString(idx)
                }
            }
    } catch (e: Exception) {
        // yoksay, aşağıda alternatif isimle devam edilecek
    }
    return name ?: uri.lastPathSegment ?: "Kitap"
}
