package dev.naicompanion.app.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.naicompanion.app.core.prompt.Combo
import dev.naicompanion.app.core.prompt.NovelAiPromptRenderer
import dev.naicompanion.app.data.repository.ComboRepository
import dev.naicompanion.app.data.repository.PromptRepository
import dev.naicompanion.app.data.settings.SettingsRepository
import dev.naicompanion.app.data.user.PromptEntity
import dev.naicompanion.app.di.AppContainer
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class LibraryTab(val label: String) {
    PROMPTS("Prompts"),
    COMBOS("Combos"),
}

data class LibraryUiState(
    val prompts: List<PromptEntity> = emptyList(),
    val combos: List<Combo> = emptyList(),
    val folders: List<String> = emptyList(),
    val query: String = "",
    val folderFilter: String? = null,
    val favoritesOnly: Boolean = false,
) {
    val visiblePrompts: List<PromptEntity>
        get() = prompts.filter { prompt ->
            (folderFilter == null || prompt.folder == folderFilter) &&
                (!favoritesOnly || prompt.isFavorite)
        }
}

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class LibraryViewModel(
    private val promptRepository: PromptRepository,
    private val comboRepository: ComboRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val folderFilter = MutableStateFlow<String?>(null)
    private val favoritesOnly = MutableStateFlow(false)

    private val _tab = MutableStateFlow(LibraryTab.PROMPTS)
    val tab: StateFlow<LibraryTab> = _tab.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages = _messages.asSharedFlow()

    private val prompts = query
        .debounce(200)
        .flatMapLatest { promptRepository.search(it) }

    val uiState: StateFlow<LibraryUiState> = combine(
        prompts,
        comboRepository.observeAll(),
        promptRepository.observeFolders(),
        combine(query, folderFilter, favoritesOnly) { q, folder, favorites ->
            Triple(q, folder, favorites)
        },
    ) { promptList, comboList, folders, filters ->
        val (currentQuery, folder, favorites) = filters
        LibraryUiState(
            prompts = promptList,
            combos = comboList.filterByQuery(currentQuery)
                .filter { !favorites || it.isFavorite },
            folders = folders,
            query = currentQuery,
            folderFilter = folder,
            favoritesOnly = favorites,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryUiState())

    /** Rendered previews for combos, using the user's current render settings. */
    val renderOptions = settingsRepository.settings
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            dev.naicompanion.app.data.settings.AppSettings(),
        )

    fun setTab(value: LibraryTab) {
        _tab.value = value
    }

    fun onQueryChange(value: String) {
        query.value = value
    }

    fun setFolderFilter(value: String?) {
        folderFilter.value = value
    }

    fun toggleFavoritesOnly() {
        favoritesOnly.value = !favoritesOnly.value
    }

    fun togglePromptFavorite(prompt: PromptEntity) {
        viewModelScope.launch { promptRepository.setFavorite(prompt.id, !prompt.isFavorite) }
    }

    fun toggleComboFavorite(combo: Combo) {
        viewModelScope.launch { comboRepository.setFavorite(combo.id, !combo.isFavorite) }
    }

    fun deletePrompt(prompt: PromptEntity) {
        viewModelScope.launch {
            promptRepository.delete(prompt.id)
            _messages.emit("Deleted \"${prompt.title}\"")
        }
    }

    fun deleteCombo(combo: Combo) {
        viewModelScope.launch {
            comboRepository.delete(combo.id)
            _messages.emit("Deleted \"${combo.name}\"")
        }
    }

    fun updatePrompt(prompt: PromptEntity) {
        viewModelScope.launch { promptRepository.save(prompt) }
    }

    fun renderCombo(combo: Combo): String =
        NovelAiPromptRenderer.render(combo.entries, renderOptions.value.renderOptions)

    private fun List<Combo>.filterByQuery(rawQuery: String): List<Combo> {
        val needle = rawQuery.trim().lowercase()
        if (needle.isEmpty()) return this
        return filter { combo ->
            combo.name.lowercase().contains(needle) ||
                combo.entries.any { it.tag.lowercase().contains(needle) }
        }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                LibraryViewModel(
                    promptRepository = container.promptRepository,
                    comboRepository = container.comboRepository,
                    settingsRepository = container.settingsRepository,
                )
            }
        }
    }
}
