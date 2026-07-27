package dev.naicompanion.app.ui.builder

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.naicompanion.app.core.prompt.NovelAiPromptParser
import dev.naicompanion.app.core.prompt.NovelAiPromptRenderer
import dev.naicompanion.app.core.prompt.RenderWarning
import dev.naicompanion.app.core.prompt.TagEntry
import dev.naicompanion.app.core.prompt.TagKind
import dev.naicompanion.app.data.catalog.ArtistEntity
import dev.naicompanion.app.data.catalog.CatalogQuery
import dev.naicompanion.app.data.repository.CatalogRepository
import dev.naicompanion.app.data.repository.ComboRepository
import dev.naicompanion.app.data.repository.DraftRepository
import dev.naicompanion.app.data.repository.PromptRepository
import dev.naicompanion.app.data.settings.AppSettings
import dev.naicompanion.app.data.settings.SettingsRepository
import dev.naicompanion.app.di.AppContainer
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class BuilderUiState(
    val entries: List<TagEntry> = emptyList(),
    val settings: AppSettings = AppSettings(),
    val rendered: String = "",
    val warnings: List<RenderWarning> = emptyList(),
    val comboName: String = "",
) {
    val enabledCount: Int get() = entries.count { it.enabled }
    val isEmpty: Boolean get() = entries.isEmpty()
    val characterCount: Int get() = rendered.length
}

