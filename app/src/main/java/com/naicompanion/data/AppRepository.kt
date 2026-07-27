package com.naicompanion.data

import android.content.Context
import androidx.room.withTransaction
import com.naicompanion.data.backup.BackupCombo
import com.naicompanion.data.backup.BackupFile
import com.naicompanion.data.backup.BackupPrompt
import com.naicompanion.data.backup.ImportResult
import com.naicompanion.data.db.AppDatabase
import com.naicompanion.data.db.ArtistFavoriteEntity
import com.naicompanion.data.db.ArtistTagEntity
import com.naicompanion.data.db.ArtistWithFavorite
import com.naicompanion.data.db.ComboEntity
import com.naicompanion.data.db.PromptEntity
import com.naicompanion.data.model.TagEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
private data class AssetArtist(
    val id: Long,
    val name: String,
    val post_count: Int,
)

class AppRepository(
    private val appContext: Context,
    val db: AppDatabase,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    private val prettyJson = Json(json) { prettyPrint = true }

    // ------------------------------------------------------------------
    // Settings
    // ------------------------------------------------------------------

    private val prefs = appContext.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _artistPrefixEnabled =
        MutableStateFlow(prefs.getBoolean(KEY_ARTIST_PREFIX, true))
    val artistPrefixEnabled: StateFlow<Boolean> = _artistPrefixEnabled

    fun setArtistPrefixEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ARTIST_PREFIX, enabled).apply()
        _artistPrefixEnabled.value = enabled
    }

    // ------------------------------------------------------------------
    // Catalog seeding
    // ------------------------------------------------------------------

    private val seedMutex = Mutex()
    private val _catalogReady = MutableStateFlow(false)
    val catalogReady: StateFlow<Boolean> = _catalogReady

    suspend fun ensureCatalogSeeded() {
        if (_catalogReady.value) return
        seedMutex.withLock {
            if (_catalogReady.value) return
            withContext(Dispatchers.IO) {
                if (db.artistDao().count() == 0) {
                    val text = appContext.assets.open(CATALOG_ASSET)
                        .bufferedReader().use { it.readText() }
                    val artists = json.decodeFromString<List<AssetArtist>>(text)
                    db.withTransaction {
                        artists.map { it.toEntity() }
                            .chunked(SEED_CHUNK_SIZE)
                            .forEach { db.artistDao().insertAll(it) }
                    }
                }
                _catalogReady.value = true
            }
        }
    }

    private fun AssetArtist.toEntity() = ArtistTagEntity(
        id = id,
        name = name,
        displayName = name.replace('_', ' '),
        postCount = post_count,
        source = SOURCE_NAI_V3,
    )

    // ------------------------------------------------------------------
    // Artist catalog
    // ------------------------------------------------------------------

    fun artists(
        query: String,
        favoritesOnly: Boolean,
        sortByName: Boolean,
    ): Flow<List<ArtistWithFavorite>> {
        val fts = buildFtsQuery(query)
        return when {
            fts == null && favoritesOnly -> db.artistDao().observeFavorites(sortByName)
            fts == null -> db.artistDao().observeAll(sortByName)
            favoritesOnly -> db.artistDao().searchFavorites(fts, sortByName)
            else -> db.artistDao().search(fts, sortByName)
        }
    }

    suspend fun suggestions(query: String, limit: Int = 8): List<ArtistWithFavorite> {
        val fts = buildFtsQuery(query) ?: return emptyList()
        return db.artistDao().suggest(fts, limit)
    }

    suspend fun toggleArtistFavorite(artistId: Long, currentlyFavorite: Boolean) {
        if (currentlyFavorite) {
            db.artistFavoriteDao().remove(artistId)
        } else {
            db.artistFavoriteDao().add(ArtistFavoriteEntity(artistId, System.currentTimeMillis()))
        }
    }

    // ------------------------------------------------------------------
    // Prompts
    // ------------------------------------------------------------------

    fun prompts(query: String, favoritesOnly: Boolean): Flow<List<PromptEntity>> {
        val fts = buildFtsQuery(query)
        return when {
            fts == null && favoritesOnly -> db.promptDao().observeFavorites()
            fts == null -> db.promptDao().observeAll()
            favoritesOnly -> db.promptDao().searchFavorites(fts)
            else -> db.promptDao().search(fts)
        }
    }

    suspend fun savePrompt(
        id: Long?,
        title: String,
        body: String,
        isFavorite: Boolean = false,
        folder: String? = null,
    ): Long {
        val now = System.currentTimeMillis()
        return if (id == null) {
            db.promptDao().insert(
                PromptEntity(
                    title = title.trim(),
                    body = body.trim(),
                    isFavorite = isFavorite,
                    folder = folder,
                    createdAt = now,
                    updatedAt = now,
                )
            )
        } else {
            db.promptDao().getById(id)?.let { existing ->
                db.promptDao().update(
                    existing.copy(
                        title = title.trim(),
                        body = body.trim(),
                        folder = folder,
                        updatedAt = now,
                    )
                )
            }
            id
        }
    }

    suspend fun deletePrompt(id: Long) = db.promptDao().delete(id)

    suspend fun togglePromptFavorite(id: Long, currentlyFavorite: Boolean) =
        db.promptDao().setFavorite(id, !currentlyFavorite, System.currentTimeMillis())

    // ------------------------------------------------------------------
    // Combos
    // ------------------------------------------------------------------

    fun combos(query: String, favoritesOnly: Boolean): Flow<List<ComboEntity>> =
        when {
            query.isBlank() && favoritesOnly -> db.comboDao().observeFavorites()
            query.isBlank() -> db.comboDao().observeAll()
            favoritesOnly -> db.comboDao().searchFavoritesByTitle(query.trim())
            else -> db.comboDao().searchByTitle(query.trim())
        }

    fun encodeEntries(entries: List<TagEntry>): String = json.encodeToString(entries)

    fun decodeEntries(entriesJson: String): List<TagEntry> =
        runCatching { json.decodeFromString<List<TagEntry>>(entriesJson) }.getOrDefault(emptyList())

    suspend fun saveCombo(title: String, entries: List<TagEntry>): Long {
        val now = System.currentTimeMillis()
        return db.comboDao().insert(
            ComboEntity(
                title = title.trim(),
                entriesJson = encodeEntries(entries),
                createdAt = now,
                updatedAt = now,
            )
        )
    }

    suspend fun deleteCombo(id: Long) = db.comboDao().delete(id)

    suspend fun toggleComboFavorite(id: Long, currentlyFavorite: Boolean) =
        db.comboDao().setFavorite(id, !currentlyFavorite, System.currentTimeMillis())

    // ------------------------------------------------------------------
    // Backup / restore
    // ------------------------------------------------------------------

    suspend fun exportBackup(): String {
        val now = System.currentTimeMillis()
        val prompts = db.promptDao().getAll().map {
            BackupPrompt(it.title, it.body, it.isFavorite, it.folder, it.createdAt, it.updatedAt)
        }
        val combos = db.comboDao().getAll().map {
            BackupCombo(it.title, decodeEntries(it.entriesJson), it.isFavorite, it.createdAt, it.updatedAt)
        }
        // resolve ids back to tag names so backups survive catalog rebuilds
        val favoriteNames = db.artistFavoriteDao().getAllIds()
            .mapNotNull { db.artistDao().findNameById(it) }
        return prettyJson.encodeToString(
            BackupFile(
                exportedAt = now,
                prompts = prompts,
                combos = combos,
                favoriteArtists = favoriteNames,
            )
        )
    }

    suspend fun importBackup(text: String): ImportResult {
        val backup = json.decodeFromString<BackupFile>(text)
        require(backup.schemaVersion <= BackupFile.CURRENT_SCHEMA_VERSION) {
            "Backup schema v${backup.schemaVersion} is newer than this app supports"
        }

        var promptsImported = 0
        var promptsSkipped = 0
        backup.prompts.forEach { p ->
            if (db.promptDao().countByTitleAndBody(p.title, p.body) > 0) {
                promptsSkipped++
            } else {
                db.promptDao().insert(
                    PromptEntity(
                        title = p.title,
                        body = p.body,
                        isFavorite = p.isFavorite,
                        folder = p.folder,
                        createdAt = p.createdAt,
                        updatedAt = p.updatedAt,
                    )
                )
                promptsImported++
            }
        }

        var combosImported = 0
        var combosSkipped = 0
        backup.combos.forEach { c ->
            if (db.comboDao().countByTitle(c.title) > 0) {
                combosSkipped++
            } else {
                db.comboDao().insert(
                    ComboEntity(
                        title = c.title,
                        entriesJson = encodeEntries(c.entries),
                        isFavorite = c.isFavorite,
                        createdAt = c.createdAt,
                        updatedAt = c.updatedAt,
                    )
                )
                combosImported++
            }
        }

        var favoritesImported = 0
        backup.favoriteArtists.forEach { name ->
            db.artistDao().findByName(name)?.let { artist ->
                db.artistFavoriteDao().add(
                    ArtistFavoriteEntity(artist.id, System.currentTimeMillis())
                )
                favoritesImported++
            }
        }

        return ImportResult(
            promptsImported = promptsImported,
            promptsSkipped = promptsSkipped,
            combosImported = combosImported,
            combosSkipped = combosSkipped,
            favoritesImported = favoritesImported,
        )
    }

    companion object {
        private const val KEY_ARTIST_PREFIX = "artist_prefix"
        private const val CATALOG_ASSET = "artists.json"
        private const val SEED_CHUNK_SIZE = 1_000
        const val SOURCE_NAI_V3 = "nai-v3"

        /**
         * Builds an FTS4 MATCH expression with prefix matching from free-form
         * user input. Returns null when nothing searchable remains, which the
         * callers treat as "no query".
         */
        fun buildFtsQuery(input: String): String? {
            val tokens = input.trim()
                .split(Regex("\\s+"))
                .map { token -> token.filter { it.isLetterOrDigit() } }
                .filter { it.isNotEmpty() }
            if (tokens.isEmpty()) return null
            return tokens.joinToString(" ") { "$it*" }
        }
    }
}
