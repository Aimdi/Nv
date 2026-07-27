package com.aimdi.nv.data.model

import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import com.aimdi.nv.domain.TagEntry
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

@Entity(tableName = "artists")
data class ArtistEntity(
    @PrimaryKey val id: Long,
    val name: String,
    val postCount: Int = 0,
    val source: String = "nai-v3",
    val uniqueness: Int? = null,
    val thumbnailFile: String? = null,
    val detailFile: String? = null,
    val isFavorite: Boolean = false,
)

@Fts4(contentEntity = ArtistEntity::class)
@Entity(tableName = "artists_fts")
data class ArtistFts(
    val name: String,
)

@Entity(tableName = "prompts")
data class PromptEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val body: String,
    val isFavorite: Boolean = false,
    val tagsCsv: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

@Fts4(contentEntity = PromptEntity::class)
@Entity(tableName = "prompts_fts")
data class PromptFts(
    val title: String,
    val body: String,
)

@Entity(tableName = "combos")
data class ComboEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    /** JSON-serialized list of [TagEntry]. */
    val entriesJson: String,
    val isFavorite: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "app_meta")
data class AppMetaEntity(
    @PrimaryKey val key: String,
    val value: String,
)

class Converters {
    private val gson = Gson()
    private val listType = object : TypeToken<List<TagEntry>>() {}.type

    @TypeConverter
    fun fromTagEntries(value: List<TagEntry>?): String =
        gson.toJson(value ?: emptyList<TagEntry>())

    @TypeConverter
    fun toTagEntries(value: String?): List<TagEntry> {
        if (value.isNullOrBlank()) return emptyList()
        return gson.fromJson(value, listType) ?: emptyList()
    }
}
