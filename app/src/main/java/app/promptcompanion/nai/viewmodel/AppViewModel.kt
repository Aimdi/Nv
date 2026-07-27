package app.promptcompanion.nai.viewmodel

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.promptcompanion.nai.PromptCompanionApp
import app.promptcompanion.nai.data.db.entity.ArtistEntity
import app.promptcompanion.nai.data.db.entity.ComboEntity
import app.promptcompanion.nai.data.db.entity.PromptEntity
import app.promptcompanion.nai.data.export.TagEntryJson
import app.promptcompanion.nai.data.repository.PreviewPackManager
import app.promptcompanion.nai.domain.NovelAiSyntax
import app.promptcompanion.nai.domain.TagEntry
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BuilderUiState(
    val entries: List<TagEntry> = emptyList(),
    val draftTag: String = "",
    val selectedId: String? = null,
    val rendered: String = "",
)

data class LibraryUiState(
    val prompts: List<PromptEntity> = emptyList(),
    val combos: List<ComboEntity> = emptyList(),
    val query: String = "",
    val searching: Boolean = false,
)

data class BrowserUiState(
    val artists: List<ArtistEntity> = emptyList(),
    val query: String = "",
    val loading: Boolean = true,
    val favoritesOnly: Boolean = false,
    val sort: String = "posts",
    val source: String? = null,
    val catalogCount: Int = 0,
    val packInstalled: Boolean = false,
    val selected: ArtistEntity? = null,
)

data class SettingsUiState(
    val pack: PreviewPackManager.PackStatus? = null,
    val downloading: Boolean = false,
    val progressText: String = "",
    val packUrl: String = PreviewPackManager.DEFAULT_PACK_URL,
    val message: String? = null,
)

