package com.dgs.readerapp.epub

import android.annotation.SuppressLint
import android.net.Uri
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebViewAssetLoader
import com.dgs.readerapp.R
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EpubViewerScreen(uri: Uri, onBack: () -> Unit) {
    val context = LocalContext.current
    var book by remember { mutableStateOf<EpubBook?>(null) }
    var currentChapter by remember { mutableIntStateOf(0) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf(false) }

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
                title = { Text(book?.title ?: stringResource_appName(context)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
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
                    Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                        EpubWebView(chapterFile = b.chapters[currentChapter], extractDir = b.extractDir)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween
                    ) {
                        IconButton(
                            onClick = { if (currentChapter > 0) currentChapter-- },
                            enabled = currentChapter > 0
                        ) { Text("<-") }
                        Text(
                            text = "${currentChapter + 1} / ${b.chapters.size}",
                            modifier = Modifier.align(Alignment.CenterVertically)
                        )
                        IconButton(
                            onClick = { if (currentChapter < b.chapters.size - 1) currentChapter++ },
                            enabled = currentChapter < b.chapters.size - 1
                        ) { Text("->") }
                    }
                }
            }
        }
    }
}

private fun stringResource_appName(context: android.content.Context) =
    context.getString(R.string.app_name)

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun EpubWebView(chapterFile: File, extractDir: File) {
    AndroidView(factory = { ctx ->
        WebView(ctx).apply {
            settings.javaScriptEnabled = false
            settings.allowFileAccess = false
            settings.allowContentAccess = false

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
        val relativePath = chapterFile.relativeTo(extractDir).path.replace(File.separatorChar, '/')
        webView.loadUrl("https://appassets.androidplatform.net/epub/$relativePath")
    })
}
