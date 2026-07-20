package com.dgs.readerapp

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.dgs.readerapp.epub.EpubViewerScreen
import com.dgs.readerapp.pdf.PdfViewerScreen
import com.dgs.readerapp.tiff.TiffViewerScreen
import com.dgs.readerapp.ui.theme.EpubPdfReaderTheme

private sealed class Screen {
    data object Home : Screen()
    data class Pdf(val uri: Uri) : Screen()
    data class Epub(val uri: Uri) : Screen()
    data class Tiff(val uri: Uri) : Screen()
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Modern kenardan kenara (edge-to-edge) görünüm - Android 15 (SDK 35+) zorunlu kılıyor
        enableEdgeToEdge()

        // Uygulama .pdf / .epub / .tiff dosyasıyla açıldıysa (VIEW intent) ilgili ekranı aç
        val viewUri: Uri? = intent?.data
        val viewType: String? = intent?.type

        setContent {
            EpubPdfReaderTheme {
                var screen by remember {
                    mutableStateOf<Screen>(
                        when {
                            viewUri == null -> Screen.Home
                            viewType == "application/epub+zip" -> Screen.Epub(viewUri)
                            viewType == "image/tiff" || viewType == "image/tif" -> Screen.Tiff(viewUri)
                            else -> Screen.Pdf(viewUri)
                        }
                    )
                }

                Surface(modifier = Modifier.fillMaxSize()) {
                    when (val current = screen) {
                        is Screen.Home -> HomeScreen(
                            onPdfPicked = { uri -> screen = Screen.Pdf(uri) },
                            onEpubPicked = { uri -> screen = Screen.Epub(uri) },
                            onTiffPicked = { uri -> screen = Screen.Tiff(uri) }
                        )
                        is Screen.Pdf -> PdfViewerScreen(
                            uri = current.uri,
                            onBack = { screen = Screen.Home }
                        )
                        is Screen.Epub -> EpubViewerScreen(
                            uri = current.uri,
                            onBack = { screen = Screen.Home }
                        )
                        is Screen.Tiff -> TiffViewerScreen(
                            uri = current.uri,
                            onBack = { screen = Screen.Home }
                        )
                    }
                }
            }
        }
    }
}
