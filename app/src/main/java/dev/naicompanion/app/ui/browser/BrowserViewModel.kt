package dev.naicompanion.app.ui.browser

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.naicompanion.app.core.prompt.TagKind
import dev.naicompanion.app.data.catalog.ArtistEntity
import dev.naicompanion.app.data.catalog.CatalogQuery
import dev.naicompanion.app.data.catalog.CatalogSort
import dev.naicompanion.app.data.catalog.StrengthFilter
import dev.naicompanion.app.data.packs.PackRepository
import dev.naicompanion.app.data.repository.CatalogRepository
import dev.naicompanion.app.data.repository.CatalogStatus
import dev.naicompanion.app.data.repository.FavoriteTagRepository
import dev.naicompanion.app.data.settings.SettingsRepository
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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

/** A catalog row plus the per-user and per-device state the grid needs to draw it. */
data class BrowseItem(
    val artist: ArtistEntity,
    val isFavorite: Boolean,
    val thumbnail: File?,
) {
    val kind: TagKind get() = TagKind.fromStorage(artist.kind)
}

data class BrowserFilters(
    val text: String = "",
    val sources: Set<String> = emptySet(),
    val favoritesOnly: Boolean = false,
    val sort: CatalogSort = CatalogSort.STRENGTH_DESC,
    val strengthFilter: StrengthFilter = StrengthFilter.HIDE_WEAK,
    val onlyWithPreview: Boolean = false,
)

data class BrowserUiState(
    val items: List<BrowseItem> = emptyList(),
    val filters: BrowserFilters = BrowserFilters(),
    val availableSources: List<String> = emptyList(),
    val columns: Int = 3,
    val status: CatalogStatus = CatalogStatus.Loading,
    val anyPackInstalled: Boolean = false,
    val loading: Boolean = true,
)

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class BrowserViewModel(
    private val catalogRepository: CatalogRepository,
    private val favoriteTagRepository: FavoriteTagRepository,
    private val packRepository: PackRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val _filters = MutableStateFlow(BrowserFilters())
    val filters: StateFlow<BrowserFilters> = _filters.asStateFlow()

    private val availableSources = MutableStateFlow<List<String>>(emptyList())

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages = _messages.asSharedFlow()

    /** Full-screen one-at-a-time discovery mode. */
    private val _swipeMode = MutableStateFlow(false)
    val swipeMode: StateFlow<Boolean> = _swipeMode.asStateFlow()

    private val catalogResults = _filters
        .debounce { if (it.text.isBlank()) 0L else 200L }
        .distinctUntilChanged()
        .flatMapLatest { filters ->
            catalogRepository.observe(
                CatalogQuery(
                    text = filters.text,
                    sources = filters.sources,
                    requirePreview = filters.onlyWithPreview,
                    strengthFilter = filters.strengthFilter,
                    sort = filters.sort,
                ),
            )
        }

    val uiState: StateFlow<BrowserUiState> = combine(
        catalogResults,
        favoriteTagRepository.favoriteNames,
        packRepository.installed,
        settingsRepository.settings,
        combine(_filters, catalogRepository.status, availableSources) { f, s, sources ->
            Triple(f, s, sources)
        },
    ) { artists, favoriteNames, installedPacks, settings, rest ->
        val (currentFilters, status, sources) = rest
        val filtered = if (currentFilters.favoritesOnly) {
            artists.filter { it.name in favoriteNames }
        } else {
            artists
        }
        BrowserUiState(
            items = filtered.map { artist ->
                BrowseItem(
                    artist = artist,
                    isFavorite = artist.name in favoriteNames,
                    thumbnail = packRepository.resolvePreview(
                        artist.source,
                        artist.preview,
                        PackRepository.PreviewSize.THUMB,
                    ),
                )
            },
            filters = currentFilters,
            availableSources = sources,
            columns = settings.browserColumns,
            status = status,
            anyPackInstalled = installedPacks.isNotEmpty(),
            loading = false,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BrowserUiState())

    init {
        viewModelScope.launch {
            catalogRepository.refresh()
            availableSources.value = catalogRepository.sources()
        }
        viewModelScope.launch {
            settingsRepository.settings.map { it.browserSort to it.onlyWithPreview }
                .distinctUntilChanged()
                .collect { (sort, onlyWithPreview) ->
                    _filters.value = _filters.value.copy(
                        sort = sort,
                        onlyWithPreview = onlyWithPreview,
                    )
                }
        }
    }

    fun onQueryChange(value: String) {
        _filters.value = _filters.value.copy(text = value)
    }

    fun toggleSource(source: String) {
        val current = _filters.value.sources
        _filters.value = _filters.value.copy(
            sources = if (source in current) current - source else current + source,
        )
    }

    fun toggleFavoritesOnly() {
        _filters.value = _filters.value.copy(favoritesOnly = !_filters.value.favoritesOnly)
    }

    fun setSort(sort: CatalogSort) {
        _filters.value = _filters.value.copy(sort = sort)
        viewModelScope.launch { settingsRepository.setBrowserSort(sort) }
    }

    fun toggleOnlyWithPreview() {
        val next = !_filters.value.onlyWithPreview
        _filters.value = _filters.value.copy(onlyWithPreview = next)
        viewModelScope.launch { settingsRepository.setOnlyWithPreview(next) }
    }

    fun cycleStrengthFilter() {
        val order = StrengthFilter.entries
        val next = order[(_filters.value.strengthFilter.ordinal + 1) % order.size]
        _filters.value = _filters.value.copy(strengthFilter = next)
    }

    fun setStrengthFilter(value: StrengthFilter) {
        _filters.value = _filters.value.copy(strengthFilter = value)
    }

    fun setSwipeMode(enabled: Boolean) {
        _swipeMode.value = enabled
    }

    fun toggleFavorite(item: BrowseItem) {
        viewModelScope.launch {
            val added = favoriteTagRepository.toggle(item.artist.name, item.kind)
            _messages.emit(
                if (added) {
                    "Starred ${item.artist.displayName}"
                } else {
                    "Removed ${item.artist.displayName} from favorites"
                },
            )
        }
    }

    fun detailImage(item: BrowseItem): File? = packRepository.resolvePreview(
        item.artist.source,
        item.artist.preview,
        PackRepository.PreviewSize.FULL,
    )

    fun notifyAdded(item: BrowseItem) {
        val strength = item.artist.strength.shortLabel
        _messages.tryEmit("Added ${item.artist.displayName} ($strength)")
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                BrowserViewModel(
                    catalogRepository = container.catalogRepository,
                    favoriteTagRepository = container.favoriteTagRepository,
                    packRepository = container.packRepository,
                    settingsRepository = container.settingsRepository,
                )
            }
        }
    }
}
