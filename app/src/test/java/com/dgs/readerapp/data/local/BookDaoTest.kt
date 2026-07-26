package com.dgs.readerapp.data.local

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Bellek içi (in-memory) gerçek bir Room veritabanı ile BookDao'nun gerçek
 * SQL davranışını doğrular (upsert/güncelleme/silme/sıralama). Diskte kalıcı
 * dosya oluşturmaz, her testten sonra kapatılır.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BookDaoTest {

    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    private lateinit var database: AppDatabase
    private lateinit var dao: BookDao

    @Before
    fun setup() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        dao = database.bookDao()
    }

    @After
    fun teardown() {
        database.close()
    }

    private fun sampleBook(id: String = "book1") = BookEntity(
        id = id, name = "Test Kitap", path = id, type = BookType.EPUB
    )

    @Test
    fun `upsert sonra getById kitabi doner`() = runTest {
        dao.upsert(sampleBook())
        val result = dao.getById("book1")
        assertEquals("Test Kitap", result?.name)
    }

    @Test
    fun `getById olmayan kitap icin null doner`() = runTest {
        assertNull(dao.getById("yok"))
    }

    @Test
    fun `ayni id ile tekrar upsert gunceller, kopya olusturmaz`() = runTest {
        dao.upsert(sampleBook())
        dao.upsert(sampleBook().copy(name = "Güncellendi"))
        val all = dao.getAllOnce()
        assertEquals(1, all.size)
        assertEquals("Güncellendi", all[0].name)
    }

    @Test
    fun `setFavorite favori durumunu gunceller`() = runTest {
        dao.upsert(sampleBook())
        dao.setFavorite("book1", true)
        assertTrue(dao.getById("book1")?.favorite == true)
    }

    @Test
    fun `deleteById kitabi kaldirir`() = runTest {
        dao.upsert(sampleBook())
        dao.deleteById("book1")
        assertNull(dao.getById("book1"))
    }

    @Test
    fun `updateProgress ilerleme alanlarini dogru gunceller`() = runTest {
        dao.upsert(sampleBook())
        dao.updateProgress("book1", lastPage = 5, totalPages = 10, progress = 0.5f, lastOpened = 123L)
        val result = dao.getById("book1")
        assertEquals(5, result?.lastPage)
        assertEquals(10, result?.totalPages)
        assertEquals(0.5f, result?.progress)
        assertEquals(123L, result?.lastOpened)
    }

    @Test
    fun `addReadingTime mevcut sureye ekler, ustune yazmaz`() = runTest {
        dao.upsert(sampleBook())
        dao.addReadingTime("book1", 1000L)
        dao.addReadingTime("book1", 500L)
        assertEquals(1500L, dao.getById("book1")?.readingTimeMillis)
    }

    @Test
    fun `getAllOnce tum kitaplari doner`() = runTest {
        dao.upsert(sampleBook("a"))
        dao.upsert(sampleBook("b"))
        dao.upsert(sampleBook("c"))
        assertEquals(3, dao.getAllOnce().size)
    }
}
