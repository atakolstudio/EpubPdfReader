package com.dgs.readerapp.data.repository

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.dgs.readerapp.data.CoverGenerator
import com.dgs.readerapp.data.local.AppDatabase
import com.dgs.readerapp.data.local.BookDao
import com.dgs.readerapp.data.local.BookEntity
import com.dgs.readerapp.data.local.BookType
import com.dgs.readerapp.data.queryDisplayName
import kotlinx.coroutines.flow.Flow
import java.io.File

/**
 * "Kitaplığım" için Repository katmanı (Clean Architecture / Repository Pattern).
 * Room DAO'yu sarmalar; kapak üretimi ve dosya meta verisi okuma gibi
 * I/O işlerini ViewModel'den soyutlar.
 */
class LibraryRepository(private val dao: BookDao) {

    fun observeBooks(): Flow<List<BookEntity>> = dao.observeAll()

    /** Dosya açıldığında/tekrar açıldığında çağrılır: kütüphaneye ekler veya günceller. */
    suspend fun recordOpened(
        context: Context,
        uri: Uri,
        type: String,
        epubCoverFile: File? = null
    ) {
        val uriKey = uri.toString()
        val existing = dao.getById(uriKey)
        val name = queryDisplayName(context, uri)
        val size = querySize(context, uri)
        val cover = existing?.coverPath ?: generateCover(context, uri, uriKey, type, epubCoverFile)

        val entity = (existing ?: BookEntity(
            id = uriKey,
            name = name,
            path = uriKey,
            type = type,
            addedAt = System.currentTimeMillis()
        )).copy(
            name = name,
            type = type,
            fileSizeBytes = size,
            coverPath = cover,
            lastOpened = System.currentTimeMillis()
        )
        dao.upsert(entity)
    }

    suspend fun updateProgress(uriKey: String, position: Int, total: Int) {
        if (dao.getById(uriKey) == null) return
        val progress = if (total > 0) position.toFloat() / total else 0f
        dao.updateProgress(uriKey, position, total, progress, System.currentTimeMillis())
    }

    suspend fun toggleFavorite(id: String, favorite: Boolean) = dao.setFavorite(id, favorite)

    suspend fun delete(id: String) = dao.deleteById(id)

    private fun generateCover(
        context: Context,
        uri: Uri,
        uriKey: String,
        type: String,
        epubCoverFile: File?
    ): String? = when (type) {
        BookType.PDF -> CoverGenerator.generatePdfCover(context, uri, uriKey)
        BookType.TIFF -> CoverGenerator.generateTiffCover(context, uri, uriKey)
        BookType.EPUB -> epubCoverFile?.let { CoverGenerator.saveEpubCover(context, it, uriKey) }
        else -> null
    }

    private fun querySize(context: Context, uri: Uri): Long {
        return try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (idx >= 0 && !cursor.isNull(idx)) cursor.getLong(idx) else 0L
                } else 0L
            } ?: 0L
        } catch (e: Exception) {
            0L
        }
    }

    companion object {
        fun create(context: Context): LibraryRepository =
            LibraryRepository(AppDatabase.getInstance(context).bookDao())
    }
}