sealed interface UiEvent {
    data class Snackbar(val message: String) : UiEvent
}

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as PromptCompanionApp
    private val prompts = app.promptRepository
    private val artists = app.artistRepository
    private val packs = app.previewPackManager

    private val _builder = MutableStateFlow(BuilderUiState())
    val builder: StateFlow<BuilderUiState> = _builder.asStateFlow()

    private val _library = MutableStateFlow(LibraryUiState())
    val library: StateFlow<LibraryUiState> = _library.asStateFlow()

    private val _browser = MutableStateFlow(BrowserUiState())
    val browser: StateFlow<BrowserUiState> = _browser.asStateFlow()

    private val _settings = MutableStateFlow(SettingsUiState())
    val settings: StateFlow<SettingsUiState> = _settings.asStateFlow()

    private val _events = MutableSharedFlow<UiEvent>()
    val events: SharedFlow<UiEvent> = _events.asSharedFlow()

    private var searchJob: Job? = null
    private var browserJob: Job? = null

    init {
        viewModelScope.launch {
            artists.ensureSeeded(getApplication())
            refreshBrowser()
            refreshPackStatus()
        }
        viewModelScope.launch {
            prompts.observePrompts().collect { list ->
                _library.update { it.copy(prompts = list) }
            }
        }
        viewModelScope.launch {
            prompts.observeCombos().collect { list ->
                _library.update { it.copy(combos = list) }
            }
        }
    }

    // ---- Builder ----

    fun setDraftTag(value: String) {
        _builder.update { it.copy(draftTag = value) }
    }

    fun addDraftTag(asArtist: Boolean = false) {
        val raw = _builder.value.draftTag.trim()
        if (raw.isEmpty()) return
        addTag(raw, asArtist)
        _builder.update { it.copy(draftTag = "") }
    }

    fun addTag(raw: String, asArtist: Boolean = false) {
        val forceArtist = asArtist || raw.startsWith("artist:", ignoreCase = true)
        val tag = if (forceArtist && !raw.startsWith("artist:", ignoreCase = true)) {
            "artist:${raw.replace('_', ' ')}"
        } else {
            NovelAiSyntax.normalizeTag(raw, forceArtistPrefix = false)
        }
        val entry = TagEntry(tag = tag, forceArtistPrefix = forceArtist)
        _builder.update { state ->
            val entries = state.entries + entry
            state.copy(entries = entries, rendered = NovelAiSyntax.renderPrompt(entries), selectedId = entry.id)
        }
    }

    fun selectEntry(id: String?) {
        _builder.update { it.copy(selectedId = id) }
    }

    fun updateEntry(id: String, transform: (TagEntry) -> TagEntry) {
        _builder.update { state ->
            val entries = state.entries.map { if (it.id == id) transform(it) else it }
            state.copy(entries = entries, rendered = NovelAiSyntax.renderPrompt(entries))
        }
    }

    fun removeEntry(id: String) {
        _builder.update { state ->
            val entries = state.entries.filterNot { it.id == id }
            state.copy(
                entries = entries,
                rendered = NovelAiSyntax.renderPrompt(entries),
                selectedId = state.selectedId?.takeIf { it != id },
            )
        }
    }

    fun moveEntry(from: Int, to: Int) {
        _builder.update { state ->
            if (from !in state.entries.indices || to !in state.entries.indices) return@update state
            val mutable = state.entries.toMutableList()
            val item = mutable.removeAt(from)
            mutable.add(to, item)
            state.copy(entries = mutable, rendered = NovelAiSyntax.renderPrompt(mutable))
        }
    }

    fun clearBuilder() {
        _builder.value = BuilderUiState()
    }

    fun loadEntries(entries: List<TagEntry>) {
        _builder.value = BuilderUiState(
            entries = entries,
            rendered = NovelAiSyntax.renderPrompt(entries),
        )
    }

    fun loadPromptBody(body: String) {
        loadEntries(NovelAiSyntax.parsePrompt(body))
    }

    fun loadCombo(combo: ComboEntity) {
        loadEntries(TagEntryJson.decode(combo.entriesJson))
    }

    fun copyRendered(openNovelAi: Boolean = false) {
        val text = _builder.value.rendered
        if (text.isBlank()) {
            viewModelScope.launch { _events.emit(UiEvent.Snackbar("Nothing to copy")) }
            return
        }
        val cm = getApplication<Application>().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("NovelAI prompt", text))
        viewModelScope.launch {
            _events.emit(UiEvent.Snackbar(if (openNovelAi) "Copied — opening NovelAI" else "Copied to clipboard"))
        }
        if (openNovelAi) {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://novelai.net/image")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            runCatching { getApplication<Application>().startActivity(intent) }
        }
    }

    fun shareRendered() {
        val text = _builder.value.rendered
        if (text.isBlank()) return
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val chooser = Intent.createChooser(send, "Share prompt").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        getApplication<Application>().startActivity(chooser)
    }

    fun saveCurrentAsPrompt(title: String) {
        viewModelScope.launch {
            val body = _builder.value.rendered
            if (body.isBlank()) {
                _events.emit(UiEvent.Snackbar("Builder is empty"))
                return@launch
            }
            prompts.savePrompt(title, body)
            _events.emit(UiEvent.Snackbar("Saved to library"))
        }
    }

    fun saveCurrentAsCombo(title: String) {
        viewModelScope.launch {
            val entries = _builder.value.entries
            if (entries.isEmpty()) {
                _events.emit(UiEvent.Snackbar("Builder is empty"))
                return@launch
            }
            prompts.saveCombo(title, entries)
            _events.emit(UiEvent.Snackbar("Combo saved"))
        }
    }

    // ---- Library ----

    fun setLibraryQuery(query: String) {
        _library.update { it.copy(query = query) }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(200)
            if (query.isBlank()) {
                // Flow observers already keep full lists; no-op.
                _library.update { it.copy(searching = false) }
            } else {
                _library.update { it.copy(searching = true) }
                val found = prompts.searchPrompts(query)
                _library.update { it.copy(prompts = found, searching = false) }
            }
        }
    }

    fun deletePrompt(id: Long) = viewModelScope.launch { prompts.deletePrompt(id) }
    fun deleteCombo(id: Long) = viewModelScope.launch { prompts.deleteCombo(id) }
    fun togglePromptFavorite(prompt: PromptEntity) =
        viewModelScope.launch { prompts.setPromptFavorite(prompt.id, !prompt.isFavorite) }
    fun toggleComboFavorite(combo: ComboEntity) =
        viewModelScope.launch { prompts.setComboFavorite(combo.id, !combo.isFavorite) }

    fun exportToUri(uri: Uri) = viewModelScope.launch {
        runCatching { prompts.writeExportToUri(getApplication(), uri) }
            .onSuccess { _events.emit(UiEvent.Snackbar("Exported JSON")) }
            .onFailure { _events.emit(UiEvent.Snackbar("Export failed: ${it.message}")) }
    }

    fun importFromUri(uri: Uri) = viewModelScope.launch {
        runCatching { prompts.readImportFromUri(getApplication(), uri) }
            .onSuccess { _events.emit(UiEvent.Snackbar("Import complete")) }
            .onFailure { _events.emit(UiEvent.Snackbar("Import failed: ${it.message}")) }
    }

    // ---- Browser ----

    fun setBrowserQuery(query: String) {
        _browser.update { it.copy(query = query) }
        browserJob?.cancel()
        browserJob = viewModelScope.launch {
            delay(180)
            refreshBrowser()
        }
    }

    fun setFavoritesOnly(value: Boolean) {
        _browser.update { it.copy(favoritesOnly = value) }
        refreshBrowser()
    }

    fun setSort(sort: String) {
        _browser.update { it.copy(sort = sort) }
        refreshBrowser()
    }

    fun setSourceFilter(source: String?) {
        _browser.update { it.copy(source = source) }
        refreshBrowser()
    }

    fun refreshBrowser() {
        viewModelScope.launch {
            _browser.update { it.copy(loading = true) }
            val state = _browser.value
            val list = if (state.query.isNotBlank() && !state.favoritesOnly) {
                artists.search(state.query)
            } else {
                artists.filtered(
                    source = state.source,
                    favoritesOnly = state.favoritesOnly,
                    sort = state.sort,
                    limit = 300,
                )
            }
            val count = artists.count()
            val pack = packs.status()
            _browser.update {
                it.copy(
                    artists = list,
                    loading = false,
                    catalogCount = count,
                    packInstalled = pack.installed,
                )
            }
        }
    }

    fun selectArtist(artist: ArtistEntity?) {
        _browser.update { it.copy(selected = artist) }
    }

    fun toggleArtistFavorite(artist: ArtistEntity) {
        viewModelScope.launch {
            artists.setFavorite(artist.id, !artist.isFavorite)
            refreshBrowser()
        }
    }

    fun addArtistToBuilder(artist: ArtistEntity, weighted: Boolean = false) {
        val tag = "artist:${artist.displayName}"
        if (weighted) {
            val entry = TagEntry(tag = tag, numericWeight = 1.1, forceArtistPrefix = true)
            _builder.update { state ->
                val entries = state.entries + entry
                state.copy(entries = entries, rendered = NovelAiSyntax.renderPrompt(entries), selectedId = entry.id)
            }
        } else {
            addTag(tag, asArtist = true)
        }
        viewModelScope.launch { _events.emit(UiEvent.Snackbar("Added ${artist.displayName}")) }
    }

    // ---- Settings / pack ----

    fun refreshPackStatus() {
        viewModelScope.launch {
            _settings.update { it.copy(pack = packs.status()) }
        }
    }

    fun setPackUrl(url: String) {
        _settings.update { it.copy(packUrl = url) }
    }

    fun downloadPack() {
        viewModelScope.launch {
            _settings.update { it.copy(downloading = true, progressText = "Starting…", message = null) }
            runCatching {
                packs.download(url = _settings.value.packUrl) { downloaded, total ->
                    val text = if (total > 0) {
                        val pct = (downloaded * 100 / total).toInt()
                        "$pct% (${downloaded / (1024 * 1024)} / ${total / (1024 * 1024)} MB)"
                    } else {
                        "${downloaded / (1024 * 1024)} MB"
                    }
                    _settings.update { it.copy(progressText = text) }
                }
            }.onSuccess {
                _settings.update {
                    it.copy(downloading = false, message = "Preview pack installed", progressText = "")
                }
                refreshPackStatus()
                refreshBrowser()
                _events.emit(UiEvent.Snackbar("Preview pack installed"))
            }.onFailure { err ->
                _settings.update {
                    it.copy(
                        downloading = false,
                        message = "Download failed: ${err.message}",
                        progressText = "",
                    )
                }
                _events.emit(UiEvent.Snackbar("Download failed — set a valid pack URL in Settings"))
            }
        }
    }

    fun removePack() {
        viewModelScope.launch {
            packs.remove()
            refreshPackStatus()
            refreshBrowser()
            _events.emit(UiEvent.Snackbar("Preview pack removed"))
        }
    }

    fun thumbnailFile(artist: ArtistEntity) = packs.resolveThumbnail(artist.thumbnailFile)
}
