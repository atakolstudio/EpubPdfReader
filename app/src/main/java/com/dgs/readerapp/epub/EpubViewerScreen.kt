package com.dgs.readerapp.epub

import android.annotation.SuppressLint
import android.net.Uri
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.matchParentSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.TextDecrease
import androidx.compose.material.icons.filled.TextIncrease
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewFeature
import com.dgs.readerapp.R
import kotlinx.coroutines.launch
import java.io.File

private const val MIN_TEXT_ZOOM = 70
private const val MAX_TEXT_ZOOM = 220
private const val TEXT_ZOOM_STEP = 15

enum class ReadingMode(val label: String) {
    DAY("Gündüz"),
    NIGHT("Gece"),
    SEPIA("Sepya")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EpubViewerScreen(uri: Uri, onBack: () -> Unit) {
    val context = LocalContext.current
    var book by remember { mutableStateOf<EpubBook?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf(false) }
    var textZoom by remember { mutableIntStateOf(100) }
    var readingMode by remember { mutableStateOf(ReadingMode.DAY) }
    var themeMenuExpanded by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(uri) {
        try {
            book = EpubParser(context).parse(uri)
        } catch (e: Exception) {
            error = true
        } finally {
            isLoading = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(book?.title ?: context.getString(R.string.app_name)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    IconButton(
                        onClick = { textZoom = (textZoom - TEXT_ZOOM_STEP).coerceAtLeast(MIN_TEXT_ZOOM) }
                    ) {
                        Icon(Icons.Filled.TextDecrease, contentDescription = "Yazıyı küçült")
                    }
                    IconButton(
                        onClick = { textZoom = (textZoom + TEXT_ZOOM_STEP).coerceAtMost(MAX_TEXT_ZOOM) }
                    ) {
                        Icon(Icons.Filled.TextIncrease, contentDescription = "Yazıyı büyüt")
                    }
                    Box {
                        IconButton(onClick = { themeMenuExpanded = true }) {
                            Icon(Icons.Filled.Palette, contentDescription = "Okuma teması")
                        }
                        DropdownMenu(
                            expanded = themeMenuExpanded,
                            onDismissRequest = { themeMenuExpanded = false }
                        ) {
                            ReadingMode.entries.forEach { mode ->
                                DropdownMenuItem(
                                    text = { Text(mode.label) },
                                    onClick = {
                                        readingMode = mode
                                        themeMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            when {
                isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                error || book == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(context.getString(R.string.error_loading))
                }
                else -> {
                    val b = book!!
                    val pagerState = rememberPagerState(pageCount = { b.chapters.size })

                    // Sağ/sol kaydırma (swipe) ile bölümler arası geçiş.
                    // Dikey kaydırma WebView içinde normal şekilde çalışmaya devam eder,
                    // çünkü HorizontalPager sadece yatay hareketleri kendisi yönetir.
                    Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                        HorizontalPager(
                            state = pagerState,
                            modifier = Modifier.fillMaxSize()
                        ) { page ->
                            EpubWebView(
                                chapterFile = b.chapters[page],
                                extractDir = b.extractDir,
                                textZoom = textZoom,
                                readingMode = readingMode
                            )
                        }

                        // Sepya modunda WebView üzerine dokunuşları engellemeyen,
                        // sadece görsel olarak ısıtan yarı saydam bir katman.
                        if (readingMode == ReadingMode.SEPIA) {
                            Box(
                                modifier = matchParentSize()
                                    .background(Color(0xFFF4E9D0).copy(alpha = 0.28f))
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween
                    ) {
                        IconButton(
                            onClick = {
                                scope.launch {
                                    pagerState.animateScrollToPage((pagerState.currentPage - 1).coerceAtLeast(0))
                                }
                            },
                            enabled = pagerState.currentPage > 0
                        ) { Text("<-") }
                        Text(
                            text = "${pagerState.currentPage + 1} / ${b.chapters.size}",
                            modifier = Modifier.align(Alignment.CenterVertically)
                        )
                        IconButton(
                            onClick = {
                                scope.launch {
                                    pagerState.animateScrollToPage(
                                        (pagerState.currentPage + 1).coerceAtMost(b.chapters.size - 1)
                                    )
                                }
                            },
                            enabled = pagerState.currentPage < b.chapters.size - 1
                        ) { Text("->") }
                    }
                }
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun EpubWebView(
    chapterFile: File,
    extractDir: File,
    textZoom: Int,
    readingMode: ReadingMode
) {
    AndroidView(factory = { ctx ->
        WebView(ctx).apply {
            settings.javaScriptEnabled = false
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.textZoom = textZoom

            val assetLoader = WebViewAssetLoader.Builder()
                .setDomain("appassets.androidplatform.net")
                .addPathHandler(
                    "/epub/",
                    WebViewAssetLoader.InternalStoragePathHandler(ctx, extractDir)
                )
                .build()

            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(
                    view: WebView,
                    request: android.webkit.WebResourceRequest
                ) = assetLoader.shouldInterceptRequest(request.url)
            }
        }
    }, update = { webView ->
        webView.settings.textZoom = textZoom

        // Gece modu: WebView'in resmi algoritmik karartma API'si (CSS/JS gerekmez).
        if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
            WebSettingsCompat.setAlgorithmicDarkeningAllowed(
                webView.settings,
                readingMode == ReadingMode.NIGHT
            )
        }

        val relativePath = chapterFile.relativeTo(extractDir).path.replace(File.separatorChar, '/')
        val url = "https://appassets.androidplatform.net/epub/$relativePath"
        // Sadece bölüm gerçekten değiştiyse yeniden yükle; aksi halde
        // her recomposition'da (ör. yazı boyutu/tema değişince) sayfa baştan
        // yüklenip kaydırma konumu kaybolmasın.
        if (webView.tag != url) {
            webView.tag = url
            webView.loadUrl(url)
        }
    })
}
