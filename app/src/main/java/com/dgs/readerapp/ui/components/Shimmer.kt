package com.dgs.readerapp.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush

/**
 * Kapak/kart yüklenirken gösterilen kayan parlaklık (shimmer) efekti.
 * Kütüphane 10.000+ kitap içerdiğinde her kart yüklenirken kullanıcıya
 * "bir şeyler oluyor" hissi verir (Skeleton/Shimmer Loading).
 */
@Composable
fun Modifier.shimmerEffect(): Modifier {
    val base = MaterialTheme.colorScheme.surfaceVariant
    val highlight = MaterialTheme.colorScheme.surface
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateAnim by transition.animateFloat(
        initialValue = -600f,
        targetValue = 600f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerTranslate"
    )
    val brush = Brush.linearGradient(
        colors = listOf(base, highlight, base),
        start = Offset(translateAnim, 0f),
        end = Offset(translateAnim + 300f, 300f)
    )
    return this.background(brush)
}
