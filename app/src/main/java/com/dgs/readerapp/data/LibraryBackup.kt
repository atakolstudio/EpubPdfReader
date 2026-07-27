package com.dgs.readerapp.data

import com.dgs.readerapp.data.local.BookEntity
import org.json.JSONArray
import org.json.JSONObject

/** Kütüphane meta verisini (favori, ilerleme, okuma süresi) JSON'a aktarır/geri yükler. */
object LibraryBackup {

    fun toJson(books: List<BookEntity>): String {
        val arr = JSONArray()
        books.forEach { b ->
            arr.put(
                JSONObject().apply {
                    put("id", b.id)
                    put("name", b.name)
                    put("path", b.path)
                    put("type", b.type)
                    put("coverPath", b.coverPath ?: JSONObject.NULL)
                    put("accentColor", b.accentColor ?: JSONObject.NULL)
                    put("lastPage", b.lastPage)
                    put("totalPages", b.totalPages)
                    put("progress", b.progress.toDouble())
                    put("lastOpened", b.lastOpened)
                    put("favorite", b.favorite)
                    put("readingTimeMillis", b.readingTimeMillis)
                    put("fileSizeBytes", b.fileSizeBytes)
                    put("addedAt", b.addedAt)
                }
            )
        }
        return arr.toString()
    }

    fun fromJson(json: String): List<BookEntity> {
        val arr = JSONArray(json)
        return (0 until arr.length()).mapNotNull { i ->
            try {
                val o = arr.getJSONObject(i)
                BookEntity(
                    id = o.getString("id"),
                    name = o.getString("name"),
                    path = o.getString("path"),
                    type = o.getString("type"),
                    coverPath = if (o.isNull("coverPath")) null else o.optString("coverPath"),
                    accentColor = if (o.has("accentColor") && !o.isNull("accentColor")) o.optInt("accentColor") else null,
                    lastPage = o.optInt("lastPage", 0),
                    totalPages = o.optInt("totalPages", 0),
                    progress = o.optDouble("progress", 0.0).toFloat(),
                    lastOpened = o.optLong("lastOpened", 0L),
                    favorite = o.optBoolean("favorite", false),
                    readingTimeMillis = o.optLong("readingTimeMillis", 0L),
                    fileSizeBytes = o.optLong("fileSizeBytes", 0L),
                    addedAt = o.optLong("addedAt", 0L)
                )
            } catch (e: Exception) {
                null
            }
        }
    }
}
