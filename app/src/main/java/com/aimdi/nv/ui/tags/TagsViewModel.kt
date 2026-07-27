package com.aimdi.nv.ui.tags

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aimdi.nv.NaiComposerApp
import com.aimdi.nv.data.model.ArtistEntity
import com.aimdi.nv.data.preview.PreviewPackManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class TagsViewModel : ViewModel() {
    private val artists = NaiComposerApp.instance.artists
    private val previewPack = NaiComposerApp.instance.previewPack

    var query by mutableStateOf("")
        private set
    var favoritesOnly by mutableStateOf(false)
        private set
    var results by mutableStateOf<List<ArtistEntity>>(emptyList())
        private set
    var totalCount by mutableStateOf(0)
        private set
    var loading by mutableStateOf(true)
        private set
    var selected by mutableStateOf<ArtistEntity?>(null)
        private set
    var packState by mutableStateOf<PreviewPackManager.State>(PreviewPackManager.State.NotInstalled)
        private set

    private var searchJob: Job? = null

    init {
        viewModelScope.launch {
            totalCount = artists.count()
            refresh()
        }
        viewModelScope.launch {
            previewPack.state.collectLatest { packState = it }
        }
        viewModelScope.launch { previewPack.refresh() }
    }

    fun onQueryChange(value: String) {
        query = value
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(180)
            refresh()
        }
    }

    fun toggleFavorites() {
        favoritesOnly = !favoritesOnly
        viewModelScope.launch { refresh() }
    }

    fun select(artist: ArtistEntity?) {
        selected = artist
    }

    fun toggleFavorite(artist: ArtistEntity) {
        viewModelScope.launch {
            artists.setFavorite(artist.id, !artist.isFavorite)
            refresh()
            selected = artists.get(artist.id)
        }
    }

    fun thumbPath(name: String) = previewPack.thumbFileFor(name)?.absolutePath
    fun detailPath(name: String) = previewPack.detailFileFor(name)?.absolutePath

    private suspend fun refresh() {
        loading = true
        results = artists.search(
            query = query,
            favoritesOnly = favoritesOnly,
            sort = "posts",
            limit = 200,
        )
        if (totalCount == 0) totalCount = artists.count()
        loading = false
    }
}
