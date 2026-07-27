package com.aimdi.nv.ui.library

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aimdi.nv.NaiComposerApp
import com.aimdi.nv.data.model.ComboEntity
import com.aimdi.nv.data.model.PromptEntity
import com.aimdi.nv.domain.TagEntry
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class LibraryViewModel : ViewModel() {
    private val promptsRepo = NaiComposerApp.instance.prompts
    private val combosRepo = NaiComposerApp.instance.combos

    val prompts = promptsRepo.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val combos = combosRepo.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    var query by mutableStateOf("")
        private set

    var searchResults by mutableStateOf<List<PromptEntity>?>(null)
        private set

    var tab by mutableStateOf(0) // 0 prompts, 1 combos
        private set

    fun selectTab(index: Int) {
        tab = index
    }

    fun onQueryChange(value: String) {
        query = value
        if (value.isBlank()) {
            searchResults = null
            return
        }
        viewModelScope.launch {
            searchResults = promptsRepo.search(value)
        }
    }

    fun deletePrompt(id: Long) = viewModelScope.launch { promptsRepo.delete(id) }
    fun deleteCombo(id: Long) = viewModelScope.launch { combosRepo.delete(id) }
    fun toggleFavorite(id: Long) = viewModelScope.launch { promptsRepo.toggleFavorite(id) }

    fun parseCombo(combo: ComboEntity): List<TagEntry> = combosRepo.parseEntries(combo)
}
