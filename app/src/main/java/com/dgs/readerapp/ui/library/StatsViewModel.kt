package com.dgs.readerapp.ui.library

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.dgs.readerapp.data.local.BookEntity
import com.dgs.readerapp.data.repository.LibraryRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class LibraryStats(
    val totalBooks: Int = 0,
    val totalReadingTimeMillis: Long = 0L,
    val favoriteCount: Int = 0,
    val topBooks: List<BookEntity> = emptyList(),
    val recentBooks: List<BookEntity> = emptyList()
)

class StatsViewModel(repository: LibraryRepository) : ViewModel() {

    val stats: StateFlow<LibraryStats> = repository.observeBooks()
        .map { books ->
            LibraryStats(
                totalBooks = books.size,
                totalReadingTimeMillis = books.sumOf { it.readingTimeMillis },
                favoriteCount = books.count { it.favorite },
                topBooks = books.sortedByDescending { it.readingTimeMillis }.take(5),
                recentBooks = books.sortedByDescending { it.lastOpened }.take(5)
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LibraryStats())

    companion object {
        fun factory(context: Context): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return StatsViewModel(LibraryRepository.create(context.applicationContext)) as T
            }
        }
    }
}
