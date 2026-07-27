package com.dgs.readerapp.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.palette.graphics.Palette
import org.beyka.tiffbitmapfactory.TiffBitmapFactory
import java.io.File
import java.io.FileOutputStream

/** Kapak dosya yolu ve kapaktan çıkarılan baskın renk (varsa) birlikte tutulur. */
data class CoverResult(val path: String, val accentColor: Int?)

/**
 * Kitap kapağı üretir ve uygulamanın cache dizinine kaydeder.
 * PDF -> ilk sayfa; TIFF -> ilk kare; EPUB -> ayrı olarak EpubParser
 * tarafından bulunan kapak dosyası kopyalanır (bkz. saveEpubCover).
 * Ayrıca Palette API ile kapaktan bir "aksan rengi" çıkarır; bu renk
 * kütüphane kartlarında o kitaba özgü kişiselleştirilmiş bir vurgu için
 * kullanılır (müzik uygulamalarındaki "kapaktan renk" tekniğine benzer).
 */
object CoverGenerator {

    private fun coverFile(context: Context, uriKey: String): File =
        File(context.cacheDir, "covers/${uriKey.hashCode()}.png")

    private fun extractAccentColor(bitmap: Bitmap): Int? = try {
        val palette = Palette.from(bitmap).maximumColorCount(12).generate()
        palette.vibrantSwatch?.rgb
            ?: palette.dominantSwatch?.rgb
            ?: palette.mutedSwatch?.rgb
    } catch (e: Exception) {
        null
    }

    fun generatePdfCover(context: Context, uri: Uri, uriKey: String): CoverResult? {
        return try {
            val out = coverFile(context, uriKey)
            out.parentFile?.mkdirs()
            var pfd: ParcelFileDescriptor? = null
            try {
                pfd = context.contentResolver.openFileDescriptor(uri, "r") ?: return null
                val renderer = PdfRenderer(pfd)
                if (renderer.pageCount == 0) {
                    renderer.close()
                    return null
                }
                val page = renderer.openPage(0)
                val bmp = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
                bmp.eraseColor(android.graphics.Color.WHITE)
                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()
                renderer.close()
                val accent = extractAccentColor(bmp)
                FileOutputStream(out).use { bmp.compress(Bitmap.CompressFormat.PNG, 90, it) }
                bmp.recycle()
                CoverResult(out.absolutePath, accent)
            } finally {
                pfd?.close()
            }
        } catch (e: Exception) {
            null
        }
    }

    fun generateTiffCover(context: Context, uri: Uri, uriKey: String): CoverResult? {
        return try {
            val tempFile = File(context.cacheDir, "tmp_cover_${uriKey.hashCode()}.tiff")
            context.contentResolver.openInputStream(uri)?.use { input ->
                tempFile.outputStream().use { output -> input.copyTo(output) }
            }
            val options = TiffBitmapFactory.Options().apply { inDirectoryNumber = 0 }
            val bmp = TiffBitmapFactory.decodeFile(tempFile, options)
            tempFile.delete()
            if (bmp == null) return null
            val accent = extractAccentColor(bmp)
            val out = coverFile(context, uriKey)
            out.parentFile?.mkdirs()
            FileOutputStream(out).use { bmp.compress(Bitmap.CompressFormat.PNG, 90, it) }
            bmp.recycle()
            CoverResult(out.absolutePath, accent)
        } catch (e: Exception) {
            null
        }
    }

    /** EPUB parser'ın bulduğu kapak resmi dosyasını kalıcı cache'e kopyalar ve rengini çıkarır. */
    fun saveEpubCover(context: Context, sourceFile: File, uriKey: String): CoverResult? {
        return try {
            val out = coverFile(context, uriKey)
            out.parentFile?.mkdirs()
            sourceFile.copyTo(out, overwrite = true)
            val bmp = BitmapFactory.decodeFile(out.absolutePath)
            val accent = bmp?.let { extractAccentColor(it) }
            bmp?.recycle()
            CoverResult(out.absolutePath, accent)
        } catch (e: Exception) {
            null
        }
    }
}
