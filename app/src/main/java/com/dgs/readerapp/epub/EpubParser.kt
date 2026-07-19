package com.dgs.readerapp.epub

import android.content.Context
import android.net.Uri
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.util.zip.ZipFile

/**
 * Basit EPUB (v2/v3) çözümleyici.
 * EPUB dosyası aslında bir ZIP arşividir. Bu sınıf:
 *  1) META-INF/container.xml içinden OPF dosyasının yolunu bulur
 *  2) OPF dosyasından manifest + spine (bölüm sırası) bilgisini okur
 *  3) Tüm arşivi uygulamanın cache dizinine açar (WebView'in yerel
 *     dosyaları -relative resim/css yollarıyla birlikte- gösterebilmesi için)
 */
data class EpubBook(
    val title: String,
    val chapters: List<File>,
    val extractDir: File
)

class EpubParser(private val context: Context) {

    fun parse(uri: Uri): EpubBook {
        val extractDir = File(context.cacheDir, "epub_cache/${uri.hashCode()}")
        if (extractDir.exists()) extractDir.deleteRecursively()
        extractDir.mkdirs()

        // Content URI -> geçici dosya (ZipFile rastgele erişim gerektirir)
        val tempZip = File(context.cacheDir, "tmp_${uri.hashCode()}.epub")
        context.contentResolver.openInputStream(uri)?.use { input ->
            tempZip.outputStream().use { output -> input.copyTo(output) }
        }

        ZipFile(tempZip).use { zip ->
            val entries = zip.entries().toList()
            entries.forEach { entry ->
                val outFile = File(extractDir, entry.name)
                if (entry.isDirectory) {
                    outFile.mkdirs()
                } else {
                    outFile.parentFile?.mkdirs()
                    zip.getInputStream(entry).use { input ->
                        outFile.outputStream().use { output -> input.copyTo(output) }
                    }
                }
            }
        }
        tempZip.delete()

        val containerFile = File(extractDir, "META-INF/container.xml")
        val opfPath = parseContainer(containerFile) ?: findFirstOpf(extractDir)
        val opfFile = File(extractDir, opfPath)
        val opfDir = opfFile.parentFile ?: extractDir

        val (title, chapterHrefs) = parseOpf(opfFile)
        val chapterFiles = chapterHrefs.map { File(opfDir, it) }.filter { it.exists() }

        return EpubBook(
            title = title.ifBlank { "Kitap" },
            chapters = chapterFiles.ifEmpty { listOf(opfFile) },
            extractDir = extractDir
        )
    }

    private fun parseContainer(file: File): String? {
        if (!file.exists()) return null
        val parser = xmlParserFor(file)
        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.START_TAG && parser.name == "rootfile") {
                return parser.getAttributeValue(null, "full-path")
            }
            eventType = parser.next()
        }
        return null
    }

    private fun findFirstOpf(dir: File): String {
        val opf = dir.walkTopDown().firstOrNull { it.extension == "opf" }
        return opf?.relativeTo(dir)?.path ?: "content.opf"
    }

    /** OPF içinden başlık, manifest (id->href) ve spine sırasını çıkarır. */
    private fun parseOpf(opfFile: File): Pair<String, List<String>> {
        if (!opfFile.exists()) return "" to emptyList()

        val manifest = mutableMapOf<String, String>() // id -> href
        val spineIds = mutableListOf<String>()
        var title = ""

        val parser = xmlParserFor(opfFile)
        var eventType = parser.eventType
        var inTitle = false
        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    when (parser.name) {
                        "item" -> {
                            val id = parser.getAttributeValue(null, "id")
                            val href = parser.getAttributeValue(null, "href")
                            if (id != null && href != null) manifest[id] = Uri.decode(href)
                        }
                        "itemref" -> {
                            parser.getAttributeValue(null, "idref")?.let { spineIds.add(it) }
                        }
                        "title" -> inTitle = true
                    }
                }
                XmlPullParser.TEXT -> {
                    if (inTitle && title.isBlank()) title = parser.text ?: ""
                }
                XmlPullParser.END_TAG -> {
                    if (parser.name == "title") inTitle = false
                }
            }
            eventType = parser.next()
        }

        val hrefs = spineIds.mapNotNull { manifest[it] }
        return title to hrefs
    }

    private fun xmlParserFor(file: File): XmlPullParser {
        val factory = org.xmlpull.v1.XmlPullParserFactory.newInstance()
        factory.isNamespaceAware = false
        val parser = factory.newPullParser()
        parser.setInput(file.inputStream(), "UTF-8")
        return parser
    }
}
