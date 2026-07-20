package com.dgs.readerapp.data

import android.content.Context
import androidx.core.content.edit

/**
 * Basit okuma ilerlemesi deposu (SharedPreferences tabanlı).
 * Her dosya, kendi content URI'sinin string haline göre anahtarlanır;
 * aynı dosya tekrar açıldığında kaldığı bölüm/sayfa geri okunur.
 */
class ReadingProgressStore(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences("reading_progress", Context.MODE_PRIVATE)

    fun getEpubChapterIndex(uriKey: String): Int =
        prefs.getInt(epubKey(uriKey), 0)

    fun saveEpubChapterIndex(uriKey: String, chapterIndex: Int) {
        prefs.edit { putInt(epubKey(uriKey), chapterIndex) }
    }

    fun getPdfPageIndex(uriKey: String): Int =
        prefs.getInt(pdfKey(uriKey), 0)

    fun savePdfPageIndex(uriKey: String, pageIndex: Int) {
        prefs.edit { putInt(pdfKey(uriKey), pageIndex) }
    }

    fun getTiffPageIndex(uriKey: String): Int =
        prefs.getInt(tiffKey(uriKey), 0)

    fun saveTiffPageIndex(uriKey: String, pageIndex: Int) {
        prefs.edit { putInt(tiffKey(uriKey), pageIndex) }
    }

    private fun epubKey(uriKey: String) = "epub_chapter::$uriKey"
    private fun pdfKey(uriKey: String) = "pdf_page::$uriKey"
    private fun tiffKey(uriKey: String) = "tiff_page::$uriKey"
}
