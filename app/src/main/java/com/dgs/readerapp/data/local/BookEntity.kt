package com.dgs.readerapp.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

object BookType {
    const val PDF = "pdf"
    const val EPUB = "epub"
    const val TIFF = "tiff"
}

@Entity(tableName = "books")
data class BookEntity(
    @PrimaryKey val id: String,        // content URI'sinin string hali (kararlı anahtar)
    val name: String,
    val path: String,                  // aynı content URI (spesifikasyonla uyum için ayrı tutulur)
    val type: String,                  // BookType.PDF / EPUB / TIFF
    val coverPath: String? = null,     // önbelleğe alınmış kapak görseli dosya yolu
    val lastPage: Int = 0,
    val totalPages: Int = 0,
    val progress: Float = 0f,          // 0f..1f
    val lastOpened: Long = 0L,
    val favorite: Boolean = false,
    val readingTimeMillis: Long = 0L,
    val fileSizeBytes: Long = 0L,
    val addedAt: Long = 0L
)
