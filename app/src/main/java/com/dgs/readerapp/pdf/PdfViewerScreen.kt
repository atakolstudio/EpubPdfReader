package com.dgs.readerapp.pdf

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
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
import androidx.compose.runtime.mutableFloatStateOf
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
import com.dgs.readerapp.R
import com.dgs.readerapp.data.ReadingProgressStore
import com.dgs.readerapp.data.local.BookType
import com.dgs.readerapp.data.repository.LibraryRepository
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

private const val MIN_ZOOM = 0.5f
private const val MAX_ZOOM = 3f
private const val ZOOM_STEP = 0.25f
private const val MAX_READING_GAP_MS = 30 * 60 * 1000L

/** Android 15 (API 35) öncesinde PdfRenderer metin arama desteklemez (yalnızca raster render). */
val PDF_SEARCH_SUPPORTED = Build.VERSION.SDK_INT >= 35

private fun searchPdfPages(context: android.content.Context, uri: Uri, query: String): List<Int> {
    if (query.isBlank() || !PDF_SEARCH_SUPPORTED) return emptyList()
    val matches = mutableListOf<Int>()
    context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
        val renderer = PdfRenderer(pfd)
        for (i in 0 until renderer.pageCount) {
            renderer.openPage(i).use { page ->
                try {
                    val results = page.searchText(query)
                    if (results.isNotEmpty()) matches.add(i)
                } catch (e: Exception) {
                    // bu cihaz/sürücü aramayı desteklemiyor olabilir; sayfa atlanır
                }
            }
        }
        renderer.close()
    }
    return matches
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfViewerScreen(uri: Uri, onBack: () -> Unit) {
    val context = LocalContext.current
    var pages by remember { mutableStateOf<List<Bitmap>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf(false) }
    var pfd by remember { mutableStateOf<ParcelFileDescriptor?>(null) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var showSearch by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<Int>>(emptyList()) }
    var currentResultIndex by remember { mutableIntStateOf(0) }
    var showBookmarks by remember { mutableStateOf(false) }
    var isCurrentBookmarked by remember { mutableStateOf(false) }
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
                if (pages.isNotEmpty()) {
                    libraryRepository.updateProgress(uriKey, index, pages.size)
                }
                isCurrentBookmarked = libraryRepository.isBookmarked(uriKey, index)
            }
    }

    DisposableEffect(uri) {
        onDispose {
            pfd?.close()
            pages.forEach { it.recycle() }
        }
    }

    LaunchedEffect(pages) {
        if (pages.isNotEmpty()) {
            libraryRepository.updateProgress(uriKey, listState.firstVisibleItemIndex, pages.size)
        }
    }

    LaunchedEffect(uri) {
        try {
            val descriptor = context.contentResolver.openFileDescriptor(uri, "r")
            pfd = descriptor
            if (descriptor != null) {
                val renderer = PdfRenderer(descriptor)
                val bitmaps = mutableListOf<Bitmap>()
                for (i in 0 until renderer.pageCount) {
                    renderer.openPage(i).use { page ->
                        val scale = 2 // ekran netliği için 2x render
                        val bitmap = Bitmap.createBitmap(
                            page.width * scale,
                            page.height * scale,
                            Bitmap.Config.ARGB_8888
                        )
                        bitmap.eraseColor(android.graphics.Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        bitmaps.add(bitmap)
                    }
                }
                renderer.close()
                pages = bitmaps
                libraryRepository.recordOpened(context, uri, BookType.PDF)
            } else {
                error = true
            }
        } catch (e: Exception) {
            error = true
        } finally {
            isLoading = false
        }
    }

    Scaffold(
        topBar = {
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
                    IconButton(onClick = { zoom = (zoom - ZOOM_STEP).coerceAtLeast(MIN_ZOOM) }) {
                        Icon(Icons.Filled.ZoomOut, contentDescription = "Uzaklaştır")
                    }
                    IconButton(onClick = { zoom = (zoom + ZOOM_STEP).coerceAtMost(MAX_ZOOM) }) {
                        Icon(Icons.Filled.ZoomIn, contentDescription = "Yakınlaştır")
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                when {
                    isLoading -> CircularProgressIndicator()
                    error -> Text(context.getString(R.string.error_loading))
                    else -> {
                        val screenWidthDp = LocalConfiguration.current.screenWidthDp.dp
                        LazyColumn(modifier = Modifier.fillMaxSize(), state = listState) {
                            items(pages) { bitmap ->
                                val targetWidth = screenWidthDp * zoom
                                val aspect = bitmap.height.toFloat() / bitmap.width.toFloat()
                                val targetHeight = targetWidth * aspect

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState())
                                        .background(MaterialTheme.colorScheme.surface)
                                ) {
                                    Image(
                                        bitmap = bitmap.asImageBitmap(),
                                        contentDescription = null,
                                        modifier = Modifier
                                            .width(targetWidth)
                                            .height(targetHeight)
                                            .padding(vertical = 4.dp)
                                    )
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
                    items(bookmarks, key = { it.id }) { bm ->
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
}
