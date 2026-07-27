package com.naicompanion.ui.tags

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.naicompanion.data.AppRepository
import com.naicompanion.data.db.ArtistWithFavorite
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class TagsViewModel(private val repository: AppRepository) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query

    private val _favoritesOnly = MutableStateFlow(false)
    val favoritesOnly: StateFlow<Boolean> = _favoritesOnly

    private val _sortByName = MutableStateFlow(false)
    val sortByName: StateFlow<Boolean> = _sortByName

    val artistPrefixEnabled: StateFlow<Boolean> = repository.artistPrefixEnabled
    val catalogReady: StateFlow<Boolean> = repository.catalogReady

    val artists: StateFlow<List<ArtistWithFavorite>> =
        combine(_query, _favoritesOnly, _sortByName) { q, f, s -> Triple(q, f, s) }
            .flatMapLatest { (q, f, s) -> repository.artists(q, f, s) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch { repository.ensureCatalogSeeded() }
    }

    fun setQuery(value: String) {
        _query.value = value
    }

    fun setFavoritesOnly(value: Boolean) {
        _favoritesOnly.value = value
    }

    fun setSortByName(value: Boolean) {
        _sortByName.value = value
    }

    fun setArtistPrefixEnabled(value: Boolean) {
        repository.setArtistPrefixEnabled(value)
    }

    fun toggleFavorite(item: ArtistWithFavorite) {
        viewModelScope.launch {
            repository.toggleArtistFavorite(item.artist.id, item.isFavorite)
        }
    }

    companion object {
        fun factory(repository: AppRepository) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                TagsViewModel(repository) as T
        }
    }
}
