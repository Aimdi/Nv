package com.nai.promptcompanion.data.repo

import com.nai.promptcompanion.data.backup.BackupFile
import com.nai.promptcompanion.data.backup.buildBackup
import com.nai.promptcompanion.data.catalog.ArtistTagEntity
import com.nai.promptcompanion.data.catalog.CatalogDatabase
import com.nai.promptcompanion.data.user.ComboEntity
import com.nai.promptcompanion.data.user.FavoriteTagEntity
import com.nai.promptcompanion.data.user.PromptEntity
import com.nai.promptcompanion.data.user.UserDatabase
import com.nai.promptcompanion.novelai.TagEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class PromptRepository(private val db: UserDatabase) {
    private val dao = db.promptDao()

    fun observe(query: String): Flow<List<PromptEntity>> {
        val fts = ftsQuery(query)
        return if (fts == null) dao.observeAll() else dao.search(fts)
    }

    suspend fun save(prompt: PromptEntity): Long = dao.upsert(prompt)
    suspend fun delete(prompt: PromptEntity) = dao.deleteWithFts(prompt)
    suspend fun toggleFavorite(prompt: PromptEntity) = dao.upsert(prompt.copy(favorite = !prompt.favorite))
    suspend fun all(): List<PromptEntity> = dao.observeAll().first()
}

class ComboRepository(private val db: UserDatabase) {
    private val dao = db.comboDao()
    private val json = Json { ignoreUnknownKeys = true }

    fun observeAll(): Flow<List<ComboEntity>> = dao.observeAll()
    suspend fun save(combo: ComboEntity): Long = dao.upsert(combo)
    suspend fun delete(combo: ComboEntity) = dao.delete(combo)
    suspend fun toggleFavorite(combo: ComboEntity) = dao.upsert(combo.copy(favorite = !combo.favorite))
    suspend fun all(): List<ComboEntity> = dao.observeAll().first()

    fun decodeEntries(combo: ComboEntity): List<TagEntry> =
        runCatching {
            json.decodeFromString<List<TagEntry>>(combo.entriesJson)
        }.getOrDefault(emptyList())

    fun encodeEntries(entries: List<TagEntry>): String = json.encodeToString(entries)
}

class FavoritesRepository(private val db: UserDatabase) {
    private val dao = db.favoriteTagDao()

    fun observeAll(): Flow<List<FavoriteTagEntity>> = dao.observeAll()
    suspend fun isFavorite(tag: String): Boolean =
        dao.observeAll().first().any { it.tag == tag }

    suspend fun toggle(tag: String) {
        if (isFavorite(tag)) dao.remove(tag)
        else dao.add(FavoriteTagEntity(tag = tag, addedAt = System.currentTimeMillis()))
    }

    suspend fun addAll(tags: List<Pair<String, Long>>) {
        tags.forEach { (tag, addedAt) -> dao.add(FavoriteTagEntity(tag, addedAt)) }
    }
}

enum class CatalogSort(val label: String) {
    POST_COUNT("Post count"),
    NAME("Name"),
    UNIQUENESS("Uniqueness"),
}

class CatalogRepository(private val db: CatalogDatabase) {
    private val dao = db.artistTagDao()

    val count: Flow<Int> = dao.observeCount()

    /** Base list for a search query; filtering/sorting for display happens in the ViewModel. */
    fun observe(query: String): Flow<List<ArtistTagEntity>> {
        val fts = ftsQuery(query)
        return if (fts == null) dao.observeAll() else dao.search(fts)
    }

    suspend fun suggest(rawPrefix: String, limit: Int = 8): List<ArtistTagEntity> {
        val fts = ftsQuery(rawPrefix) ?: return emptyList()
        return dao.suggest(fts, limit)
    }

    suspend fun byTag(tag: String): ArtistTagEntity? = dao.byTag(tag)
}

suspend fun buildBackupFromRepos(
    prompts: PromptRepository,
    combos: ComboRepository,
    favorites: FavoritesRepository,
): BackupFile = buildBackup(
    prompts = prompts.all(),
    combos = combos.all(),
    favoriteTags = favorites.observeAll().first(),
)
