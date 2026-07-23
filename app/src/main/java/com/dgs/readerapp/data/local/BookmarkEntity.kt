package com.dgs.readerapp.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "bookmarks")
data class BookmarkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bookId: String,     // BookEntity.id (uriKey) ile eşleşir
    val position: Int,      // EPUB: bölüm index'i, PDF/TIFF: sayfa index'i
    val label: String,
    val createdAt: Long = System.currentTimeMillis()
)
