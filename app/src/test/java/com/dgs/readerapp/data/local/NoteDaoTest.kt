package com.dgs.readerapp.data.local

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NoteDaoTest {

    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    private lateinit var database: AppDatabase
    private lateinit var dao: NoteDao

    @Before
    fun setup() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        dao = database.noteDao()
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun `insert sonra observeForBook notu doner`() = runTest {
        dao.insert(NoteEntity(bookId = "book1", position = 2, content = "İlk not"))
        val notes = dao.observeForBook("book1").first()
        assertEquals(1, notes.size)
        assertEquals("İlk not", notes[0].content)
    }

    @Test
    fun `notlar konuma ve olusturulma zamanina gore siralanir`() = runTest {
        dao.insert(NoteEntity(bookId = "book1", position = 5, content = "Beşinci"))
        dao.insert(NoteEntity(bookId = "book1", position = 1, content = "Birinci"))
        val notes = dao.observeForBook("book1").first()
        assertEquals(listOf("Birinci", "Beşinci"), notes.map { it.content })
    }

    @Test
    fun `update not icerigini degistirir`() = runTest {
        dao.insert(NoteEntity(bookId = "book1", position = 0, content = "Eski"))
        val note = dao.observeForBook("book1").first()[0]
        dao.update(note.copy(content = "Yeni"))
        val updated = dao.observeForBook("book1").first()
        assertEquals("Yeni", updated[0].content)
    }

    @Test
    fun `deleteById notu kaldirir`() = runTest {
        dao.insert(NoteEntity(bookId = "book1", position = 0, content = "Silinecek"))
        val note = dao.observeForBook("book1").first()[0]
        dao.deleteById(note.id)
        assertEquals(0, dao.observeForBook("book1").first().size)
    }

    @Test
    fun `farkli kitaplarin notlari birbirine karismaz`() = runTest {
        dao.insert(NoteEntity(bookId = "book1", position = 0, content = "K1 notu"))
        dao.insert(NoteEntity(bookId = "book2", position = 0, content = "K2 notu"))
        assertEquals(1, dao.observeForBook("book1").first().size)
        assertEquals(1, dao.observeForBook("book2").first().size)
    }
}
