package com.dgs.readerapp.ui.library

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.dgs.readerapp.data.local.BookEntity
import com.dgs.readerapp.data.repository.LibraryRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class SortOption(val label: String) {
    RECENT("Son açılan"),
    NAME("Ad (A-Z)"),
    PROGRESS("İlerleme")
}

enum class TypeFilter(val label: String) {
    ALL("Tümü"),
    PDF("PDF"),
    EPUB("EPUB"),
    TIFF("TIFF"),
    FAVORITES("Favoriler")
}

/** MVVM: UI durumunu StateFlow ile UI katmanına sunar. */
class LibraryViewModel(private val repository: LibraryRepository) : ViewModel() {

    val books: StateFlow<List<BookEntity>> = repository.observeBooks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun toggleFavorite(book: BookEntity) {
        viewModelScope.launch { repository.toggleFavorite(book.id, !book.favorite) }
    }

    fun delete(book: BookEntity) {
        viewModelScope.launch { repository.delete(book.id) }
    }

    fun restoreBackup(books: List<BookEntity>) {
        viewModelScope.launch { repository.importAll(books) }
    }

    companion object {
        fun factory(context: Context): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return LibraryViewModel(LibraryRepository.create(context.applicationContext)) as T
            }
        }
    }
}
