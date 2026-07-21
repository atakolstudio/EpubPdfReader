package com.dgs.readerapp.tiff

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.dgs.readerapp.R
import com.dgs.readerapp.data.BookType
import com.dgs.readerapp.data.LibraryStore
import com.dgs.readerapp.data.ReadingProgressStore
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import org.beyka.tiffbitmapfactory.TiffBitmapFactory
import java.io.File

private const val MIN_ZOOM = 0.5f
private const val MAX_ZOOM = 3f
private const val ZOOM_STEP = 0.25f

/**
 * TIFF görüntüleyici. TIFF, Android'in kendi BitmapFactory'si tarafından
 * desteklenmediği için native libtiff tabanlı Android-TiffBitmapFactory
 * kütüphanesi kullanılır. Çok sayfalı (multi-directory) TIFF dosyalarında
 * her sayfa ayrı bir "sayfa" olarak listelenir; PDF görüntüleyiciyle aynı
 * yakınlaştırma ve "kaldığı yerden devam" mantığı uygulanır.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TiffViewerScreen(uri: Uri, onBack: () -> Unit) {
    val context = LocalContext.current
    var pages by remember { mutableStateOf<List<Bitmap>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf(false) }
    var zoom by remember { mutableFloatStateOf(1f) }
    val progressStore = remember { ReadingProgressStore(context) }
    val libraryStore = remember { LibraryStore(context) }
    val uriKey = remember(uri) { uri.toString() }
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = remember(uriKey) { progressStore.getTiffPageIndex(uriKey) }
    )

    LaunchedEffect(listState, uriKey) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .distinctUntilChanged()
            .collect { index ->
                progressStore.saveTiffPageIndex(uriKey, index)
                if (pages.isNotEmpty()) {
                    libraryStore.updateProgress(uriKey, index, pages.size)
                }
            }
    }

    LaunchedEffect(pages) {
        if (pages.isNotEmpty()) {
            libraryStore.updateProgress(uriKey, listState.firstVisibleItemIndex, pages.size)
        }
    }

    LaunchedEffect(uri) {
        var tempFile: File? = null
        try {
            tempFile = File(context.cacheDir, "tmp_tiff_${uri.hashCode()}.tiff")
            context.contentResolver.openInputStream(uri)?.use { input ->
                tempFile.outputStream().use { output -> input.copyTo(output) }
            }

            val boundsOptions = TiffBitmapFactory.Options().apply { inJustDecodeBounds = true }
            TiffBitmapFactory.decodeFile(tempFile, boundsOptions)
            val directoryCount = boundsOptions.outDirectoryCount.coerceAtLeast(1)

            val bitmaps = mutableListOf<Bitmap>()
            for (i in 0 until directoryCount) {
                val options = TiffBitmapFactory.Options().apply { inDirectoryNumber = i }
                val bmp = TiffBitmapFactory.decodeFile(tempFile, options)
                if (bmp != null) bitmaps.add(bmp)
            }

            if (bitmaps.isEmpty()) {
                error = true
            } else {
                pages = bitmaps
            }
        } catch (e: Exception) {
            error = true
        } finally {
            tempFile?.delete()
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
                    IconButton(
                        onClick = { zoom = (zoom - ZOOM_STEP).coerceAtLeast(MIN_ZOOM) }
                    ) {
                        Icon(Icons.Filled.ZoomOut, contentDescription = "Uzaklaştır")
                    }
                    IconButton(
                        onClick = { zoom = (zoom + ZOOM_STEP).coerceAtMost(MAX_ZOOM) }
                    ) {
                        Icon(Icons.Filled.ZoomIn, contentDescription = "Yakınlaştır")
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
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
    }
}
