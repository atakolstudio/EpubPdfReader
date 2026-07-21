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
 *  3) Varsa içindekiler tablosunu (EPUB3 nav.xhtml ya da EPUB2 toc.ncx) okur
 *  4) Tüm arşivi uygulamanın cache dizinine açar (WebView'in yerel
 *     dosyaları -relative resim/css yollarıyla birlikte- gösterebilmesi için)
 */
data class TocEntry(val title: String, val chapterIndex: Int)

data class EpubBook(
    val title: String,
    val chapters: List<File>,
    val extractDir: File,
    val toc: List<TocEntry> = emptyList()
)

private data class ManifestItem(val href: String, val properties: String?)

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
            zip.entries().toList().forEach { entry ->
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

        val opfResult = parseOpf(opfFile)
        val chapterFiles = opfResult.spineHrefs.map { File(opfDir, it) }.filter { it.exists() }
        val finalChapters = chapterFiles.ifEmpty { listOf(opfFile) }

        val toc = try {
            buildToc(opfResult, opfDir, finalChapters)
        } catch (e: Exception) {
            emptyList()
        }

        return EpubBook(
            title = opfResult.title.ifBlank { "Kitap" },
            chapters = finalChapters,
            extractDir = extractDir,
            toc = toc
        )
    }

    private fun buildToc(opfResult: OpfResult, opfDir: File, chapters: List<File>): List<TocEntry> {
        // EPUB3: manifest'te properties="nav" olan öge tercih edilir.
        val navHref = opfResult.manifest.values.firstOrNull {
            it.properties?.contains("nav") == true
        }?.href

        val rawEntries: List<Pair<String, String>>
        val sourceDir: File

        if (navHref != null) {
            val navFile = File(opfDir, navHref)
            sourceDir = navFile.parentFile ?: opfDir
            rawEntries = parseNavXhtml(navFile)
        } else {
            // EPUB2: spine'ın toc="..." attribute'u manifest'teki ncx ögesini gösterir.
            val ncxHref = opfResult.tocId?.let { opfResult.manifest[it]?.href }
            if (ncxHref == null) return emptyList()
            val ncxFile = File(opfDir, ncxHref)
            sourceDir = ncxFile.parentFile ?: opfDir
            rawEntries = parseNcx(ncxFile)
        }

        return rawEntries.mapNotNull { (hrefRaw, title) ->
            val href = Uri.decode(hrefRaw.substringBefore('#'))
            if (href.isBlank()) return@mapNotNull null
            val targetFile = File(sourceDir, href)
            val targetPath = safeCanonicalPath(targetFile)
            var index = chapters.indexOfFirst { safeCanonicalPath(it) == targetPath }
            if (index < 0) {
                index = chapters.indexOfFirst { it.name == targetFile.name }
            }
            if (index >= 0) TocEntry(title, index) else null
        }
    }

    private fun safeCanonicalPath(file: File): String =
        try { file.canonicalPath } catch (e: Exception) { file.path }

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

    private data class OpfResult(
        val title: String,
        val spineHrefs: List<String>,
        val manifest: Map<String, ManifestItem>,
        val tocId: String?
    )

    /** OPF içinden başlık, manifest (id->href/properties), spine sırası ve toc referansını çıkarır. */
    private fun parseOpf(opfFile: File): OpfResult {
        if (!opfFile.exists()) return OpfResult("", emptyList(), emptyMap(), null)

        val manifest = mutableMapOf<String, ManifestItem>()
        val spineIds = mutableListOf<String>()
        var title = ""
        var tocId: String? = null

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
                            val properties = parser.getAttributeValue(null, "properties")
                            if (id != null && href != null) {
                                manifest[id] = ManifestItem(Uri.decode(href), properties)
                            }
                        }
                        "itemref" -> {
                            parser.getAttributeValue(null, "idref")?.let { spineIds.add(it) }
                        }
                        "spine" -> {
                            tocId = parser.getAttributeValue(null, "toc")
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

        val hrefs = spineIds.mapNotNull { manifest[it]?.href }
        return OpfResult(title, hrefs, manifest, tocId)
    }

    /** EPUB3 nav.xhtml içindeki <a href="...">Başlık</a> ögelerini (iç içe etiketler dahil) çıkarır. */
    private fun parseNavXhtml(file: File): List<Pair<String, String>> {
        val result = mutableListOf<Pair<String, String>>()
        if (!file.exists()) return result
        val parser = xmlParserFor(file)
        var eventType = parser.eventType
        var currentHref: String? = null
        var depthInsideA = 0
        val titleBuilder = StringBuilder()
        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    if (depthInsideA == 0 && parser.name == "a") {
                        currentHref = parser.getAttributeValue(null, "href")
                        titleBuilder.clear()
                        depthInsideA = 1
                    } else if (depthInsideA > 0) {
                        depthInsideA++
                    }
                }
                XmlPullParser.TEXT -> {
                    if (depthInsideA > 0) titleBuilder.append(parser.text ?: "")
                }
                XmlPullParser.END_TAG -> {
                    if (depthInsideA > 0) {
                        if (parser.name == "a" && depthInsideA == 1) {
                            val href = currentHref
                            val title = titleBuilder.toString().trim().replace(Regex("\\s+"), " ")
                            if (href != null && title.isNotBlank()) result.add(href to title)
                            depthInsideA = 0
                            currentHref = null
                        } else {
                            depthInsideA--
                        }
                    }
                }
            }
            eventType = parser.next()
        }
        return result
    }

    /** EPUB2 toc.ncx içindeki <navPoint><navLabel><text>Başlık</text></navLabel><content src="..."/> ögelerini çıkarır. */
    private fun parseNcx(file: File): List<Pair<String, String>> {
        val result = mutableListOf<Pair<String, String>>()
        if (!file.exists()) return result
        val parser = xmlParserFor(file)
        var eventType = parser.eventType
        var inText = false
        var currentTitle = StringBuilder()
        var currentSrc: String? = null
        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    when (parser.name) {
                        "navPoint" -> {
                            currentTitle = StringBuilder()
                            currentSrc = null
                        }
                        "text" -> inText = true
                        "content" -> currentSrc = parser.getAttributeValue(null, "src")
                    }
                }
                XmlPullParser.TEXT -> {
                    if (inText) currentTitle.append(parser.text ?: "")
                }
                XmlPullParser.END_TAG -> {
                    when (parser.name) {
                        "text" -> inText = false
                        "navPoint" -> {
                            val title = currentTitle.toString().trim()
                            val src = currentSrc
                            if (src != null && title.isNotBlank()) result.add(src to title)
                        }
                    }
                }
            }
            eventType = parser.next()
        }
        return result
    }

    private fun xmlParserFor(file: File): XmlPullParser {
        val factory = org.xmlpull.v1.XmlPullParserFactory.newInstance()
        factory.isNamespaceAware = false
        val parser = factory.newPullParser()
        parser.setInput(file.inputStream(), "UTF-8")
        return parser
    }
}
