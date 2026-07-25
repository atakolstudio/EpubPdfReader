package com.dgs.readerapp.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Saf Kotlin/JVM birim testleri (Android çerçevesine ihtiyaç duymaz,
 * bu yüzden Robolectric gerekmez, çok hızlı çalışır).
 */
class FormattersTest {

    @Test
    fun `formatFileSize sifir veya negatif icin tire doner`() {
        assertEquals("-", formatFileSize(0))
        assertEquals("-", formatFileSize(-100))
    }

    @Test
    fun `formatFileSize bayt degerini dogru bicimlendirir`() {
        assertEquals("500,0 B", formatFileSize(500))
    }

    @Test
    fun `formatFileSize kilobayt degerini dogru bicimlendirir`() {
        assertEquals("1,5 KB", formatFileSize(1536))
    }

    @Test
    fun `formatFileSize megabayt degerini dogru bicimlendirir`() {
        assertEquals("1,5 MB", formatFileSize(1_572_864))
    }

    @Test
    fun `formatDate sifir veya negatif icin tire doner`() {
        assertEquals("-", formatDate(0))
        assertEquals("-", formatDate(-1))
    }

    @Test
    fun `formatDate gecerli zaman damgasi icin bos olmayan metin doner`() {
        val result = formatDate(System.currentTimeMillis())
        assertNotEquals("-", result)
        assertTrue(result.isNotBlank())
    }

    @Test
    fun `formatDuration sifir veya negatif icin 0 dk doner`() {
        assertEquals("0 dk", formatDuration(0))
        assertEquals("0 dk", formatDuration(-500))
    }

    @Test
    fun `formatDuration sadece dakikalari dogru bicimlendirir`() {
        assertEquals("5 dk", formatDuration(5 * 60_000L))
    }

    @Test
    fun `formatDuration saat ve dakikayi dogru bicimlendirir`() {
        val twoHoursFifteenMinutes = 2 * 60 * 60_000L + 15 * 60_000L
        assertEquals("2 sa 15 dk", formatDuration(twoHoursFifteenMinutes))
    }
}
