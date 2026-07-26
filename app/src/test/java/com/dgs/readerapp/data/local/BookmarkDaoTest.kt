package com.dgs.readerapp.data.local

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BookmarkDaoTest {

    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    private lateinit var database: AppDatabase
    private lateinit var dao: BookmarkDao

    @Before
    fun setup() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        dao = database.bookmarkDao()
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun `insert sonra findAt yer imini bulur`() = runTest {
        dao.insert(BookmarkEntity(bookId = "book1", position = 3, label = "Bölüm 4"))
        val found = dao.findAt("book1", 3)
        assertNotNull(found)
        assertEquals("Bölüm 4", found?.label)
    }

    @Test
    fun `findAt olmayan konum icin null doner`() = runTest {
        assertNull(dao.findAt("book1", 99))
    }

    @Test
    fun `deleteAt sadece belirtilen konumu siler`() = runTest {
        dao.insert(BookmarkEntity(bookId = "book1", position = 1, label = "A"))
        dao.insert(BookmarkEntity(bookId = "book1", position = 2, label = "B"))
        dao.deleteAt("book1", 1)
        assertNull(dao.findAt("book1", 1))
        assertNotNull(dao.findAt("book1", 2))
    }

    @Test
    fun `farkli kitaplarin yer imleri birbirini etkilemez`() = runTest {
        dao.insert(BookmarkEntity(bookId = "book1", position = 0, label = "K1"))
        dao.insert(BookmarkEntity(bookId = "book2", position = 0, label = "K2"))
        assertNotNull(dao.findAt("book1", 0))
        assertNotNull(dao.findAt("book2", 0))
        dao.deleteAt("book1", 0)
        assertNull(dao.findAt("book1", 0))
        assertNotNull(dao.findAt("book2", 0))
    }
}
