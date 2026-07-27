package com.dgs.readerapp.pdf

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.dgs.readerapp.R
import com.dgs.readerapp.data.ReadingProgressStore
import com.dgs.readerapp.data.local.BookType
import com.dgs.readerapp.data.repository.LibraryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.engawapg.lib.zoomable.ExperimentalZoomableApi
import net.engawapg.lib.zoomable.rememberZoomState
import net.engawapg.lib.zoomable.zoomableWithScroll

private const val MAX_READING_GAP_MS = 30 * 60 * 1000L

/** Android 15 (API 35) öncesinde PdfRenderer metin arama desteklemez (yalnızca raster render). */
val PDF_SEARCH_SUPPORTED = Build.VERSION.SDK_INT >= 35

private data class PdfPageInfo(val width: Int, val height: Int)

private suspend fun searchPdfPages(context: android.content.Context, uri: Uri, query: String): List<Int> =
    withContext(Dispatchers.IO) {
        if (query.isBlank() || !PDF_SEARCH_SUPPORTED) return@withContext emptyList()
        val matches = mutableListOf<Int>()
        context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
            val renderer = PdfRenderer(pfd)
            for (i in 0 until renderer.pageCount) {
                renderer.openPage(i).use { page ->
                    try {
                        if (page.searchText(query).isNotEmpty()) matches.add(i)
                    } catch (e: Exception) {
                        // bu cihaz/sürücü aramayı desteklemiyor olabilir; sayfa atlanır
                    }
                }
            }
            renderer.close()
        }
        matches
    }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalZoomableApi::class)
