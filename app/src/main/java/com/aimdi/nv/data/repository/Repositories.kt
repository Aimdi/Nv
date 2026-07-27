package com.aimdi.nv.data.repository

import com.aimdi.nv.data.db.ArtistDao
import com.aimdi.nv.data.db.ComboDao
import com.aimdi.nv.data.db.PromptDao
import com.aimdi.nv.data.model.ArtistEntity
import com.aimdi.nv.data.model.ComboEntity
import com.aimdi.nv.data.model.PromptEntity
import com.aimdi.nv.domain.TagEntry
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow

class ArtistRepository(private val dao: ArtistDao) {
    suspend fun search(
        query: String,
        favoritesOnly: Boolean = false,
        source: String? = null,
        sort: String = "posts",
        limit: Int = 100,
    ): List<ArtistEntity> {
        val trimmed = query.trim()
        return if (trimmed.isEmpty()) {
            dao.list(favoritesOnly, source, sort, limit)
        } else {
            // FTS4 prefix match: append * to last token
            val ftsQuery = trimmed
                .split(Regex("\\s+"))
                .filter { it.isNotBlank() }
                .joinToString(" ") { token ->
                    val cleaned = token.replace(Regex("[^\"\\w]"), "")
                    if (cleaned.isEmpty()) token else "$cleaned*"
                }
            if (ftsQuery.isBlank()) {
                dao.list(favoritesOnly, source, sort, limit)
            } else {
                dao.searchFts(ftsQuery, favoritesOnly, source, limit)
            }
        }
    }

    suspend fun setFavorite(id: Long, favorite: Boolean) = dao.setFavorite(id, favorite)
    suspend fun get(id: Long) = dao.getById(id)
    suspend fun count() = dao.count()
}

class PromptRepository(private val dao: PromptDao) {
    fun observeAll(): Flow<List<PromptEntity>> = dao.observeAll()
    fun observeFavorites(): Flow<List<PromptEntity>> = dao.observeFavorites()

    suspend fun save(title: String, body: String, isFavorite: Boolean = false, id: Long = 0): Long {
        val now = System.currentTimeMillis()
        val existing = if (id > 0) dao.getById(id) else null
        val entity = PromptEntity(
            id = id,
            title = title.ifBlank { body.take(40).ifBlank { "Untitled" } },
            body = body,
            isFavorite = isFavorite || existing?.isFavorite == true,
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
        )
        return dao.upsert(entity)
    }

    suspend fun delete(id: Long) = dao.delete(id)
    suspend fun search(query: String): List<PromptEntity> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return dao.getAll()
        val fts = trimmed.split(Regex("\\s+")).joinToString(" ") { "$it*" }
        return dao.searchFts(fts)
    }

    suspend fun getAll() = dao.getAll()
    suspend fun replaceAll(prompts: List<PromptEntity>) {
        dao.deleteAll()
        prompts.forEach { dao.upsert(it.copy(id = 0)) }
    }

    suspend fun toggleFavorite(id: Long) {
        val p = dao.getById(id) ?: return
        dao.update(p.copy(isFavorite = !p.isFavorite, updatedAt = System.currentTimeMillis()))
    }
}

class ComboRepository(private val dao: ComboDao) {
    private val gson = Gson()
    private val type = object : TypeToken<List<TagEntry>>() {}.type

    fun observeAll(): Flow<List<ComboEntity>> = dao.observeAll()

    suspend fun save(title: String, entries: List<TagEntry>, id: Long = 0): Long {
        val now = System.currentTimeMillis()
        val existing = if (id > 0) dao.getById(id) else null
        val entity = ComboEntity(
            id = id,
            title = title.ifBlank { "Combo ${now % 10000}" },
            entriesJson = gson.toJson(entries),
            isFavorite = existing?.isFavorite == true,
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
        )
        return dao.upsert(entity)
    }

    fun parseEntries(combo: ComboEntity): List<TagEntry> {
        return try {
            gson.fromJson(combo.entriesJson, type) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun delete(id: Long) = dao.delete(id)
    suspend fun getAll() = dao.getAll()
    suspend fun get(id: Long) = dao.getById(id)

    suspend fun replaceAll(combos: List<ComboEntity>) {
        dao.deleteAll()
        combos.forEach { dao.upsert(it.copy(id = 0)) }
    }
}
