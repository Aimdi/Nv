package dev.naicompanion.app.data.user

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PromptDao {

    @Query("SELECT * FROM prompts ORDER BY is_favorite DESC, updated_at DESC")
    fun observeAll(): Flow<List<PromptEntity>>

    @Query("SELECT * FROM prompts WHERE id = :id")
    fun observeById(id: Long): Flow<PromptEntity?>

    @Query("SELECT * FROM prompts WHERE id = :id")
    suspend fun findById(id: Long): PromptEntity?

    @Query("SELECT * FROM prompts ORDER BY updated_at DESC")
    suspend fun getAll(): List<PromptEntity>

    /**
     * FTS-backed search. Falls back to a `LIKE` scan alongside the `MATCH` so partial words match
     * the same way they do in the tag browser.
     */
    @Query(
        """
        SELECT * FROM prompts
        WHERE rowid IN (SELECT rowid FROM prompts_fts WHERE prompts_fts MATCH :ftsQuery)
           OR title LIKE :likeQuery
           OR body LIKE :likeQuery
        ORDER BY is_favorite DESC, updated_at DESC
        """,
    )
    fun search(ftsQuery: String, likeQuery: String): Flow<List<PromptEntity>>

    @Query("SELECT DISTINCT folder FROM prompts WHERE folder != '' ORDER BY folder")
    fun observeFolders(): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(prompt: PromptEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(prompts: List<PromptEntity>): List<Long>

    @Update
    suspend fun update(prompt: PromptEntity)

    @Query("UPDATE prompts SET is_favorite = :favorite, updated_at = :now WHERE id = :id")
    suspend fun setFavorite(id: Long, favorite: Boolean, now: Long)

    @Query("DELETE FROM prompts WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM prompts")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM prompts")
    suspend fun count(): Int
}

@Dao
interface ComboDao {

    @Transaction
    @Query("SELECT * FROM combos ORDER BY is_favorite DESC, updated_at DESC")
    fun observeAll(): Flow<List<ComboWithTags>>

    @Transaction
    @Query("SELECT * FROM combos WHERE id = :id")
    suspend fun findById(id: Long): ComboWithTags?

    @Transaction
    @Query("SELECT * FROM combos ORDER BY updated_at DESC")
    suspend fun getAll(): List<ComboWithTags>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCombo(combo: ComboEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTags(tags: List<ComboTagEntity>)

    @Query("DELETE FROM combo_tags WHERE combo_id = :comboId")
    suspend fun deleteTagsFor(comboId: Long)

    @Query("DELETE FROM combos WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM combos")
    suspend fun deleteAll()

    @Query("UPDATE combos SET is_favorite = :favorite, updated_at = :now WHERE id = :id")
    suspend fun setFavorite(id: Long, favorite: Boolean, now: Long)

    @Query("SELECT COUNT(*) FROM combos")
    suspend fun count(): Int

    /** Replaces a combo and its tag rows atomically so ordering can never end up half-written. */
    @Transaction
    suspend fun upsert(combo: ComboEntity, tags: List<ComboTagEntity>): Long {
        val comboId = insertCombo(combo)
        val effectiveId = if (combo.id == 0L) comboId else combo.id
        deleteTagsFor(effectiveId)
        insertTags(tags.mapIndexed { index, tag -> tag.copy(id = 0L, comboId = effectiveId, position = index) })
        return effectiveId
    }
}

@Dao
interface FavoriteTagDao {

    @Query("SELECT * FROM favorite_tags ORDER BY added_at DESC")
    fun observeAll(): Flow<List<FavoriteTagEntity>>

    @Query("SELECT name FROM favorite_tags")
    fun observeNames(): Flow<List<String>>

    @Query("SELECT * FROM favorite_tags ORDER BY added_at DESC")
    suspend fun getAll(): List<FavoriteTagEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(favorite: FavoriteTagEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(favorites: List<FavoriteTagEntity>)

    @Query("DELETE FROM favorite_tags WHERE name = :name")
    suspend fun delete(name: String)

    @Query("DELETE FROM favorite_tags")
    suspend fun deleteAll()

    @Query("SELECT EXISTS(SELECT 1 FROM favorite_tags WHERE name = :name)")
    suspend fun isFavorite(name: String): Boolean
}
