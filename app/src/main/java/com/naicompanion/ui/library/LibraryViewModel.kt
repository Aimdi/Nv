package com.naicompanion.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.naicompanion.data.AppRepository
import com.naicompanion.data.backup.ImportResult
import com.naicompanion.data.db.ComboEntity
import com.naicompanion.data.db.PromptEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModel(private val repository: AppRepository) : ViewModel() {

    enum class Tab { PROMPTS, COMBOS }

    private val _tab = MutableStateFlow(Tab.PROMPTS)
    val tab: StateFlow<Tab> = _tab

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query

    private val _favoritesOnly = MutableStateFlow(false)
    val favoritesOnly: StateFlow<Boolean> = _favoritesOnly

    val prompts: StateFlow<List<PromptEntity>> =
        combine(_query, _favoritesOnly) { q, f -> q to f }
            .flatMapLatest { (q, f) -> repository.prompts(q, f) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val combos: StateFlow<List<ComboEntity>> =
        combine(_query, _favoritesOnly) { q, f -> q to f }
            .flatMapLatest { (q, f) -> repository.combos(q, f) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setTab(tab: Tab) {
        _tab.value = tab
    }

    fun setQuery(value: String) {
        _query.value = value
    }

    fun setFavoritesOnly(value: Boolean) {
        _favoritesOnly.value = value
    }

    fun togglePromptFavorite(prompt: PromptEntity) {
        viewModelScope.launch { repository.togglePromptFavorite(prompt.id, prompt.isFavorite) }
    }

    fun deletePrompt(id: Long) {
        viewModelScope.launch { repository.deletePrompt(id) }
    }

    fun toggleComboFavorite(combo: ComboEntity) {
        viewModelScope.launch { repository.toggleComboFavorite(combo.id, combo.isFavorite) }
    }

    fun deleteCombo(id: Long) {
        viewModelScope.launch { repository.deleteCombo(id) }
    }

    fun savePrompt(id: Long?, title: String, body: String) {
        viewModelScope.launch { repository.savePrompt(id, title, body) }
    }

    fun decodeCombo(combo: ComboEntity) = repository.decodeEntries(combo.entriesJson)

    suspend fun exportBackup(): String = repository.exportBackup()

    suspend fun importBackup(text: String): ImportResult = repository.importBackup(text)

    companion object {
        fun factory(repository: AppRepository) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                LibraryViewModel(repository) as T
        }
    }
}