@Composable
fun PdfViewerScreen(uri: Uri, onBack: () -> Unit) {
    val context = LocalContext.current
    var pageInfos by remember { mutableStateOf<List<PdfPageInfo>>(emptyList()) }
    val pageBitmaps = remember { androidx.compose.runtime.mutableStateListOf<Bitmap?>() }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf(false) }
    var pfd by remember { mutableStateOf<ParcelFileDescriptor?>(null) }
    var showSearch by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<Int>>(emptyList()) }
    var currentResultIndex by remember { mutableIntStateOf(0) }
    var showBookmarks by remember { mutableStateOf(false) }
    var isCurrentBookmarked by remember { mutableStateOf(false) }
    var isFullScreen by remember { mutableStateOf(false) }
    var showGoToPage by remember { mutableStateOf(false) }
    var goToPageText by remember { mutableStateOf("") }
    val activity = context as? Activity

    // Tam ekran: sistem çubuklarını (durum/gezinme) ve uygulama başlık çubuğunu
    // gizleyerek okuma alanını maksimize eder. Kenardan içe kaydırınca çubuklar
    // geçici olarak tekrar görünür (BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE).
    LaunchedEffect(isFullScreen) {
        val window = activity?.window ?: return@LaunchedEffect
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        if (isFullScreen) {
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            val window = activity?.window
            if (window != null) {
                WindowCompat.getInsetsController(window, window.decorView)
                    .show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }
    val scope = rememberCoroutineScope()
    val progressStore = remember { ReadingProgressStore(context) }
    val libraryRepository = remember { LibraryRepository.create(context) }
    val uriKey = remember(uri) { uri.toString() }
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = remember(uriKey) { progressStore.getPdfPageIndex(uriKey) }
    )
    var lastTimestamp by remember { mutableLongStateOf(System.currentTimeMillis()) }

    // Kaydırma pozisyonu her değiştiğinde kaldığı sayfayı ve okuma süresini kaydet.
    LaunchedEffect(listState, uriKey) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .distinctUntilChanged()
            .collect { index ->
                val now = System.currentTimeMillis()
                val delta = now - lastTimestamp
                lastTimestamp = now
                if (delta in 500..MAX_READING_GAP_MS) {
                    libraryRepository.addReadingTime(uriKey, delta)
                }
                progressStore.savePdfPageIndex(uriKey, index)
                if (pageInfos.isNotEmpty()) {
                    libraryRepository.updateProgress(uriKey, index, pageInfos.size)
                }
                isCurrentBookmarked = libraryRepository.isBookmarked(uriKey, index)
            }
    }

    DisposableEffect(uri) {
        onDispose {
            pageBitmaps.forEach { it?.recycle() }
            pageBitmaps.clear()
            pfd?.close()
        }
    }

    // Önce sayfa boyutları (hızlı, sadece metadata) okunur ve liste hemen gösterilir;
    // ardından sayfalar arka planda SIRAYLA render edilir ve her biri bitince ekranda
    // anında belirir. Böylece kullanıcı saniyeler içinde içerik görmeye başlar, tüm
    // PDF'in önceden render olmasını beklemez. Basit ve öngörülebilir olduğu için bu
    // yaklaşım, kaydırmaya bağlı "sadece görüneni render et" mantığından daha güvenilirdir.
    LaunchedEffect(uri) {
        try {
            withContext(Dispatchers.IO) {
                val descriptor = context.contentResolver.openFileDescriptor(uri, "r")
                pfd = descriptor
                if (descriptor != null) {
                    val renderer = PdfRenderer(descriptor)
                    val count = renderer.pageCount
                    val infos = ArrayList<PdfPageInfo>(count)
                    for (i in 0 until count) {
                        renderer.openPage(i).use { page -> infos.add(PdfPageInfo(page.width, page.height)) }
                    }
                    pageInfos = infos
                    pageBitmaps.clear()
                    repeat(count) { pageBitmaps.add(null) }
                    isLoading = false
                    libraryRepository.recordOpened(context, uri, BookType.PDF)

                    for (i in 0 until count) {
                        try {
                            renderer.openPage(i).use { page ->
                                val scale = 2
                                val bmp = Bitmap.createBitmap(
                                    page.width * scale,
                                    page.height * scale,
                                    Bitmap.Config.ARGB_8888
                                )
                                bmp.eraseColor(android.graphics.Color.WHITE)
                                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                pageBitmaps[i] = bmp
                            }
                        } catch (e: Exception) {
                            // bu sayfa render edilemedi; yer tutucu görünmeye devam eder, diğer sayfalar etkilenmez
                        }
                    }
                    renderer.close()
                } else {
                    error = true
                    isLoading = false
                }
            }
        } catch (e: Exception) {
            error = true
            isLoading = false
        }
    }

    Scaffold(
        topBar = {
            if (!isFullScreen) {
                TopAppBar(
                title = { Text(context.getString(R.string.app_name)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    if (PDF_SEARCH_SUPPORTED) {
                        IconButton(onClick = { showSearch = !showSearch }) {
                            Icon(Icons.Filled.Search, contentDescription = "Ara")
                        }
                    }
                    IconButton(onClick = { showBookmarks = true }) {
                        Icon(Icons.Filled.Bookmarks, contentDescription = "Yer imlerim")
                    }
                    IconButton(
                        onClick = {
                            scope.launch {
                                val page = listState.firstVisibleItemIndex
                                libraryRepository.toggleBookmark(uriKey, page, "Sayfa ${page + 1}")
                                isCurrentBookmarked = !isCurrentBookmarked
                            }
                        }
                    ) {
                        Icon(
                            imageVector = if (isCurrentBookmarked) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                            contentDescription = "Yer imi ekle/kaldır"
                        )
                    }
                    IconButton(onClick = { showGoToPage = true }) {
                        Icon(Icons.Filled.MenuBook, contentDescription = "Sayfaya git")
                    }
                    IconButton(onClick = { isFullScreen = true }) {
                        Icon(Icons.Filled.Fullscreen, contentDescription = "Tam ekran")
                    }
                }
                )
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                when {
                    isLoading -> CircularProgressIndicator()
                    error -> Text(context.getString(R.string.error_loading))
                    else -> {
                        val screenWidthDp = LocalConfiguration.current.screenWidthDp.dp
                        val zoomState = rememberZoomState()
                        // Pinch ile yakınlaştır/uzaklaştır, çift dokunuşla hızlı yakınlaştır,
                        // yakınlaşıldığında tek parmakla gezinme — kaydırmayı (dikey liste
                        // kaydırma) bozmadan. net.engawapg.lib:zoomable kütüphanesi bunu
                        // tam olarak bu senaryo için sağlıyor.
                        LazyColumn(
                            modifier = Modifier.fillMaxSize().zoomableWithScroll(zoomState),
                            state = listState
                        ) {
                            itemsIndexed(pageInfos) { index, info ->
                                val targetWidth = screenWidthDp
                                val aspect = info.height.toFloat() / info.width.toFloat()
                                val targetHeight = targetWidth * aspect
                                val bitmap = pageBitmaps.getOrNull(index)

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(MaterialTheme.colorScheme.surface)
                                ) {
                                    if (bitmap != null) {
                                        Image(
                                            bitmap = bitmap.asImageBitmap(),
                                            contentDescription = null,
                                            modifier = Modifier
                                                .width(targetWidth)
                                                .height(targetHeight)
                                                .padding(vertical = 4.dp)
                                        )
                                    } else {
                                        // Sayfa henüz render edilmedi: doğru boyutta bir yer
                                        // tutucu gösterilir, kaydırma davranışı bozulmaz.
                                        Box(
                                            modifier = Modifier
                                                .width(targetWidth)
                                                .height(targetHeight)
                                                .padding(vertical = 4.dp)
                                                .background(MaterialTheme.colorScheme.surfaceVariant),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (showSearch) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            placeholder = { Text("PDF içinde ara…") },
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                keyboardType = KeyboardType.Text,
                                imeAction = ImeAction.Search
                            ),
                            keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                                onSearch = {
                                    scope.launch {
                                        searchResults = searchPdfPages(context, uri, searchQuery)
                                        currentResultIndex = 0
                                        searchResults.getOrNull(0)?.let { page ->
                                            listState.animateScrollToItem(page)
                                        }
                                    }
                                }
                            )
                        )
                        if (searchResults.isNotEmpty()) {
                            Text(
                                text = "${currentResultIndex + 1}/${searchResults.size}",
                                modifier = Modifier.padding(horizontal = 8.dp)
                            )
                            IconButton(onClick = {
                                if (searchResults.isNotEmpty()) {
                                    currentResultIndex = (currentResultIndex - 1 + searchResults.size) % searchResults.size
                                    scope.launch { listState.animateScrollToItem(searchResults[currentResultIndex]) }
                                }
                            }) { Text("<") }
                            IconButton(onClick = {
                                if (searchResults.isNotEmpty()) {
                                    currentResultIndex = (currentResultIndex + 1) % searchResults.size
                                    scope.launch { listState.animateScrollToItem(searchResults[currentResultIndex]) }
                                }
                            }) { Text(">") }
                        }
                    }
                }
            }

            if (isFullScreen) {
                IconButton(
                    onClick = { isFullScreen = false },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp)
                        .background(
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                            shape = MaterialTheme.shapes.large
                        )
                ) {
                    Icon(Icons.Filled.FullscreenExit, contentDescription = "Tam ekrandan çık")
                }
            }
        }
    }

    if (showBookmarks) {
        val bookmarks by libraryRepository.observeBookmarks(uriKey).collectAsState(initial = emptyList())
        ModalBottomSheet(onDismissRequest = { showBookmarks = false }) {
            if (bookmarks.isEmpty()) {
                Text(
                    "Henüz yer imi yok. Üst çubuktaki yer imi ikonuna dokunarak ekleyebilirsin.",
                    modifier = Modifier.padding(24.dp)
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 480.dp)) {
                    itemsIndexed(bookmarks) { _, bm ->
                        Text(
                            text = bm.label,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    scope.launch { listState.animateScrollToItem(bm.position) }
                                    showBookmarks = false
                                }
                                .padding(horizontal = 20.dp, vertical = 14.dp)
                        )
                    }
                }
            }
        }
    }

    if (showGoToPage) {
        AlertDialog(
            onDismissRequest = { showGoToPage = false },
            title = { Text("Sayfaya git") },
            text = {
                OutlinedTextField(
                    value = goToPageText,
                    onValueChange = { input -> goToPageText = input.filter { it.isDigit() } },
                    placeholder = { Text("Sayfa numarası (1-${pageInfos.size})") },
                    singleLine = true
                )
            },
            confirmButton = {
                androidx.compose.material3.Button(onClick = {
                    val target = goToPageText.toIntOrNull()
                    if (target != null && target in 1..pageInfos.size) {
                        scope.launch { listState.animateScrollToItem(target - 1) }
                    }
                    showGoToPage = false
                    goToPageText = ""
                }) { Text("Git") }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showGoToPage = false; goToPageText = "" }) {
                    Text("İptal")
                }
            }
        )
    }
}
