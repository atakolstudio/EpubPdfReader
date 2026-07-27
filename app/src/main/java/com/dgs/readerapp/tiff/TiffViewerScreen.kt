package com.dgs.readerapp.tiff

import android.app.Activity
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.RotateLeft
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
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
import kotlinx.coroutines.withContext
import net.engawapg.lib.zoomable.ExperimentalZoomableApi
import net.engawapg.lib.zoomable.rememberZoomState
import net.engawapg.lib.zoomable.zoomableWithScroll
import org.beyka.tiffbitmapfactory.TiffBitmapFactory
import java.io.File

/**
 * TIFF görüntüleyici. TIFF, Android'in kendi BitmapFactory'si tarafından
 * desteklenmediği için native libtiff tabanlı Android-TiffBitmapFactory
 * kütüphanesi kullanılır. Çok sayfalı (multi-directory) TIFF dosyalarında
 * her sayfa ayrı bir "sayfa" olarak listelenir. Pinch/çift dokunuşla
 * yakınlaştırma, PDF görüntüleyiciyle aynı `zoomableWithScroll` deseniyle
 * (dikey kaydırmayı bozmadan) sağlanır.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalZoomableApi::class)
@Composable
fun TiffViewerScreen(uri: Uri, onBack: () -> Unit) {
    val context = LocalContext.current
    var pages by remember { mutableStateOf<List<Bitmap>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf(false) }
    var lastTimestamp by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var isFullScreen by remember { mutableStateOf(false) }
    var rotationDegrees by remember { mutableIntStateOf(0) }
    val activity = context as? Activity

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
    val progressStore = remember { ReadingProgressStore(context) }
    val libraryRepository = remember { LibraryRepository.create(context) }
    val uriKey = remember(uri) { uri.toString() }
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = remember(uriKey) { progressStore.getTiffPageIndex(uriKey) }
    )

    LaunchedEffect(listState, uriKey) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .distinctUntilChanged()
            .collect { index ->
                val now = System.currentTimeMillis()
                val delta = now - lastTimestamp
                lastTimestamp = now
                if (delta in 500..(30 * 60 * 1000L)) {
                    libraryRepository.addReadingTime(uriKey, delta)
                }
                progressStore.saveTiffPageIndex(uriKey, index)
                if (pages.isNotEmpty()) {
                    libraryRepository.updateProgress(uriKey, index, pages.size)
                }
            }
    }

    LaunchedEffect(pages) {
        if (pages.isNotEmpty()) {
            libraryRepository.updateProgress(uriKey, listState.firstVisibleItemIndex, pages.size)
        }
    }

    LaunchedEffect(uri) {
        var tempFile: File? = null
        try {
            withContext(Dispatchers.IO) {
                tempFile = File(context.cacheDir, "tmp_tiff_${uri.hashCode()}.tiff")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    tempFile!!.outputStream().use { output -> input.copyTo(output) }
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
                    libraryRepository.recordOpened(context, uri, BookType.TIFF)
                }
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
            if (!isFullScreen) {
                TopAppBar(
                    title = { Text(context.getString(R.string.app_name)) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                        }
                    },
                    actions = {
                        IconButton(onClick = { rotationDegrees = (rotationDegrees - 90 + 360) % 360 }) {
                            Icon(Icons.Filled.RotateLeft, contentDescription = "Sola döndür")
                        }
                        IconButton(onClick = { rotationDegrees = (rotationDegrees + 90) % 360 }) {
                            Icon(Icons.Filled.RotateRight, contentDescription = "Sağa döndür")
                        }
                        IconButton(onClick = { isFullScreen = true }) {
                            Icon(Icons.Filled.Fullscreen, contentDescription = "Tam ekran")
                        }
                    }
                )
            }
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
                    val zoomState = rememberZoomState()
                    // Pinch ile yakınlaştır/uzaklaştır, çift dokunuşla hızlı yakınlaştır,
                    // yakınlaşıldığında tek parmakla gezinme — dikey liste kaydırmasını
                    // bozmadan (net.engawapg.lib:zoomable, tam bu senaryo için).
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().zoomableWithScroll(zoomState),
                        state = listState
                    ) {
                        items(pages) { bitmap ->
                            val rotatedBitmap = remember(bitmap, rotationDegrees) {
                                if (rotationDegrees == 0) {
                                    bitmap
                                } else {
                                    val matrix = android.graphics.Matrix().apply {
                                        postRotate(rotationDegrees.toFloat())
                                    }
                                    Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                                }
                            }
                            val aspect = rotatedBitmap.height.toFloat() / rotatedBitmap.width.toFloat()
                            val targetWidth = screenWidthDp
                            val targetHeight = targetWidth * aspect

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surface),
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    bitmap = rotatedBitmap.asImageBitmap(),
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
}
