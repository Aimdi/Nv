package com.nai.promptcompanion.ui.tags

import android.app.Application
import android.net.Uri
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nai.promptcompanion.BuildConfig
import com.nai.promptcompanion.CatalogSeedState
import com.nai.promptcompanion.NaiCompanionApp
import com.nai.promptcompanion.data.catalog.ArtistTagEntity
import com.nai.promptcompanion.data.imagepack.ImagePackState
import com.nai.promptcompanion.data.repo.CatalogSort
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class TagsViewModel(app: Application) : AndroidViewModel(app) {
    private val container = (app as NaiCompanionApp).container
    private val catalog = container.catalog
    private val favorites = container.favorites
    private val builder = container.builderState
    private val imagePack = container.imagePack

    val query = MutableStateFlow("")
    val sort = MutableStateFlow(CatalogSort.POST_COUNT)
    val favoritesOnly = MutableStateFlow(false)
    val swipeMode = MutableStateFlow(false)

    val seedState: StateFlow<CatalogSeedState> = container.catalogSeedState
    val packState: StateFlow<ImagePackState> = imagePack.state

    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val events: SharedFlow<String> = _events.asSharedFlow()

    val favoriteTags: StateFlow<Set<String>> = favorites.observeAll()
        .map { list -> list.mapTo(HashSet()) { it.tag } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    val items: StateFlow<List<ArtistTagEntity>> = combine(
        query.debounce(150).flatMapLatest { catalog.observe(it) },
        favoriteTags,
        sort,
        favoritesOnly,
    ) { list, favs, sortMode, favOnly ->
        val filtered = if (favOnly) list.filter { it.tag in favs } else list
        when (sortMode) {
            CatalogSort.POST_COUNT -> filtered.sortedByDescending { it.postCount }
            CatalogSort.NAME -> filtered.sortedBy { it.tag }
            CatalogSort.UNIQUENESS -> filtered.sortedByDescending { it.uniqueness }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Bundled demo previews shipped in assets (a handful of well-known artists). */
    private val bundledPreviews: Set<String> by lazy {
        runCatching {
            app.assets.list("previews")?.mapNotNull {
                it.removeSuffix(".webp").takeIf(String::isNotBlank)
            }?.toSet()
        }.getOrDefault(emptySet()) ?: emptySet()
    }

    /** Coil model for the grid thumbnail: pack file, bundled asset, or null (placeholder). */
    fun thumbnailModel(tag: String): Any? {
        imagePack.thumbFile(tag)?.let { return it }
        if (tag in bundledPreviews) return "file:///android_asset/previews/$tag.webp".toUri()
        return null
    }

    fun detailModel(tag: String): Any? {
        imagePack.detailFile(tag)?.let { return it }
        if (tag in bundledPreviews) return "file:///android_asset/previews/$tag.webp".toUri()
        return null
    }

    fun toggleFavorite(tag: String) {
        viewModelScope.launch { favorites.toggle(tag) }
    }

    /** Artist tags get the V4/V4.5 `artist:` prefix when inserted into prompts. */
    fun addToCombo(tag: String, withWeight: Double? = null) {
        val prefixed = if (tag.startsWith("artist:")) tag else "artist:$tag"
        builder.addTag(prefixed)
        if (withWeight != null) {
            val idx = builder.entries.value.lastIndex
            val entry = builder.entries.value.getOrNull(idx)
            if (entry != null) builder.updateAt(idx, entry.copy(numericWeight = withWeight))
        }
        _events.tryEmit("Added \"$prefixed\" to combo")
    }

    fun copyTagEvent(tag: String) {
        _events.tryEmit("Copied \"$tag\"")
    }

    fun defaultPackUrl(): String = BuildConfig.IMAGEPACK_MANIFEST_URL

    suspend fun savedPackUrl(): String? = container.settings.imagePackUrl.first()

    fun savePackUrl(url: String) {
        viewModelScope.launch { container.settings.setImagePackUrl(url) }
    }

    fun downloadPack(url: String) {
        savePackUrl(url)
        container.appScope.launch { imagePack.download(url) }
    }

    fun cancelDownload() = imagePack.cancelDownload()

    fun removePack() {
        container.appScope.launch { imagePack.removePack() }
    }
}
