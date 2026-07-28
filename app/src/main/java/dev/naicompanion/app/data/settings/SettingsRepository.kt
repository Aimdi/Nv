package dev.naicompanion.app.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import dev.naicompanion.app.core.prompt.NovelAiModel
import dev.naicompanion.app.core.prompt.RenderOptions
import dev.naicompanion.app.data.catalog.CatalogSort
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** User-visible preferences plus the render options derived from them. */
data class AppSettings(
    val model: NovelAiModel = NovelAiModel.V4_5,
    val underscoresToSpaces: Boolean = true,
    val artistPrefix: Boolean = true,
    val multilineSeparator: Boolean = false,
    val browserColumns: Int = 3,
    val browserSort: CatalogSort = CatalogSort.POST_COUNT_DESC,
    val copyOpensNovelAi: Boolean = false,
    val packManifestUrl: String = DEFAULT_PACK_MANIFEST_URL,
    val onlyWithPreview: Boolean = false,
    /** When true, builder search merges live Danbooru autocomplete with the local catalog. */
    val onlineTagSearch: Boolean = true,
    /** When true, artist browser loads HuggingFace CDN previews on demand via Coil. */
    val onlineArtistPreviews: Boolean = true,
    /**
     * Conservative NSFW gate for online suggestions. Default off: rating:* meta tags other than
     * general are hidden from Danbooru autocomplete.
     */
    val allowNsfwTags: Boolean = false,
) {
    val renderOptions: RenderOptions
        get() = RenderOptions(
            model = model,
            underscoresToSpaces = underscoresToSpaces,
            artistPrefix = artistPrefix,
            separator = if (multilineSeparator) ",\n" else ", ",
        )

    companion object {
        /**
         * Points at this project's own releases. Overridable in Settings so a user can host their
         * own packs, which is also how the packs feature is exercised without a public host.
         */
        const val DEFAULT_PACK_MANIFEST_URL =
            "https://github.com/Aimdi/Nv/releases/latest/download/packs.json"
    }
}

class SettingsRepository(private val dataStore: DataStore<Preferences>) {

    val settings: Flow<AppSettings> = dataStore.data.map { prefs ->
        AppSettings(
            model = NovelAiModel.fromStorage(prefs[KEY_MODEL]),
            underscoresToSpaces = prefs[KEY_UNDERSCORES] ?: true,
            artistPrefix = prefs[KEY_ARTIST_PREFIX] ?: true,
            multilineSeparator = prefs[KEY_MULTILINE] ?: false,
            browserColumns = prefs[KEY_COLUMNS] ?: 3,
            browserSort = runCatching { CatalogSort.valueOf(prefs[KEY_SORT] ?: "") }
                .getOrDefault(CatalogSort.POST_COUNT_DESC),
            copyOpensNovelAi = prefs[KEY_COPY_OPENS] ?: false,
            packManifestUrl = prefs[KEY_MANIFEST_URL] ?: AppSettings.DEFAULT_PACK_MANIFEST_URL,
            onlyWithPreview = prefs[KEY_ONLY_PREVIEW] ?: false,
            onlineTagSearch = prefs[KEY_ONLINE_SEARCH] ?: true,
            onlineArtistPreviews = prefs[KEY_ONLINE_PREVIEWS] ?: true,
            allowNsfwTags = prefs[KEY_ALLOW_NSFW] ?: false,
        )
    }

    suspend fun setModel(model: NovelAiModel) = edit { it[KEY_MODEL] = model.name }
    suspend fun setUnderscoresToSpaces(value: Boolean) = edit { it[KEY_UNDERSCORES] = value }
    suspend fun setArtistPrefix(value: Boolean) = edit { it[KEY_ARTIST_PREFIX] = value }
    suspend fun setMultilineSeparator(value: Boolean) = edit { it[KEY_MULTILINE] = value }
    suspend fun setBrowserColumns(value: Int) = edit { it[KEY_COLUMNS] = value.coerceIn(2, 5) }
    suspend fun setBrowserSort(value: CatalogSort) = edit { it[KEY_SORT] = value.name }
    suspend fun setCopyOpensNovelAi(value: Boolean) = edit { it[KEY_COPY_OPENS] = value }
    suspend fun setOnlyWithPreview(value: Boolean) = edit { it[KEY_ONLY_PREVIEW] = value }
    suspend fun setOnlineTagSearch(value: Boolean) = edit { it[KEY_ONLINE_SEARCH] = value }
    suspend fun setOnlineArtistPreviews(value: Boolean) = edit { it[KEY_ONLINE_PREVIEWS] = value }
    suspend fun setAllowNsfwTags(value: Boolean) = edit { it[KEY_ALLOW_NSFW] = value }

    suspend fun setPackManifestUrl(value: String) = edit {
        val trimmed = value.trim()
        it[KEY_MANIFEST_URL] =
            trimmed.ifEmpty { AppSettings.DEFAULT_PACK_MANIFEST_URL }
    }

    private suspend fun edit(block: (MutablePreferences) -> Unit) {
        dataStore.edit(block)
    }

    private companion object {
        val KEY_MODEL = stringPreferencesKey("model")
        val KEY_UNDERSCORES = booleanPreferencesKey("underscores_to_spaces")
        val KEY_ARTIST_PREFIX = booleanPreferencesKey("artist_prefix")
        val KEY_MULTILINE = booleanPreferencesKey("multiline_separator")
        val KEY_COLUMNS = intPreferencesKey("browser_columns")
        val KEY_SORT = stringPreferencesKey("browser_sort")
        val KEY_COPY_OPENS = booleanPreferencesKey("copy_opens_novelai")
        val KEY_MANIFEST_URL = stringPreferencesKey("pack_manifest_url")
        val KEY_ONLY_PREVIEW = booleanPreferencesKey("only_with_preview")
        val KEY_ONLINE_SEARCH = booleanPreferencesKey("online_tag_search")
        val KEY_ONLINE_PREVIEWS = booleanPreferencesKey("online_artist_previews")
        val KEY_ALLOW_NSFW = booleanPreferencesKey("allow_nsfw_tags")
    }
}
