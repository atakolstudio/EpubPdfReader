package com.dgs.readerapp.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {
    @Query("SELECT * FROM books ORDER BY lastOpened DESC")
    fun observeAll(): Flow<List<BookEntity>>

    @Query("SELECT * FROM books")
    suspend fun getAllOnce(): List<BookEntity>

    @Query("SELECT * FROM books WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): BookEntity?

    @Upsert
    suspend fun upsert(book: BookEntity)

    @Query("DELETE FROM books WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("UPDATE books SET favorite = :favorite WHERE id = :id")
    suspend fun setFavorite(id: String, favorite: Boolean)

    @Query(
        "UPDATE books SET lastPage = :lastPage, totalPages = :totalPages, " +
            "progress = :progress, lastOpened = :lastOpened WHERE id = :id"
    )
    suspend fun updateProgress(id: String, lastPage: Int, totalPages: Int, progress: Float, lastOpened: Long)

    @Query("UPDATE books SET readingTimeMillis = readingTimeMillis + :deltaMillis WHERE id = :id")
    suspend fun addReadingTime(id: String, deltaMillis: Long)
}
