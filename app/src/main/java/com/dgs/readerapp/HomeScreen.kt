package com.dgs.readerapp

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dgs.readerapp.data.BookType
import com.dgs.readerapp.data.LibraryStore
import com.dgs.readerapp.data.RecentBook
import com.dgs.readerapp.data.queryDisplayName

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onPdfPicked: (Uri) -> Unit,
    onEpubPicked: (Uri) -> Unit,
    onTiffPicked: (Uri) -> Unit
) {
    val context = LocalContext.current
    val libraryStore = remember { LibraryStore(context) }
    var recents by remember { mutableStateOf(libraryStore.getRecents()) }

    fun openAndRecord(uri: Uri, type: String, onPicked: (Uri) -> Unit) {
        try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (e: SecurityException) {
            // Bazı sağlayıcılar kalıcı izni desteklemez; dosya bu oturumda yine de açılabilir.
        }
        val name = queryDisplayName(context, uri)
        libraryStore.recordOpened(uri.toString(), name, type)
        recents = libraryStore.getRecents()
        onPicked(uri)
    }

    val pdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { openAndRecord(it, BookType.PDF, onPdfPicked) } }

    val epubLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { openAndRecord(it, BookType.EPUB, onEpubPicked) } }

    val tiffLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { openAndRecord(it, BookType.TIFF, onTiffPicked) } }

    fun reopen(book: RecentBook) {
        val uri = Uri.parse(book.uriKey)
        libraryStore.recordOpened(book.uriKey, book.displayName, book.type)
        recents = libraryStore.getRecents()
        when (book.type) {
            BookType.PDF -> onPdfPicked(uri)
            BookType.EPUB -> onEpubPicked(uri)
            BookType.TIFF -> onTiffPicked(uri)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.home_title)) })
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Button(
                onClick = { pdfLauncher.launch(arrayOf("application/pdf")) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.PictureAsPdf, contentDescription = null)
                Text(text = "  " + stringResource(R.string.open_pdf), modifier = Modifier.padding(start = 4.dp))
            }

            androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(6.dp))

            Button(
                onClick = { epubLauncher.launch(arrayOf("application/epub+zip")) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.MenuBook, contentDescription = null)
                Text(text = "  " + stringResource(R.string.open_epub), modifier = Modifier.padding(start = 4.dp))
            }

            androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(6.dp))

            Button(
                onClick = { tiffLauncher.launch(arrayOf("image/tiff")) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.Image, contentDescription = null)
                Text(text = "  " + stringResource(R.string.open_tiff), modifier = Modifier.padding(start = 4.dp))
            }

            androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(12.dp))
            HorizontalDivider()
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(8.dp))

            Text(
                text = stringResource(R.string.library_title),
                style = MaterialTheme.typography.titleLarge
            )

            androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(6.dp))

            if (recents.isEmpty()) {
                Text(
                    text = stringResource(R.string.library_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(recents, key = { it.uriKey }) { book ->
                        RecentBookRow(
                            book = book,
                            onClick = { reopen(book) },
                            onDelete = {
                                libraryStore.remove(book.uriKey)
                                recents = libraryStore.getRecents()
                            }
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun RecentBookRow(book: RecentBook, onClick: () -> Unit, onDelete: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = when (book.type) {
                    BookType.PDF -> Icons.Filled.PictureAsPdf
                    BookType.TIFF -> Icons.Filled.Image
                    else -> Icons.Filled.MenuBook
                },
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Column(modifier = Modifier.padding(start = 12.dp)) {
                Text(
                    text = book.displayName,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (book.total > 0) {
                    val unit = if (book.type == BookType.EPUB) "Bölüm" else "Sayfa"
                    Text(
                        text = "$unit ${book.position + 1} / ${book.total}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Filled.Close, contentDescription = "Kaldır")
        }
    }
}
