package com.dgs.readerapp.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.dgs.readerapp.data.local.BookType
import org.beyka.tiffbitmapfactory.TiffBitmapFactory
import java.io.File
import java.io.FileOutputStream

/**
 * Kitap kapağı üretir ve uygulamanın cache dizinine kaydeder.
 * PDF -> ilk sayfa; TIFF -> ilk kare; EPUB -> ayrı olarak EpubParser
 * tarafından bulunan kapak dosyası kopyalanır (bkz. saveEpubCover).
 */
object CoverGenerator {

    private fun coverFile(context: Context, uriKey: String): File =
        File(context.cacheDir, "covers/${uriKey.hashCode()}.png")

    fun generatePdfCover(context: Context, uri: Uri, uriKey: String): String? {
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
                FileOutputStream(out).use { bmp.compress(Bitmap.CompressFormat.PNG, 90, it) }
                bmp.recycle()
                out.absolutePath
            } finally {
                pfd?.close()
            }
        } catch (e: Exception) {
            null
        }
    }

    fun generateTiffCover(context: Context, uri: Uri, uriKey: String): String? {
        return try {
            val tempFile = File(context.cacheDir, "tmp_cover_${uriKey.hashCode()}.tiff")
            context.contentResolver.openInputStream(uri)?.use { input ->
                tempFile.outputStream().use { output -> input.copyTo(output) }
            }
            val options = TiffBitmapFactory.Options().apply { inDirectoryNumber = 0 }
            val bmp = TiffBitmapFactory.decodeFile(tempFile, options)
            tempFile.delete()
            if (bmp == null) return null
            val out = coverFile(context, uriKey)
            out.parentFile?.mkdirs()
            FileOutputStream(out).use { bmp.compress(Bitmap.CompressFormat.PNG, 90, it) }
            bmp.recycle()
            out.absolutePath
        } catch (e: Exception) {
            null
        }
    }

    /** EPUB parser'ın bulduğu kapak resmi dosyasını kalıcı cache'e kopyalar. */
    fun saveEpubCover(context: Context, sourceFile: File, uriKey: String): String? {
        return try {
            val out = coverFile(context, uriKey)
            out.parentFile?.mkdirs()
            sourceFile.copyTo(out, overwrite = true)
            out.absolutePath
        } catch (e: Exception) {
            null
        }
    }
}
