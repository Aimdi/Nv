package app.promptcompanion.nai.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import app.promptcompanion.nai.data.db.entity.AppMetaEntity
import app.promptcompanion.nai.data.db.entity.ArtistEntity
import app.promptcompanion.nai.data.db.entity.ComboEntity
import app.promptcompanion.nai.data.db.entity.PromptEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ArtistDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(artists: List<ArtistEntity>)

    @Query("SELECT COUNT(*) FROM artists")
    suspend fun count(): Int

    @Query(
        """
        SELECT * FROM artists
        ORDER BY post_count DESC
        LIMIT :limit OFFSET :offset
        """
    )
    suspend fun pageByPostCount(limit: Int, offset: Int): List<ArtistEntity>

    @Query(
        """
        SELECT * FROM artists
        WHERE rowid IN (
          SELECT rowid FROM artists_fts WHERE artists_fts MATCH :query
        )
        ORDER BY post_count DESC
        LIMIT :limit
        """
    )
    suspend fun search(query: String, limit: Int = 200): List<ArtistEntity>

    @Query("SELECT * FROM artists WHERE is_favorite = 1 ORDER BY display_name ASC")
    fun observeFavorites(): Flow<List<ArtistEntity>>

    @Query("UPDATE artists SET is_favorite = :favorite WHERE id = :id")
    suspend fun setFavorite(id: Long, favorite: Boolean)

    @Query("SELECT * FROM artists WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): ArtistEntity?

    @Query(
        """
        SELECT * FROM artists
        WHERE (:source IS NULL OR source = :source)
          AND (:favoritesOnly = 0 OR is_favorite = 1)
        ORDER BY
          CASE WHEN :sort = 'name' THEN display_name END ASC,
          CASE WHEN :sort = 'posts' THEN post_count END DESC
        LIMIT :limit OFFSET :offset
        """
    )
    suspend fun filtered(
        source: String?,
        favoritesOnly: Boolean,
        sort: String,
        limit: Int,
        offset: Int,
    ): List<ArtistEntity>

    @Query("DELETE FROM artists")
    suspend fun clear()
}

@Dao
interface PromptDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(prompt: PromptEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(prompts: List<PromptEntity>)

    @Update
    suspend fun update(prompt: PromptEntity)

    @Query("DELETE FROM prompts WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM prompts ORDER BY updated_at DESC")
    fun observeAll(): Flow<List<PromptEntity>>

    @Query(
        """
        SELECT * FROM prompts
        WHERE rowid IN (
          SELECT rowid FROM prompts_fts WHERE prompts_fts MATCH :query
        )
        ORDER BY updated_at DESC
        LIMIT :limit
        """
    )
    suspend fun search(query: String, limit: Int = 100): List<PromptEntity>

    @Query("UPDATE prompts SET is_favorite = :favorite, updated_at = :updatedAt WHERE id = :id")
    suspend fun setFavorite(id: Long, favorite: Boolean, updatedAt: Long = System.currentTimeMillis())

    @Query("SELECT * FROM prompts")
    suspend fun getAll(): List<PromptEntity>

    @Query("DELETE FROM prompts")
    suspend fun clear()
}

@Dao
interface ComboDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(combo: ComboEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(combos: List<ComboEntity>)

    @Query("DELETE FROM combos WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM combos ORDER BY updated_at DESC")
    fun observeAll(): Flow<List<ComboEntity>>

    @Query("SELECT * FROM combos")
    suspend fun getAll(): List<ComboEntity>

    @Query("DELETE FROM combos")
    suspend fun clear()

    @Query("UPDATE combos SET is_favorite = :favorite, updated_at = :updatedAt WHERE id = :id")
    suspend fun setFavorite(id: Long, favorite: Boolean, updatedAt: Long = System.currentTimeMillis())
}

@Dao
interface MetaDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(meta: AppMetaEntity)

    @Query("SELECT value FROM app_meta WHERE `key` = :key LIMIT 1")
    suspend fun get(key: String): String?

    @Query("DELETE FROM app_meta WHERE `key` = :key")
    suspend fun delete(key: String)
}
