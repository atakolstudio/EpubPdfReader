package com.dgs.readerapp.ui.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dgs.readerapp.data.formatDuration
import com.dgs.readerapp.ui.library.StatsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val viewModel: StatsViewModel = viewModel(factory = StatsViewModel.factory(context))
    val stats by viewModel.stats.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Okuma İstatistikleri") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatCard(title = "Kitap", value = stats.totalBooks.toString(), modifier = Modifier.weight(1f))
                    StatCard(title = "Favori", value = stats.favoriteCount.toString(), modifier = Modifier.weight(1f))
                }
            }
            item {
                StatCard(
                    title = "Toplam Okuma Süresi",
                    value = formatDuration(stats.totalReadingTimeMillis),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                Text("En Çok Okunanlar", style = MaterialTheme.typography.titleLarge)
            }
            if (stats.topBooks.isEmpty()) {
                item { Text("Henüz veri yok.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            } else {
                items(stats.topBooks, key = { "top_${it.id}" }) { book ->
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = book.name,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = formatDuration(book.readingTimeMillis),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        HorizontalDivider()
                    }
                }
            }
            item {
                Text("Son Açılanlar", style = MaterialTheme.typography.titleLarge)
            }
            if (stats.recentBooks.isEmpty()) {
                item { Text("Henüz veri yok.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            } else {
                items(stats.recentBooks, key = { "recent_${it.id}" }) { book ->
                    Column {
                        Text(
                            text = book.name,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun StatCard(title: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = value, style = MaterialTheme.typography.headlineLarge)
            Text(text = title, style = MaterialTheme.typography.labelLarge)
        }
    }
}
