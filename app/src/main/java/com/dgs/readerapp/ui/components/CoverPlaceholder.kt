package com.dgs.readerapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import kotlin.math.abs

/**
 * Gerçek bir kapak resmi yokken gösterilen, kitabın adından türetilen
 * renkli/gradyan bir "Canva tarzı" kapak. Aynı isim her zaman aynı rengi
 * üretir (deterministik), böylece kütüphane görsel olarak canlı ve
 * ayırt edilebilir görünür.
 */
private val CoverPalette = listOf(
    Color(0xFF7F52FF) to Color(0xFF5B3FCC), // Kotlin moru
    Color(0xFFE44857) to Color(0xFFB22E3C), // Mercan kırmızısı
    Color(0xFF177EFB) to Color(0xFF0F5BC4), // Gökyüzü mavisi
    Color(0xFF00A389) to Color(0xFF00806A), // Zümrüt yeşili
    Color(0xFFFF9800) to Color(0xFFE07B00), // Turuncu
    Color(0xFF8E44AD) to Color(0xFF6C3483), // Eflatun
    Color(0xFF00BCD4) to Color(0xFF0097A7), // Turkuaz
    Color(0xFFEC407A) to Color(0xFFC2185B)  // Pembe
)

@Composable
fun CoverPlaceholder(title: String, modifier: Modifier = Modifier) {
    val index = abs(title.hashCode()) % CoverPalette.size
    val (start, end) = CoverPalette[index]
    val initial = title.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.linearGradient(listOf(start, end))),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initial,
            style = MaterialTheme.typography.headlineMedium,
            color = Color.White
        )
    }
}
