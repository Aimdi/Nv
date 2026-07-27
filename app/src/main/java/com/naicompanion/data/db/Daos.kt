package com.naicompanion.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PromptDao {
    @Insert
    suspend fun insert(prompt: PromptEntity): Long

    @Update
    suspend fun update(prompt: PromptEntity)

    @Query("DELETE FROM prompts WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE prompts SET isFavorite = :favorite, updatedAt = :timestamp WHERE id = :id")
    suspend fun setFavorite(id: Long, favorite: Boolean, timestamp: Long)

    @Query("SELECT * FROM prompts ORDER BY isFavorite DESC, updatedAt DESC")
    fun observeAll(): Flow<List<PromptEntity>>

    @Query("SELECT * FROM prompts WHERE isFavorite = 1 ORDER BY updatedAt DESC")
    fun observeFavorites(): Flow<List<PromptEntity>>

    @Query(
        """SELECT prompts.* FROM prompts
           JOIN prompts_fts ON prompts.rowid = prompts_fts.docid
           WHERE prompts_fts MATCH :ftsQuery
           ORDER BY prompts.isFavorite DESC, prompts.updatedAt DESC"""
    )
    fun search(ftsQuery: String): Flow<List<PromptEntity>>

    @Query(
        """SELECT prompts.* FROM prompts
           JOIN prompts_fts ON prompts.rowid = prompts_fts.docid
           WHERE prompts_fts MATCH :ftsQuery AND prompts.isFavorite = 1
           ORDER BY prompts.updatedAt DESC"""
    )
    fun searchFavorites(ftsQuery: String): Flow<List<PromptEntity>>

    @Query("SELECT COUNT(*) FROM prompts WHERE title = :title AND body = :body")
    suspend fun countByTitleAndBody(title: String, body: String): Int

    @Query("SELECT * FROM prompts WHERE id = :id")
    suspend fun getById(id: Long): PromptEntity?

    @Query("SELECT * FROM prompts")
    suspend fun getAll(): List<PromptEntity>
}

@Dao
interface ComboDao {
    @Insert
    suspend fun insert(combo: ComboEntity): Long

    @Update
    suspend fun update(combo: ComboEntity)

    @Query("DELETE FROM combos WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE combos SET isFavorite = :favorite, updatedAt = :timestamp WHERE id = :id")
    suspend fun setFavorite(id: Long, favorite: Boolean, timestamp: Long)

    @Query("SELECT * FROM combos ORDER BY isFavorite DESC, updatedAt DESC")
    fun observeAll(): Flow<List<ComboEntity>>

    @Query("SELECT * FROM combos WHERE isFavorite = 1 ORDER BY updatedAt DESC")
    fun observeFavorites(): Flow<List<ComboEntity>>

    @Query("SELECT * FROM combos WHERE title LIKE '%' || :query || '%' ORDER BY isFavorite DESC, updatedAt DESC")
    fun searchByTitle(query: String): Flow<List<ComboEntity>>

    @Query("SELECT * FROM combos WHERE title LIKE '%' || :query || '%' AND isFavorite = 1 ORDER BY updatedAt DESC")
    fun searchFavoritesByTitle(query: String): Flow<List<ComboEntity>>

    @Query("SELECT COUNT(*) FROM combos WHERE title = :title")
    suspend fun countByTitle(title: String): Int

    @Query("SELECT * FROM combos")
    suspend fun getAll(): List<ComboEntity>
}

@Dao
interface ArtistDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(artists: List<ArtistTagEntity>)

    @Query("SELECT COUNT(*) FROM artist_tags")
    suspend fun count(): Int

    @Query("SELECT * FROM artist_tags WHERE name = :name LIMIT 1")
    suspend fun findByName(name: String): ArtistTagEntity?

    @Query("SELECT name FROM artist_tags WHERE id = :id")
    suspend fun findNameById(id: Long): String?

    @Query(
        """SELECT artist_tags.*, EXISTS(SELECT 1 FROM artist_favorites af WHERE af.artistId = artist_tags.id) AS isFavorite
           FROM artist_tags
           ORDER BY CASE WHEN :sortByName = 0 THEN artist_tags.postCount END DESC,
                    artist_tags.displayName COLLATE NOCASE ASC"""
    )
    fun observeAll(sortByName: Boolean): Flow<List<ArtistWithFavorite>>

    @Query(
        """SELECT artist_tags.*, EXISTS(SELECT 1 FROM artist_favorites af WHERE af.artistId = artist_tags.id) AS isFavorite
           FROM artist_tags
           WHERE EXISTS(SELECT 1 FROM artist_favorites af2 WHERE af2.artistId = artist_tags.id)
           ORDER BY CASE WHEN :sortByName = 0 THEN artist_tags.postCount END DESC,
                    artist_tags.displayName COLLATE NOCASE ASC"""
    )
    fun observeFavorites(sortByName: Boolean): Flow<List<ArtistWithFavorite>>

    @Query(
        """SELECT artist_tags.*, EXISTS(SELECT 1 FROM artist_favorites af WHERE af.artistId = artist_tags.id) AS isFavorite
           FROM artist_tags
           JOIN artist_tags_fts ON artist_tags.rowid = artist_tags_fts.docid
           WHERE artist_tags_fts MATCH :ftsQuery
           ORDER BY CASE WHEN :sortByName = 0 THEN artist_tags.postCount END DESC,
                    artist_tags.displayName COLLATE NOCASE ASC"""
    )
    fun search(ftsQuery: String, sortByName: Boolean): Flow<List<ArtistWithFavorite>>

    @Query(
        """SELECT artist_tags.*, EXISTS(SELECT 1 FROM artist_favorites af WHERE af.artistId = artist_tags.id) AS isFavorite
           FROM artist_tags
           JOIN artist_tags_fts ON artist_tags.rowid = artist_tags_fts.docid
           WHERE artist_tags_fts MATCH :ftsQuery
             AND EXISTS(SELECT 1 FROM artist_favorites af2 WHERE af2.artistId = artist_tags.id)
           ORDER BY CASE WHEN :sortByName = 0 THEN artist_tags.postCount END DESC,
                    artist_tags.displayName COLLATE NOCASE ASC"""
    )
    fun searchFavorites(ftsQuery: String, sortByName: Boolean): Flow<List<ArtistWithFavorite>>

    @Query(
        """SELECT artist_tags.*, EXISTS(SELECT 1 FROM artist_favorites af WHERE af.artistId = artist_tags.id) AS isFavorite
           FROM artist_tags
           JOIN artist_tags_fts ON artist_tags.rowid = artist_tags_fts.docid
           WHERE artist_tags_fts MATCH :ftsQuery
           ORDER BY artist_tags.postCount DESC
           LIMIT :limit"""
    )
    suspend fun suggest(ftsQuery: String, limit: Int): List<ArtistWithFavorite>
}

@Dao
interface ArtistFavoriteDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun add(favorite: ArtistFavoriteEntity)

    @Query("DELETE FROM artist_favorites WHERE artistId = :artistId")
    suspend fun remove(artistId: Long)

    @Query("SELECT artistId FROM artist_favorites")
    suspend fun getAllIds(): List<Long>
}
