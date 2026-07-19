package com.dgs.readerapp.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val LightColors = lightColorScheme(
    primary = KotlinPurple,
    onPrimary = OnPrimaryLight,
    secondary = KotlinBlue,
    tertiary = KotlinOrange,
    background = LightBackground,
    surface = LightSurface
)

private val DarkColors = darkColorScheme(
    primary = KotlinPurple,
    onPrimary = OnPrimaryDark,
    secondary = KotlinBlue,
    tertiary = KotlinOrange,
    background = DarkBackground,
    surface = DarkSurface
)

@Composable
fun EpubPdfReaderTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Android 12+ (S) cihazlarda dinamik renk (Material You) desteği
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        content = content
    )
}
