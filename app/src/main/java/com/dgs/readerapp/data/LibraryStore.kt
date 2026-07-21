package com.dgs.readerapp.data

import android.content.Context
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject

/** Bir dosya türünü belirtir (ikon ve nasıl açılacağı için kullanılır). */
object BookType {
    const val PDF = "pdf"
    const val EPUB = "epub"
    const val TIFF = "tiff"
}

data class RecentBook(
    val uriKey: String,
    val displayName: String,
    val type: String,
    val lastOpenedAt: Long,
    val position: Int = 0,
    val total: Int = 0
)

/**
 * "Kitaplığım" için son açılan dosyaların basit, JSON tabanlı kalıcı deposu.
 * Room gibi ağır bir bağımlılık yerine SharedPreferences + org.json (Android'de
 * hazır gelir, ekstra bağımlılık gerekmez) kullanılır.
 */
class LibraryStore(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences("library", Context.MODE_PRIVATE)

    fun getRecents(): List<RecentBook> {
        val raw = prefs.getString(KEY_RECENTS, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                RecentBook(
                    uriKey = o.getString("uriKey"),
                    displayName = o.getString("displayName"),
                    type = o.getString("type"),
                    lastOpenedAt = o.getLong("lastOpenedAt"),
                    position = o.optInt("position", 0),
                    total = o.optInt("total", 0)
                )
            }.sortedByDescending { it.lastOpenedAt }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** Dosya açıldığında çağrılır: listenin başına eklenir/güncellenir. */
    fun recordOpened(uriKey: String, displayName: String, type: String) {
        val existing = getRecents().firstOrNull { it.uriKey == uriKey }
        val updated = (existing ?: RecentBook(uriKey, displayName, type, 0)).copy(
            displayName = displayName,
            lastOpenedAt = System.currentTimeMillis()
        )
        save(getRecents().filterNot { it.uriKey == uriKey } + updated)
    }

    /** Okuma ilerledikçe (bölüm/sayfa) çağrılır; kitap listede yoksa yok sayılır. */
    fun updateProgress(uriKey: String, position: Int, total: Int) {
        val current = getRecents().toMutableList()
        val idx = current.indexOfFirst { it.uriKey == uriKey }
        if (idx >= 0) {
            current[idx] = current[idx].copy(position = position, total = total)
            save(current)
        }
    }

    fun remove(uriKey: String) {
        save(getRecents().filterNot { it.uriKey == uriKey })
    }

    private fun save(list: List<RecentBook>) {
        val trimmed = list.sortedByDescending { it.lastOpenedAt }.take(MAX_RECENTS)
        val arr = JSONArray()
        trimmed.forEach { b ->
            arr.put(
                JSONObject().apply {
                    put("uriKey", b.uriKey)
                    put("displayName", b.displayName)
                    put("type", b.type)
                    put("lastOpenedAt", b.lastOpenedAt)
                    put("position", b.position)
                    put("total", b.total)
                }
            )
        }
        prefs.edit { putString(KEY_RECENTS, arr.toString()) }
    }

    private companion object {
        const val KEY_RECENTS = "recents"
        const val MAX_RECENTS = 40
    }
}
