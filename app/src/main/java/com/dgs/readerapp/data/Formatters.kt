package com.dgs.readerapp.data

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.ln
import kotlin.math.pow

fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "-"
    val units = arrayOf("B", "KB", "MB", "GB")
    val digitGroups = (ln(bytes.toDouble()) / ln(1024.0)).toInt().coerceIn(0, units.size - 1)
    val value = bytes / 1024.0.pow(digitGroups.toDouble())
    return String.format(Locale("tr"), "%.1f %s", value, units[digitGroups])
}

fun formatDate(millis: Long): String {
    if (millis <= 0) return "-"
    return SimpleDateFormat("dd.MM.yyyy HH:mm", Locale("tr")).format(Date(millis))
}
