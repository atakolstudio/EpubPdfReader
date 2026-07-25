package com.dgs.readerapp.data

import com.dgs.readerapp.data.local.BookEntity
import com.dgs.readerapp.data.local.BookType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * LibraryBackup, Android'in org.json sınıflarını kullanır; yerel JVM birim
 * testlerinde bu sınıflar sahte (stub) olduğundan, gerçek davranışları
 * simüle eden Robolectric kullanılır. Bilinen en yeni SDK sürümleriyle
 * olası uyumsuzluk riskini azaltmak için sabit/kararlı bir SDK'ya sabitlenir.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LibraryBackupTest {

    @Test
    fun `toJson ve fromJson tum alanlari kaybetmeden geri getirir`() {
        val original = BookEntity(
            id = "content://test/book1",
            name = "Test Kitap",
            path = "content://test/book1",
            type = BookType.EPUB,
            coverPath = "/cache/covers/1.png",
            lastPage = 4,
            totalPages = 20,
            progress = 0.2f,
            lastOpened = 1_700_000_000_000L,
            favorite = true,
            readingTimeMillis = 123_456L,
            fileSizeBytes = 987_654L,
            addedAt = 1_690_000_000_000L
        )

        val json = LibraryBackup.toJson(listOf(original))
        val restored = LibraryBackup.fromJson(json)

        assertEquals(1, restored.size)
        val book = restored[0]
        assertEquals(original.id, book.id)
        assertEquals(original.name, book.name)
        assertEquals(original.type, book.type)
        assertEquals(original.coverPath, book.coverPath)
        assertEquals(original.lastPage, book.lastPage)
        assertEquals(original.totalPages, book.totalPages)
        assertEquals(original.progress, book.progress, 0.001f)
        assertEquals(original.favorite, book.favorite)
        assertEquals(original.readingTimeMillis, book.readingTimeMillis)
        assertEquals(original.fileSizeBytes, book.fileSizeBytes)
        assertEquals(original.addedAt, book.addedAt)
    }

    @Test
    fun `coverPath null oldugunda dogru sekilde korunur`() {
        val original = BookEntity(id = "x", name = "N", path = "x", type = BookType.PDF, coverPath = null)
        val restored = LibraryBackup.fromJson(LibraryBackup.toJson(listOf(original)))
        assertNull(restored[0].coverPath)
    }

    @Test
    fun `bos dizi bos liste doner`() {
        assertEquals(0, LibraryBackup.fromJson("[]").size)
    }

    @Test
    fun `zorunlu alani eksik girdi sessizce atlanir, hata firlatmaz`() {
        val malformed = """[{"id":"sadece-id-var"}]"""
        val restored = LibraryBackup.fromJson(malformed)
        assertEquals(0, restored.size)
    }

    @Test
    fun `birden fazla kitap dogru sirayla korunur`() {
        val books = listOf(
            BookEntity(id = "a", name = "A", path = "a", type = BookType.PDF),
            BookEntity(id = "b", name = "B", path = "b", type = BookType.EPUB),
            BookEntity(id = "c", name = "C", path = "c", type = BookType.TIFF)
        )
        val restored = LibraryBackup.fromJson(LibraryBackup.toJson(books))
        assertEquals(listOf("a", "b", "c"), restored.map { it.id })
    }
}