/** One-shot messages for the snackbar. */
data class BuilderMessage(val text: String, val undo: (() -> Unit)? = null)

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class BuilderViewModel(
    private val draftRepository: DraftRepository,
    private val settingsRepository: SettingsRepository,
    private val catalogRepository: CatalogRepository,
    private val comboRepository: ComboRepository,
    private val promptRepository: PromptRepository,
) : ViewModel() {

    private val entries = MutableStateFlow<List<TagEntry>>(emptyList())
    private val comboName = MutableStateFlow("")

    /** Guards against overwriting the stored draft before it has been read back in. */
    private var draftRestored = false

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    private val _messages = MutableSharedFlow<BuilderMessage>(extraBufferCapacity = 4)
    val messages = _messages.asSharedFlow()

    /** The combo being edited, when the user loaded a saved one. */
    private var editingComboId: Long = 0L

    val uiState: StateFlow<BuilderUiState> =
        combine(entries, settingsRepository.settings, comboName) { tags, settings, name ->
            val result = NovelAiPromptRenderer.renderWithWarnings(tags, settings.renderOptions)
            BuilderUiState(
                entries = tags,
                settings = settings,
                rendered = result.text,
                warnings = result.warnings,
                comboName = name,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BuilderUiState())

    val suggestions: StateFlow<List<ArtistEntity>> = _searchQuery
        .debounce(150)
        .distinctUntilChanged()
        .flatMapLatest { query ->
            if (query.isBlank()) {
                flowOf(emptyList())
            } else {
                catalogRepository.observe(
                    CatalogQuery(text = query, limit = SUGGESTION_LIMIT),
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            entries.value = draftRepository.draft.first()
            comboName.value = draftRepository.draftName.first()
            draftRestored = true
        }

        // Persist the draft slightly behind the UI so rapid edits do not thrash disk.
        entries.drop(1)
            .debounce(400)
            .onEach { if (draftRestored) draftRepository.save(it) }
            .launchIn(viewModelScope)

        comboName.drop(1)
            .debounce(400)
            .onEach { if (draftRestored) draftRepository.saveName(it) }
            .launchIn(viewModelScope)
    }

    // region editing

    fun onSearchQueryChange(value: String) {
        _searchQuery.value = value
    }

    /**
     * Adds whatever the user typed. Text containing commas or NovelAI emphasis is run through the
     * parser so pasting a whole prompt fragment expands into individual chips.
     */
    fun addFromInput(rawText: String) {
        val text = rawText.trim()
        if (text.isEmpty()) return
        val parsed = NovelAiPromptParser.parse(text)
        if (parsed.isEmpty()) return
        entries.value = entries.value + parsed
        _searchQuery.value = ""
    }

    fun addCatalogTag(artist: ArtistEntity) {
        addTag(artist.name, TagKind.fromStorage(artist.kind))
        _searchQuery.value = ""
    }

    fun addTag(tag: String, kind: TagKind = TagKind.GENERAL) {
        val normalized = tag.trim()
        if (normalized.isEmpty()) return
        val alreadyPresent = entries.value.any {
            it.tag.equals(normalized, ignoreCase = true) && it.kind == kind
        }
        if (alreadyPresent) {
            _messages.tryEmit(BuilderMessage("\"$normalized\" is already in the combo"))
            return
        }
        entries.value = entries.value + TagEntry(tag = normalized, kind = kind)
    }

    fun remove(id: String) {
        val current = entries.value
        val index = current.indexOfFirst { it.id == id }
        if (index < 0) return
        val removed = current[index]
        entries.value = current.filterNot { it.id == id }
        _messages.tryEmit(
            BuilderMessage("Removed ${removed.tag}") {
                entries.value = entries.value.toMutableList().apply {
                    add(index.coerceAtMost(size), removed)
                }
            },
        )
    }

    fun toggleEnabled(id: String) = update(id) { it.copy(enabled = !it.enabled) }

    fun setBracketCount(id: String, value: Int) = update(id) {
        it.copy(
            bracketCount = value.coerceIn(-TagEntry.MAX_BRACKET_COUNT, TagEntry.MAX_BRACKET_COUNT),
        )
    }

    fun stepBracketCount(id: String, delta: Int) = update(id) {
        val next = (it.bracketCount + delta)
            .coerceIn(-TagEntry.MAX_BRACKET_COUNT, TagEntry.MAX_BRACKET_COUNT)
        it.copy(bracketCount = next)
    }

    fun setNumericWeight(id: String, value: Double?) = update(id) {
        it.copy(
            numericWeight = value?.coerceIn(
                TagEntry.MIN_NUMERIC_WEIGHT,
                TagEntry.MAX_NUMERIC_WEIGHT,
            ),
        )
    }

    fun setKind(id: String, kind: TagKind) = update(id) { it.copy(kind = kind) }

    fun setTagText(id: String, text: String) = update(id) { it.copy(tag = text) }

    fun move(fromIndex: Int, toIndex: Int) {
        val current = entries.value.toMutableList()
        if (fromIndex !in current.indices || toIndex !in current.indices) return
        current.add(toIndex, current.removeAt(fromIndex))
        entries.value = current
    }

    fun clear() {
        val previous = entries.value
        if (previous.isEmpty()) return
        entries.value = emptyList()
        editingComboId = 0L
        comboName.value = ""
        _messages.tryEmit(BuilderMessage("Cleared ${previous.size} tags") { entries.value = previous })
    }

    fun setComboName(value: String) {
        comboName.value = value
    }

    /** Replaces the builder contents with a parsed prompt string (paste, share intent, library). */
    fun loadFromText(text: String, name: String = "") {
        val parsed = NovelAiPromptParser.parse(text)
        if (parsed.isEmpty()) {
            _messages.tryEmit(BuilderMessage("Nothing to load from that text"))
            return
        }
        entries.value = parsed
        comboName.value = name
        editingComboId = 0L
        _messages.tryEmit(BuilderMessage("Loaded ${parsed.size} tags"))
    }

    fun appendFromText(text: String) {
        val parsed = NovelAiPromptParser.parse(text)
        if (parsed.isEmpty()) return
        entries.value = entries.value + parsed
        _messages.tryEmit(BuilderMessage("Added ${parsed.size} tags"))
    }

    fun loadCombo(comboId: Long) {
        viewModelScope.launch {
            val combo = comboRepository.findById(comboId) ?: return@launch
            entries.value = combo.entries
            comboName.value = combo.name
            editingComboId = combo.id
            _messages.tryEmit(BuilderMessage("Loaded \"${combo.name}\""))
        }
    }

    // endregion

    // region saving

    fun saveCombo(name: String) {
        val tags = entries.value
        if (tags.isEmpty()) {
            _messages.tryEmit(BuilderMessage("Add some tags first"))
            return
        }
        viewModelScope.launch {
            val finalName = name.ifBlank { defaultName(tags) }
            editingComboId = comboRepository.save(finalName, tags, editingComboId)
            comboName.value = finalName
            _messages.tryEmit(BuilderMessage("Saved combo \"$finalName\""))
        }
    }

    fun saveAsNewCombo(name: String) {
        editingComboId = 0L
        saveCombo(name)
    }

    fun saveAsPrompt(title: String) {
        val rendered = uiState.value.rendered
        if (rendered.isBlank()) {
            _messages.tryEmit(BuilderMessage("Nothing to save yet"))
            return
        }
        viewModelScope.launch {
            promptRepository.create(title = title, body = rendered)
            _messages.tryEmit(BuilderMessage("Saved to library"))
        }
    }

    // endregion

    private fun update(id: String, transform: (TagEntry) -> TagEntry) {
        entries.value = entries.value.map { if (it.id == id) transform(it) else it }
    }

    private fun defaultName(tags: List<TagEntry>): String =
        tags.take(3).joinToString(", ") { it.tag }.ifBlank { "Untitled combo" }

    companion object {
        private const val SUGGESTION_LIMIT = 30

        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                BuilderViewModel(
                    draftRepository = container.draftRepository,
                    settingsRepository = container.settingsRepository,
                    catalogRepository = container.catalogRepository,
                    comboRepository = container.comboRepository,
                    promptRepository = container.promptRepository,
                )
            }
        }
    }
}
