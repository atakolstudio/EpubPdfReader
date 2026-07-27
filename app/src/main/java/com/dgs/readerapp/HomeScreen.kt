package com.dgs.readerapp

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.SubcomposeAsyncImage
import com.dgs.readerapp.data.LibraryBackup
import com.dgs.readerapp.data.formatDate
import com.dgs.readerapp.data.formatFileSize
import com.dgs.readerapp.data.local.BookEntity
import com.dgs.readerapp.data.local.BookType
import com.dgs.readerapp.ui.components.CoverPlaceholder
import com.dgs.readerapp.ui.components.shimmerEffect
import com.dgs.readerapp.ui.library.LibraryViewModel
import com.dgs.readerapp.ui.library.SortOption
import com.dgs.readerapp.ui.library.TypeFilter
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onPdfPicked: (Uri) -> Unit,
    onEpubPicked: (Uri) -> Unit,
    onTiffPicked: (Uri) -> Unit,
    onOpenStats: () -> Unit
) {
    val context = LocalContext.current
    val viewModel: LibraryViewModel = viewModel(factory = LibraryViewModel.factory(context))
    val books by viewModel.books.collectAsState()
    val scope = rememberCoroutineScope()
    var menuExpanded by remember { mutableStateOf(false) }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    var query by remember { mutableStateOf("") }
    var sortOption by remember { mutableStateOf(SortOption.RECENT) }
    var typeFilter by remember { mutableStateOf(TypeFilter.ALL) }
    var infoDialogBook by remember { mutableStateOf<BookEntity?>(null) }

    fun grantPersistablePermission(uri: Uri) {
        try {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (e: SecurityException) {
            // Bazı sağlayıcılar kalıcı izni desteklemez; dosya bu oturumda yine de açılabilir.
        }
    }

    val pdfLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { grantPersistablePermission(it); onPdfPicked(it) }
    }
    val epubLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { grantPersistablePermission(it); onEpubPicked(it) }
    }
    val tiffLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { grantPersistablePermission(it); onTiffPicked(it) }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let { target ->
            scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    val json = LibraryBackup.toJson(books)
                    context.contentResolver.openOutputStream(target)?.use { out ->
                        out.write(json.toByteArray())
                    }
                } catch (e: Exception) {
                    // yedekleme başarısız olursa sessizce yok say
                }
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { source ->
            scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    val text = context.contentResolver.openInputStream(source)?.bufferedReader()?.readText()
                    if (text != null) {
                        viewModel.restoreBackup(LibraryBackup.fromJson(text))
                    }
                } catch (e: Exception) {
                    // içe aktarma başarısız olursa sessizce yok say
                }
            }
        }
    }

    fun reopen(book: BookEntity) {
        val uri = Uri.parse(book.id)
        when (book.type) {
            BookType.PDF -> onPdfPicked(uri)
            BookType.EPUB -> onEpubPicked(uri)
            BookType.TIFF -> onTiffPicked(uri)
        }
    }

    fun shareBook(book: BookEntity) {
        val mime = when (book.type) {
            BookType.PDF -> "application/pdf"
            BookType.EPUB -> "application/epub+zip"
            BookType.TIFF -> "image/tiff"
            else -> "*/*"
        }
        try {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = mime
                putExtra(Intent.EXTRA_STREAM, Uri.parse(book.id))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Paylaş"))
        } catch (e: Exception) {
            // paylaşım başarısız olursa sessizce yok say
        }
    }

    val filtered = books
        .filter { b -> query.isBlank() || b.name.contains(query, ignoreCase = true) }
        .filter { b ->
            when (typeFilter) {
                TypeFilter.ALL -> true
                TypeFilter.PDF -> b.type == BookType.PDF
                TypeFilter.EPUB -> b.type == BookType.EPUB
                TypeFilter.TIFF -> b.type == BookType.TIFF
                TypeFilter.FAVORITES -> b.favorite
            }
        }
        .let { list ->
            when (sortOption) {
                SortOption.RECENT -> list.sortedByDescending { it.lastOpened }
                SortOption.NAME -> list.sortedBy { it.name.lowercase() }
                SortOption.PROGRESS -> list.sortedByDescending { it.progress }
            }
        }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(R.string.library_title)) },
                scrollBehavior = scrollBehavior,
                actions = {
                    IconButton(onClick = onOpenStats) {
                        Icon(Icons.Filled.BarChart, contentDescription = "İstatistikler")
                    }
                    Box {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "Menü")
                        }
                        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                            DropdownMenuItem(
                                text = { Text("Yedekle (Dışa Aktar)") },
                                leadingIcon = { Icon(Icons.Filled.CloudUpload, contentDescription = null) },
                                onClick = {
                                    menuExpanded = false
                                    exportLauncher.launch("kutuphane_yedek.json")
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Geri Yükle (İçe Aktar)") },
                                leadingIcon = { Icon(Icons.Filled.CloudDownload, contentDescription = null) },
                                onClick = {
                                    menuExpanded = false
                                    importLauncher.launch(arrayOf("application/json"))
                                }
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = padding
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ActionTile(
                        label = stringResource(R.string.open_pdf),
                        icon = Icons.Filled.PictureAsPdf,
                        onClick = { pdfLauncher.launch(arrayOf("application/pdf")) },
                        modifier = Modifier.weight(1f)
                    )
                    ActionTile(
                        label = stringResource(R.string.open_epub),
                        icon = Icons.Filled.MenuBook,
                        onClick = { epubLauncher.launch(arrayOf("application/epub+zip")) },
                        modifier = Modifier.weight(1f)
                    )
                    ActionTile(
                        label = stringResource(R.string.open_tiff),
                        icon = Icons.Filled.Image,
                        onClick = { tiffLauncher.launch(arrayOf("image/tiff")) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
                    placeholder = { Text(stringResource(R.string.search_hint)) },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    singleLine = true,
                    shape = MaterialTheme.shapes.large
                )
            }

            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(TypeFilter.entries) { filter ->
                        FilterChip(
                            selected = typeFilter == filter,
                            onClick = { typeFilter = filter },
                            label = { Text(filter.label) }
                        )
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SortOption.entries.forEach { option ->
                        FilterChip(
                            selected = sortOption == option,
                            onClick = { sortOption = option },
                            label = { Text(option.label) }
                        )
                    }
                }
            }

            if (filtered.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 56.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = if (books.isEmpty()) Icons.Filled.MenuBook else Icons.Filled.Search,
                            contentDescription = null,
                            modifier = Modifier.size(56.dp),
                            tint = MaterialTheme.colorScheme.outline
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = if (books.isEmpty()) stringResource(R.string.library_empty) else "Sonuç bulunamadı.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 40.dp)
                        )
                    }
                }
            } else {
                items(filtered, key = { it.id }) { book ->
                    BookCard(
                        book = book,
                        onClick = { reopen(book) },
                        onFavoriteClick = { viewModel.toggleFavorite(book) },
                        onDelete = { viewModel.delete(book) },
                        onShare = { shareBook(book) },
                        onInfo = { infoDialogBook = book },
                        modifier = Modifier
                            .padding(horizontal = 20.dp)
                            .animateItem()
                    )
                }
            }

            item { Spacer(Modifier.height(12.dp)) }
        }
    }

    infoDialogBook?.let { book ->
        AlertDialog(
            onDismissRequest = { infoDialogBook = null },
            confirmButton = {
                Button(onClick = { infoDialogBook = null }) { Text("Kapat") }
            },
            title = { Text(book.name) },
            text = {
                Column {
                    Text("Tür: ${book.type.uppercase()}")
                    Text("Boyut: ${formatFileSize(book.fileSizeBytes)}")
                    if (book.totalPages > 0) {
                        Text("İlerleme: ${book.lastPage + 1} / ${book.totalPages}")
                    }
                    Text("Eklendi: ${formatDate(book.addedAt)}")
                    Text("Son açılma: ${formatDate(book.lastOpened)}")
                }
            }
        )
    }
}

