package dev.naicompanion.app.data.catalog

import androidx.room.Dao
import androidx.room.Query
import androidx.room.RawQuery
import androidx.sqlite.db.SupportSQLiteQuery
import kotlinx.coroutines.flow.Flow

@Dao
interface CatalogDao {

    @RawQuery(observedEntities = [ArtistEntity::class])
    fun searchFlow(query: SupportSQLiteQuery): Flow<List<ArtistEntity>>

    @RawQuery(observedEntities = [ArtistEntity::class])
    suspend fun search(query: SupportSQLiteQuery): List<ArtistEntity>

    @Query("SELECT * FROM artists WHERE name = :name LIMIT 1")
    suspend fun findByName(name: String): ArtistEntity?

    @Query("SELECT * FROM artists WHERE name IN (:names)")
    suspend fun findAllByName(names: List<String>): List<ArtistEntity>

    @Query("SELECT COUNT(*) FROM artists")
    suspend fun count(): Int

    @Query("SELECT DISTINCT source FROM artists ORDER BY source")
    suspend fun sources(): List<String>

    @Query("SELECT DISTINCT kind FROM artists ORDER BY kind")
    suspend fun kinds(): List<String>
}
