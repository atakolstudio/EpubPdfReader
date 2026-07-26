package com.dgs.readerapp.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bookId: String,     // BookEntity.id (uriKey) ile eşleşir
    val position: Int,      // notun eklendiği bölüm/sayfa index'i
    val content: String,
    val createdAt: Long = System.currentTimeMillis()
)
