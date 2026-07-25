package com.dgs.readerapp.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.dgs.readerapp.MainActivity
import com.dgs.readerapp.R
import com.dgs.readerapp.data.local.BookType

/**
 * Launcher simgesine uzun basınca çıkan "Devam Et" dinamik kısayolunu günceller.
 * Kullanıcı bu kısayola dokununca doğrudan en son açtığı kitaba gider
 * (ana ekrandan geçmeye gerek kalmadan) — güncel Android App Shortcuts kuralı.
 */
fun pushContinueReadingShortcut(context: Context, uri: Uri, type: String, title: String) {
    try {
        val mimeType = when (type) {
            BookType.PDF -> "application/pdf"
            BookType.EPUB -> "application/epub+zip"
            BookType.TIFF -> "image/tiff"
            else -> "*/*"
        }
        val intent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            setDataAndType(uri, mimeType)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val shortcut = ShortcutInfoCompat.Builder(context, "continue_reading")
            .setShortLabel("Devam Et")
            .setLongLabel("Devam et: $title")
            .setIcon(IconCompat.createWithResource(context, R.mipmap.ic_launcher))
            .setIntent(intent)
            .build()
        ShortcutManagerCompat.pushDynamicShortcut(context, shortcut)
    } catch (e: Exception) {
        // Kısayol güncellenemezse sessizce yok say; okuma deneyimini etkilemez.
    }
}
