package dev.naicompanion.app.data.repository

import dev.naicompanion.app.core.prompt.Combo
import dev.naicompanion.app.core.prompt.TagEntry
import dev.naicompanion.app.core.prompt.TagKind
import dev.naicompanion.app.data.catalog.CatalogQueryBuilder
import dev.naicompanion.app.data.user.ComboDao
import dev.naicompanion.app.data.user.ComboEntity
import dev.naicompanion.app.data.user.FavoriteTagDao
import dev.naicompanion.app.data.user.FavoriteTagEntity
import dev.naicompanion.app.data.user.PromptDao
import dev.naicompanion.app.data.user.PromptEntity
import dev.naicompanion.app.data.user.toCombo
import dev.naicompanion.app.data.user.toEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Saved prompts: the text library the user builds up over time. */
class PromptRepository(
    private val dao: PromptDao,
    private val now: () -> Long = System::currentTimeMillis,
) {

    fun observeAll(): Flow<List<PromptEntity>> = dao.observeAll()

    fun observeFolders(): Flow<List<String>> = dao.observeFolders()

    fun observeById(id: Long): Flow<PromptEntity?> = dao.observeById(id)

    /**
     * Blank queries short-circuit to the full list; otherwise the same FTS-plus-LIKE pairing the
     * tag browser uses keeps partial-word searches working.
     */
    fun search(query: String): Flow<List<PromptEntity>> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return observeAll()
        val fts = CatalogQueryBuilder.toFtsMatchQuery(trimmed) ?: return observeAll()
        return dao.search(fts, "%${CatalogQueryBuilder.escapeLike(trimmed)}%")
    }

    suspend fun save(prompt: PromptEntity): Long {
        val timestamp = now()
        return if (prompt.id == 0L) {
            dao.insert(prompt.copy(createdAt = timestamp, updatedAt = timestamp))
        } else {
            dao.update(prompt.copy(updatedAt = timestamp))
            prompt.id
        }
    }

    suspend fun create(
        title: String,
        body: String,
        negative: String = "",
        notes: String = "",
        folder: String = "",
    ): Long {
        val timestamp = now()
        return dao.insert(
            PromptEntity(
                title = title.ifBlank { defaultTitle(body) },
                body = body,
                negative = negative,
                notes = notes,
                folder = folder,
                createdAt = timestamp,
                updatedAt = timestamp,
            ),
        )
    }

    suspend fun setFavorite(id: Long, favorite: Boolean) = dao.setFavorite(id, favorite, now())

    suspend fun delete(id: Long) = dao.delete(id)

    suspend fun findById(id: Long): PromptEntity? = dao.findById(id)

    /** Derives a readable title from the first few tags when the user does not supply one. */
    private fun defaultTitle(body: String): String {
        val firstTags = body.split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .take(3)
        return if (firstTags.isEmpty()) "Untitled prompt" else firstTags.joinToString(", ")
    }
}

/** Saved combos: reusable ordered tag sets that load straight back into the builder. */
class ComboRepository(
    private val dao: ComboDao,
    private val now: () -> Long = System::currentTimeMillis,
) {

    fun observeAll(): Flow<List<Combo>> = dao.observeAll().map { list -> list.map { it.toCombo() } }

    suspend fun findById(id: Long): Combo? = dao.findById(id)?.toCombo()

    suspend fun save(name: String, entries: List<TagEntry>, id: Long = 0L): Long {
        val timestamp = now()
        val existing = if (id != 0L) dao.findById(id)?.combo else null
        val entity = ComboEntity(
            id = id,
            name = name,
            isFavorite = existing?.isFavorite ?: false,
            createdAt = existing?.createdAt ?: timestamp,
            updatedAt = timestamp,
        )
        return dao.upsert(entity, entries.mapIndexed { index, tag -> tag.toEntity(id, index) })
    }

    suspend fun setFavorite(id: Long, favorite: Boolean) = dao.setFavorite(id, favorite, now())

    suspend fun delete(id: Long) = dao.delete(id)
}

/** Starred catalog tags. Stored by tag name so the read-only catalog stays untouched. */
class FavoriteTagRepository(
    private val dao: FavoriteTagDao,
    private val now: () -> Long = System::currentTimeMillis,
) {

    fun observeAll(): Flow<List<FavoriteTagEntity>> = dao.observeAll()

    val favoriteNames: Flow<Set<String>> = dao.observeNames().map { it.toSet() }

    suspend fun toggle(name: String, kind: TagKind): Boolean {
        val isFavorite = dao.isFavorite(name)
        if (isFavorite) {
            dao.delete(name)
        } else {
            dao.insert(FavoriteTagEntity(name = name, kind = kind.name, addedAt = now()))
        }
        return !isFavorite
    }

    suspend fun add(name: String, kind: TagKind) =
        dao.insert(FavoriteTagEntity(name = name, kind = kind.name, addedAt = now()))

    suspend fun remove(name: String) = dao.delete(name)
}
