package com.aimdi.nv.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.aimdi.nv.data.model.AppMetaEntity
import com.aimdi.nv.data.model.ArtistEntity
import com.aimdi.nv.data.model.ComboEntity
import com.aimdi.nv.data.model.PromptEntity
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
        WHERE (:favoritesOnly = 0 OR isFavorite = 1)
          AND (:source IS NULL OR source = :source)
        ORDER BY
          CASE WHEN :sort = 'name' THEN name END ASC,
          CASE WHEN :sort = 'posts' THEN postCount END DESC,
          postCount DESC
        LIMIT :limit OFFSET :offset
        """
    )
    suspend fun list(
        favoritesOnly: Boolean = false,
        source: String? = null,
        sort: String = "posts",
        limit: Int = 100,
        offset: Int = 0,
    ): List<ArtistEntity>

    @Query(
        """
        SELECT a.* FROM artists a
        JOIN artists_fts fts ON a.rowid = fts.rowid
        WHERE artists_fts MATCH :query
          AND (:favoritesOnly = 0 OR a.isFavorite = 1)
          AND (:source IS NULL OR a.source = :source)
        ORDER BY a.postCount DESC
        LIMIT :limit
        """
    )
    suspend fun searchFts(
        query: String,
        favoritesOnly: Boolean = false,
        source: String? = null,
        limit: Int = 100,
    ): List<ArtistEntity>

    @Query("SELECT * FROM artists WHERE id = :id")
    suspend fun getById(id: Long): ArtistEntity?

    @Query("UPDATE artists SET isFavorite = :favorite WHERE id = :id")
    suspend fun setFavorite(id: Long, favorite: Boolean)

    @Query("UPDATE artists SET thumbnailFile = :thumb, detailFile = :detail WHERE id = :id")
    suspend fun setPreviewFiles(id: Long, thumb: String?, detail: String?)
}

@Dao
interface PromptDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(prompt: PromptEntity): Long

    @Update
    suspend fun update(prompt: PromptEntity)

    @Query("DELETE FROM prompts WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM prompts ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<PromptEntity>>

    @Query("SELECT * FROM prompts WHERE isFavorite = 1 ORDER BY updatedAt DESC")
    fun observeFavorites(): Flow<List<PromptEntity>>

    @Query(
        """
        SELECT p.* FROM prompts p
        JOIN prompts_fts fts ON p.rowid = fts.rowid
        WHERE prompts_fts MATCH :query
        ORDER BY p.updatedAt DESC
        LIMIT :limit
        """
    )
    suspend fun searchFts(query: String, limit: Int = 50): List<PromptEntity>

    @Query("SELECT * FROM prompts WHERE id = :id")
    suspend fun getById(id: Long): PromptEntity?

    @Query("SELECT * FROM prompts")
    suspend fun getAll(): List<PromptEntity>

    @Query("DELETE FROM prompts")
    suspend fun deleteAll()
}

@Dao
interface ComboDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(combo: ComboEntity): Long

    @Query("DELETE FROM combos WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM combos ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<ComboEntity>>

    @Query("SELECT * FROM combos WHERE id = :id")
    suspend fun getById(id: Long): ComboEntity?

    @Query("SELECT * FROM combos")
    suspend fun getAll(): List<ComboEntity>

    @Query("DELETE FROM combos")
    suspend fun deleteAll()
}

@Dao
interface MetaDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(meta: AppMetaEntity)

    @Query("SELECT value FROM app_meta WHERE key = :key")
    suspend fun get(key: String): String?
}
