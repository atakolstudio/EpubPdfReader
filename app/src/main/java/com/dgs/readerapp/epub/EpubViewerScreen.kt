package com.dgs.readerapp.epub

import android.annotation.SuppressLint
import android.net.Uri
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
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
import com.dgs.readerapp.data.local.BookType
import com.dgs.readerapp.data.repository.LibraryRepository
import com.dgs.readerapp.data.ReadingProgressStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private const val MIN_TEXT_ZOOM = 70
private const val MAX_TEXT_ZOOM = 220
private const val TEXT_ZOOM_STEP = 15
private const val MIN_LINE_HEIGHT = 1.2f
private const val MAX_LINE_HEIGHT = 2.4f
private const val LINE_HEIGHT_STEP = 0.2f
private const val MAX_READING_GAP_MS = 30 * 60 * 1000L // 30 dk üstü boşluklar (arka plana atma vb.) sayılmaz

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
    var fontFamily by remember { mutableStateOf(EpubFontFamily.DEFAULT) }
    var lineHeight by remember { mutableFloatStateOf(1.5f) }
    var styleVersion by remember { mutableIntStateOf(0) }
    var showToc by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showBookmarks by remember { mutableStateOf(false) }
    var showNotes by remember { mutableStateOf(false) }
    var noteDraft by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val progressStore = remember { ReadingProgressStore(context) }
    val libraryRepository = remember { LibraryRepository.create(context) }
    val uriKey = remember(uri) { uri.toString() }

    LaunchedEffect(uri) {
        try {
            val parsed = withContext(Dispatchers.IO) { EpubParser(context).parse(uri) }
            book = parsed
            libraryRepository.recordOpened(context, uri, BookType.EPUB, parsed.coverFile)
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
                    if (!book?.toc.isNullOrEmpty()) {
                        IconButton(onClick = { showToc = true }) {
                            Icon(Icons.AutoMirrored.Filled.List, contentDescription = "İçindekiler")
                        }
                    }
                    IconButton(onClick = { showBookmarks = true }) {
                        Icon(Icons.Filled.Bookmarks, contentDescription = "Yer imlerim")
                    }
                    IconButton(onClick = { showNotes = true }) {
                        Icon(Icons.Filled.EditNote, contentDescription = "Notlarım")
                    }
                    IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Filled.Settings, contentDescription = "Okuma ayarları")
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
                    val savedChapter = remember(b) {
                        progressStore.getEpubChapterIndex(uriKey).coerceIn(0, b.chapters.size - 1)
                    }
                    val pagerState = rememberPagerState(
                        initialPage = savedChapter,
                        pageCount = { b.chapters.size }
                    )
                    var isCurrentBookmarked by remember { mutableStateOf(false) }
                    var lastTimestamp by remember { mutableLongStateOf(System.currentTimeMillis()) }

                    // Her bölüm değişiminde: kaldığı yeri kaydet, okuma süresini biriktir,
                    // yer imi durumunu güncelle.
                    LaunchedEffect(pagerState.currentPage) {
                        val now = System.currentTimeMillis()
                        val delta = now - lastTimestamp
                        lastTimestamp = now
                        if (delta in 500..MAX_READING_GAP_MS) {
                            libraryRepository.addReadingTime(uriKey, delta)
                        }
                        progressStore.saveEpubChapterIndex(uriKey, pagerState.currentPage)
                        libraryRepository.updateProgress(uriKey, pagerState.currentPage, b.chapters.size)
                        isCurrentBookmarked = libraryRepository.isBookmarked(uriKey, pagerState.currentPage)
                    }

                    // Sağ/sol kaydırma (swipe) ile bölümler arası geçiş.
                    // Dikey kaydırma WebView içinde normal şekilde çalışmaya devam eder,
                    // çünkü HorizontalPager sadece yatay hareketleri kendisi yönetir.
                    // Tablet/katlanabilir gibi geniş ekranlarda okuma alanı ortalanır ve
                    // satırların aşırı uzayıp okunmasının zorlaşmaması için genişlik sınırlanır.
                    Box(
                        modifier = Modifier.weight(1f).fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(modifier = Modifier.widthIn(max = 700.dp).fillMaxSize()) {
                            HorizontalPager(
                                state = pagerState,
                                modifier = Modifier.fillMaxSize()
                            ) { page ->
                                EpubWebView(
                                    chapterFile = b.chapters[page],
                                    extractDir = b.extractDir,
                                    textZoom = textZoom,
                                    readingMode = readingMode,
                                    styleVersion = styleVersion
                                )
                            }

                            // Sepya modunda WebView üzerine dokunuşları engellemeyen,
                            // sadece görsel olarak ısıtan yarı saydam bir katman.
                            if (readingMode == ReadingMode.SEPIA) {
                                Box(
                                    modifier = Modifier
                                        .matchParentSize()
                                        .background(Color(0xFFF4E9D0).copy(alpha = 0.28f))
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                scope.launch {
                                    pagerState.animateScrollToPage((pagerState.currentPage - 1).coerceAtLeast(0))
                                }
                            },
                            enabled = pagerState.currentPage > 0
                        ) { Text("<-") }

                        IconButton(
                            onClick = {
                                scope.launch {
                                    val label = "Bölüm ${pagerState.currentPage + 1}"
                                    libraryRepository.toggleBookmark(uriKey, pagerState.currentPage, label)
                                    isCurrentBookmarked = !isCurrentBookmarked
                                }
                            }
                        ) {
                            Icon(
                                imageVector = if (isCurrentBookmarked) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                                contentDescription = "Yer imi ekle/kaldır",
                                tint = if (isCurrentBookmarked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Text("${pagerState.currentPage + 1} / ${b.chapters.size}")

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

                    if (showToc) {
                        ModalBottomSheet(onDismissRequest = { showToc = false }) {
                            LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 480.dp)) {
                                items(b.toc) { entry ->
                                    Text(
                                        text = entry.title,
                                        style = MaterialTheme.typography.bodyLarge,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                scope.launch { pagerState.animateScrollToPage(entry.chapterIndex) }
                                                showToc = false
                                            }
                                            .padding(horizontal = 20.dp, vertical = 14.dp)
                                    )
                                }
                            }
                        }
                    }

                    if (showBookmarks) {
                        val bookmarks by libraryRepository.observeBookmarks(uriKey).collectAsState(initial = emptyList())
                        ModalBottomSheet(onDismissRequest = { showBookmarks = false }) {
                            if (bookmarks.isEmpty()) {
                                Text(
                                    "Henüz yer imi yok. Alt çubuktaki yer imi ikonuna dokunarak ekleyebilirsin.",
                                    modifier = Modifier.padding(24.dp)
                                )
                            } else {
                                LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 480.dp)) {
                                    items(bookmarks, key = { it.id }) { bm ->
                                        Text(
                                            text = bm.label,
                                            style = MaterialTheme.typography.bodyLarge,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    scope.launch { pagerState.animateScrollToPage(bm.position) }
                                                    showBookmarks = false
                                                }
                                                .padding(horizontal = 20.dp, vertical = 14.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (showNotes) {
                        val notes by libraryRepository.observeNotes(uriKey).collectAsState(initial = emptyList())
                        ModalBottomSheet(onDismissRequest = { showNotes = false }) {
                            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                                Text("Notlarım", style = MaterialTheme.typography.titleLarge)
                                OutlinedTextField(
                                    value = noteDraft,
                                    onValueChange = { noteDraft = it },
                                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                                    placeholder = { Text("Bu bölümle ilgili bir not yaz…") },
                                    minLines = 2,
                                    maxLines = 4
                                )
                                androidx.compose.material3.Button(
                                    onClick = {
                                        val text = noteDraft
                                        scope.launch {
                                            libraryRepository.addNote(uriKey, pagerState.currentPage, text)
                                        }
                                        noteDraft = ""
                                    },
                                    enabled = noteDraft.isNotBlank(),
                                    modifier = Modifier.padding(top = 8.dp)
                                ) { Text("Ekle") }
                            }

                            if (notes.isEmpty()) {
                                Text(
                                    "Henüz not eklemedin.",
                                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)
                                )
                            } else {
                                LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 380.dp)) {
                                    items(notes, key = { it.id }) { note ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .clickable {
                                                        scope.launch { pagerState.animateScrollToPage(note.position) }
                                                        showNotes = false
                                                    }
                                            ) {
                                                Text(
                                                    text = "Bölüm ${note.position + 1}",
                                                    style = MaterialTheme.typography.labelLarge,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                                Text(text = note.content, style = MaterialTheme.typography.bodyMedium)
                                            }
                                            IconButton(onClick = {
                                                scope.launch { libraryRepository.deleteNote(note.id) }
                                            }) {
                                                Icon(Icons.Filled.Delete, contentDescription = "Notu sil")
                                            }
                                        }
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    }

                    if (showSettings) {
                        ModalBottomSheet(onDismissRequest = { showSettings = false }) {
                            Column(modifier = Modifier.padding(20.dp)) {
                                Text("Okuma Teması", style = MaterialTheme.typography.titleLarge)
                                Row(
                                    modifier = Modifier.padding(top = 8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    ReadingMode.entries.forEach { mode ->
                                        FilterChip(
                                            selected = readingMode == mode,
                                            onClick = { readingMode = mode },
                                            label = { Text(mode.label) }
                                        )
                                    }
                                }

                                Text("Yazı Boyutu", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 20.dp))
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                                    IconButton(onClick = { textZoom = (textZoom - TEXT_ZOOM_STEP).coerceAtLeast(MIN_TEXT_ZOOM) }) {
                                        Text("A-")
                                    }
                                    Text("%$textZoom", modifier = Modifier.padding(horizontal = 12.dp))
                                    IconButton(onClick = { textZoom = (textZoom + TEXT_ZOOM_STEP).coerceAtMost(MAX_TEXT_ZOOM) }) {
                                        Text("A+")
                                    }
                                }

                                Text("Yazı Tipi", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 20.dp))
                                Row(
                                    modifier = Modifier.padding(top = 8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    EpubFontFamily.entries.forEach { family ->
                                        FilterChip(
                                            selected = fontFamily == family,
                                            onClick = {
                                                fontFamily = family
                                                writeReaderOverridesCss(b.extractDir, fontFamily, lineHeight)
                                                styleVersion++
                                            },
                                            label = { Text(family.label) }
                                        )
                                    }
                                }

                                Text("Satır Aralığı", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 20.dp))
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                                    IconButton(onClick = {
                                        lineHeight = (lineHeight - LINE_HEIGHT_STEP).coerceAtLeast(MIN_LINE_HEIGHT)
                                        writeReaderOverridesCss(b.extractDir, fontFamily, lineHeight)
                                        styleVersion++
                                    }) { Text("-") }
                                    Text("${"%.1f".format(lineHeight)}x", modifier = Modifier.padding(horizontal = 12.dp))
                                    IconButton(onClick = {
                                        lineHeight = (lineHeight + LINE_HEIGHT_STEP).coerceAtMost(MAX_LINE_HEIGHT)
                                        writeReaderOverridesCss(b.extractDir, fontFamily, lineHeight)
                                        styleVersion++
                                    }) { Text("+") }
                                }
                            }
                        }
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
    readingMode: ReadingMode,
    styleVersion: Int
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
        // Tag hem URL'yi hem de stil sürümünü içerir: bölüm değiştiğinde YA DA
        // yazı tipi/satır aralığı ayarı değiştiğinde (styleVersion artınca) yeniden
        // yüklenir; sadece yazı boyutu/tema değişince gereksiz yeniden yükleme olmaz.
        val tag = "$url|$styleVersion"
        if (webView.tag != tag) {
            webView.tag = tag
            webView.loadUrl(url)
        }
    })
}
