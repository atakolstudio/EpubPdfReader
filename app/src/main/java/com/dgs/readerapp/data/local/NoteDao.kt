package com.dgs.readerapp.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes WHERE bookId = :bookId ORDER BY position ASC, createdAt ASC")
    fun observeForBook(bookId: String): Flow<List<NoteEntity>>

    @Insert
    suspend fun insert(note: NoteEntity)

    @Update
    suspend fun update(note: NoteEntity)

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun deleteById(id: Long)
}
