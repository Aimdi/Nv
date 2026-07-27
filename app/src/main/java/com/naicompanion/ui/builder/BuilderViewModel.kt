package com.naicompanion.ui.builder

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.naicompanion.data.AppRepository
import com.naicompanion.data.db.ArtistTagEntity
import com.naicompanion.data.db.ArtistWithFavorite
import com.naicompanion.data.db.ComboEntity
import com.naicompanion.data.model.TagEntry
import com.naicompanion.render.PromptRenderer
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class BuilderViewModel(private val repository: AppRepository) : ViewModel() {

    private val _entries = MutableStateFlow<List<TagEntry>>(emptyList())
    val entries: StateFlow<List<TagEntry>> = _entries

    val rendered: StateFlow<String> = _entries
        .map { PromptRenderer.renderCombo(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query

    val suggestions: StateFlow<List<ArtistWithFavorite>> = _query
        .debounce(200)
        .mapLatest { q ->
            if (q.isBlank()) emptyList() else repository.suggestions(q, limit = 8)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** The entry currently open in the edit bottom sheet, if any. */
    private val _editing = MutableStateFlow<TagEntry?>(null)
    val editing: StateFlow<TagEntry?> = _editing

    fun onQueryChange(value: String) {
        _query.value = value
    }

    fun addTag(raw: String) {
        val tag = raw.trim()
        if (tag.isEmpty()) return
        _entries.value = _entries.value + TagEntry(tag = tag)
        _query.value = ""
    }

    fun addArtist(artist: ArtistTagEntity) {
        val usePrefix = repository.artistPrefixEnabled.value
        val tag = if (usePrefix) "artist:${artist.name}" else artist.name
        _entries.value = _entries.value + TagEntry(tag = tag)
        _query.value = ""
    }

    fun remove(id: String) {
        _entries.value = _entries.value.filterNot { it.id == id }
        if (_editing.value?.id == id) _editing.value = null
    }

    fun startEditing(id: String) {
        _editing.value = _entries.value.firstOrNull { it.id == id }
    }

    fun stopEditing() {
        _editing.value = null
    }

    private fun update(id: String, transform: (TagEntry) -> TagEntry) {
        _entries.value = _entries.value.map { if (it.id == id) transform(it) else it }
        _editing.value = _entries.value.firstOrNull { it.id == id }
    }

    fun setBracketCount(id: String, count: Int) {
        update(id) { it.copy(bracketCount = count.coerceIn(-MAX_BRACKETS, MAX_BRACKETS)) }
    }

    fun setNumericWeight(id: String, weight: Float?) {
        update(id) { it.copy(numericWeight = weight) }
    }

    fun toggleEnabled(id: String) {
        update(id) { it.copy(enabled = !it.enabled) }
    }

    fun move(fromIndex: Int, toIndex: Int) {
        val current = _entries.value.toMutableList()
        if (fromIndex !in current.indices || toIndex !in current.indices) return
        current.add(toIndex, current.removeAt(fromIndex))
        _entries.value = current
    }

    fun moveBy(id: String, delta: Int) {
        val current = _entries.value
        val index = current.indexOfFirst { it.id == id }
        if (index < 0) return
        move(index, index + delta)
    }

    fun clear() {
        _entries.value = emptyList()
        _editing.value = null
    }

    fun loadCombo(combo: ComboEntity) {
        _entries.value = repository.decodeEntries(combo.entriesJson)
        _editing.value = null
    }

    fun saveCombo(title: String, onSaved: () -> Unit) {
        if (title.isBlank() || _entries.value.isEmpty()) return
        viewModelScope.launch {
            repository.saveCombo(title, _entries.value)
            onSaved()
        }
    }

    fun saveAsPrompt(title: String, onSaved: () -> Unit) {
        val body = rendered.value
        if (title.isBlank() || body.isBlank()) return
        viewModelScope.launch {
            repository.savePrompt(id = null, title = title, body = body)
            onSaved()
        }
    }

    companion object {
        const val MAX_BRACKETS = 3

        fun factory(repository: AppRepository) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                BuilderViewModel(repository) as T
        }
    }
}
