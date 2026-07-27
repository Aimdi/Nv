package com.nai.promptcompanion.data.catalog

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

/**
 * One artist tag from the bundled dataset. Read-only after first-run seeding.
 *
 * @param tag Danbooru-style artist tag (underscores, no `artist:` prefix).
 * @param postCount Danbooru post count — a proxy for how well-known the tag is.
 * @param uniqueness style-uniqueness rank from the source dataset, 0 when unknown.
 * @param source dataset provenance label shown in the UI ("nai-v3", "illustrious-noobai", ...).
 */
@Entity(tableName = "artist_tags")
data class ArtistTagEntity(
    @PrimaryKey val tag: String,
    val postCount: Int = 0,
    val uniqueness: Int = 0,
    val source: String = "nai-v3",
)

/**
 * Standalone FTS4 table for prefix search-as-you-type over artist tags.
 * [searchText] additionally carries the underscore→space form of the tag so
 * that multi-word artist names match from any word (FTS's simple tokenizer
 * keeps underscores inside tokens, which would otherwise only match prefixes
 * from the very start of the tag).
 */
@Fts4
@Entity(tableName = "artist_tags_fts")
data class ArtistTagFtsEntity(
    val tag: String,
    val searchText: String,
)

@Dao
interface ArtistTagDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<ArtistTagEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFtsAll(items: List<ArtistTagFtsEntity>)

    @Query("DELETE FROM artist_tags")
    suspend fun clear()

    @Query("DELETE FROM artist_tags_fts")
    suspend fun clearFts()

    @Query("SELECT COUNT(*) FROM artist_tags")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM artist_tags")
    fun observeCount(): Flow<Int>

    @Query("SELECT * FROM artist_tags WHERE tag = :tag")
    suspend fun byTag(tag: String): ArtistTagEntity?

    @Query("SELECT * FROM artist_tags ORDER BY postCount DESC")
    fun observeAll(): Flow<List<ArtistTagEntity>>

    @Query(
        """SELECT a.* FROM artist_tags a
           JOIN artist_tags_fts f ON f.tag = a.tag
           WHERE artist_tags_fts MATCH :ftsQuery
           ORDER BY a.postCount DESC"""
    )
    fun search(ftsQuery: String): Flow<List<ArtistTagEntity>>

    @Query(
        """SELECT a.* FROM artist_tags a
           JOIN artist_tags_fts f ON f.tag = a.tag
           WHERE artist_tags_fts MATCH :ftsQuery
           ORDER BY a.postCount DESC
           LIMIT :limit"""
    )
    suspend fun suggest(ftsQuery: String, limit: Int): List<ArtistTagEntity>
}

@Database(
    entities = [ArtistTagEntity::class, ArtistTagFtsEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class CatalogDatabase : RoomDatabase() {
    abstract fun artistTagDao(): ArtistTagDao
}
