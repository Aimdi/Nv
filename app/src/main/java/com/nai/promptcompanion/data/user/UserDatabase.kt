package com.nai.promptcompanion.data.user

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "prompts")
data class PromptEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val body: String,
    val favorite: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
)

/** Standalone FTS4 table over prompt title+body, synced manually by [PromptDao]. */
@Fts4
@Entity(tableName = "prompts_fts")
data class PromptFtsEntity(
    val title: String,
    val body: String,
)

@Entity(tableName = "combos")
data class ComboEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val entriesJson: String,
    val favorite: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(tableName = "favorite_tags")
data class FavoriteTagEntity(
    @PrimaryKey val tag: String,
    val addedAt: Long,
)

@Dao
interface PromptDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(prompt: PromptEntity): Long

    @Update
    suspend fun update(prompt: PromptEntity)

    @Delete
    suspend fun delete(prompt: PromptEntity)

    @Query("SELECT * FROM prompts WHERE id = :id")
    suspend fun byId(id: Long): PromptEntity?

    @Query("SELECT * FROM prompts ORDER BY favorite DESC, updatedAt DESC")
    fun observeAll(): Flow<List<PromptEntity>>

    @Query(
        """SELECT p.* FROM prompts p
           JOIN prompts_fts f ON p.id = f.rowid
           WHERE prompts_fts MATCH :ftsQuery
           ORDER BY p.updatedAt DESC"""
    )
    fun search(ftsQuery: String): Flow<List<PromptEntity>>

    @Query("INSERT INTO prompts_fts(rowid, title, body) VALUES (:id, :title, :body)")
    suspend fun insertFts(id: Long, title: String, body: String)

    @Query("DELETE FROM prompts_fts WHERE rowid = :id")
    suspend fun deleteFts(id: Long)

    @Transaction
    suspend fun upsert(prompt: PromptEntity): Long {
        return if (prompt.id == 0L) {
            val id = insert(prompt)
            insertFts(id, prompt.title, prompt.body)
            id
        } else {
            update(prompt)
            deleteFts(prompt.id)
            insertFts(prompt.id, prompt.title, prompt.body)
            prompt.id
        }
    }

    @Transaction
    suspend fun deleteWithFts(prompt: PromptEntity) {
        delete(prompt)
        deleteFts(prompt.id)
    }
}

@Dao
interface ComboDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(combo: ComboEntity): Long

    @Delete
    suspend fun delete(combo: ComboEntity)

    @Query("SELECT * FROM combos ORDER BY favorite DESC, updatedAt DESC")
    fun observeAll(): Flow<List<ComboEntity>>

    @Query("SELECT * FROM combos WHERE id = :id")
    suspend fun byId(id: Long): ComboEntity?
}

@Dao
interface FavoriteTagDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun add(favorite: FavoriteTagEntity)

    @Query("DELETE FROM favorite_tags WHERE tag = :tag")
    suspend fun remove(tag: String)

    @Query("SELECT * FROM favorite_tags ORDER BY addedAt DESC")
    fun observeAll(): Flow<List<FavoriteTagEntity>>
}

@Database(
    entities = [
        PromptEntity::class,
        PromptFtsEntity::class,
        ComboEntity::class,
        FavoriteTagEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class UserDatabase : RoomDatabase() {
    abstract fun promptDao(): PromptDao
    abstract fun comboDao(): ComboDao
    abstract fun favoriteTagDao(): FavoriteTagDao
}