@Composable
private fun ActionTile(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // "Cam efekti" (glassmorphism): gerçek arka plan bulanıklığı Compose'da
    // native desteklenmediği için, buzlu cam görünümü yarı saydam gradyan +
    // ince ışıltılı kenarlık + hafif gölge kombinasyonuyla elde edilir.
    val glassBrush = Brush.linearGradient(
        colors = listOf(
            MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
            MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
        )
    )
    val borderBrush = Brush.linearGradient(
        colors = listOf(Color.White.copy(alpha = 0.5f), Color.White.copy(alpha = 0.05f))
    )

    Card(
        onClick = onClick,
        modifier = modifier
            .aspectRatio(0.95f)
            .shadow(elevation = 6.dp, shape = MaterialTheme.shapes.large, clip = false),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(glassBrush)
                .border(width = 1.dp, brush = borderBrush, shape = MaterialTheme.shapes.large)
                .padding(12.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(32.dp)
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = Color.White,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                maxLines = 2
            )
        }
    }
}

@Composable
private fun BookCard(
    book: BookEntity,
    onClick: () -> Unit,
    onFavoriteClick: () -> Unit,
    onDelete: () -> Unit,
    onShare: () -> Unit,
    onInfo: () -> Unit,
    modifier: Modifier = Modifier
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Card(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = modifier.fillMaxWidth().padding(vertical = 6.dp)
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(width = 56.dp, height = 80.dp)
            ) {
                if (book.coverPath != null) {
                    SubcomposeAsyncImage(
                        model = File(book.coverPath),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().clip(MaterialTheme.shapes.small),
                        loading = {
                            Box(Modifier.fillMaxSize().clip(MaterialTheme.shapes.small).shimmerEffect())
                        },
                        error = {
                            CoverPlaceholder(
                                title = book.name,
                                modifier = Modifier.clip(MaterialTheme.shapes.small)
                            )
                        }
                    )
                } else {
                    CoverPlaceholder(
                        title = book.name,
                        modifier = Modifier.clip(MaterialTheme.shapes.small)
                    )
                }
            }

            Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(
                    text = book.name,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (book.totalPages > 0) {
                    val accentColor = book.accentColor?.let { Color(it) } ?: MaterialTheme.colorScheme.primary
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { book.progress.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(4.dp).clip(MaterialTheme.shapes.extraSmall),
                        color = accentColor,
                        strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "${book.lastPage + 1} / ${book.totalPages}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = "${formatFileSize(book.fileSizeBytes)} • ${formatDate(book.lastOpened)}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            IconButton(onClick = onFavoriteClick) {
                Icon(
                    imageVector = if (book.favorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    contentDescription = "Favori",
                    tint = if (book.favorite) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "Menü")
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text("Paylaş") },
                        leadingIcon = { Icon(Icons.Filled.Share, contentDescription = null) },
                        onClick = { menuExpanded = false; onShare() }
                    )
                    DropdownMenuItem(
                        text = { Text("Bilgi") },
                        leadingIcon = { Icon(Icons.Filled.Info, contentDescription = null) },
                        onClick = { menuExpanded = false; onInfo() }
                    )
                    DropdownMenuItem(
                        text = { Text("Sil") },
                        leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                        onClick = { menuExpanded = false; onDelete() }
                    )
                }
            }
        }
    }
}

private fun bookTypeIcon(type: String): ImageVector = when (type) {
    BookType.PDF -> Icons.Filled.PictureAsPdf
    BookType.TIFF -> Icons.Filled.Image
    else -> Icons.Filled.MenuBook
}
