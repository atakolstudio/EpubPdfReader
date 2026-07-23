package com.dgs.readerapp.epub

import java.io.File

enum class EpubFontFamily(val label: String, val cssValue: String) {
    DEFAULT("Varsayılan", "unset"),
    SERIF("Serif", "serif"),
    SANS_SERIF("Sans-serif", "sans-serif"),
    MONOSPACE("Monospace", "monospace")
}

/**
 * Her ayar değişikliğinde yeniden yazılan, tüm bölümlerin ortak <link> ile
 * referans verdiği CSS dosyası. JavaScript kullanılmadan yazı tipi/satır
 * aralığı özelleştirmesi bu şekilde sağlanır.
 */
fun writeReaderOverridesCss(extractDir: File, fontFamily: EpubFontFamily, lineHeight: Float) {
    val fontRule = if (fontFamily == EpubFontFamily.DEFAULT) {
        ""
    } else {
        "font-family: ${fontFamily.cssValue} !important;"
    }
    val css = """
        html, body, p, div, span, li, td { $fontRule line-height: $lineHeight !important; }
    """.trimIndent()
    try {
        File(extractDir, "__reader_overrides.css").writeText(css)
    } catch (e: Exception) {
        // Dosya yazılamazsa ayar sessizce uygulanmaz; okuma yine de bozulmaz.
    }
}
