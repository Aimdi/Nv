package app.promptcompanion.nai.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.PrimaryKey

@Entity(tableName = "artists")
data class ArtistEntity(
    @PrimaryKey val id: Long,
    val name: String,
    @ColumnInfo(name = "display_name") val displayName: String,
    @ColumnInfo(name = "post_count") val postCount: Int,
    val source: String = SOURCE_NAI_V3,
    @ColumnInfo(name = "thumbnail_file") val thumbnailFile: String? = null,
    @ColumnInfo(name = "detail_file") val detailFile: String? = null,
    @ColumnInfo(name = "is_favorite") val isFavorite: Boolean = false,
) {
    companion object {
        const val SOURCE_NAI_V3 = "nai_v3"
        const val SOURCE_ILLUSTRIOUS = "illustrious_noobai"
    }
}

@Fts4(contentEntity = ArtistEntity::class)
@Entity(tableName = "artists_fts")
data class ArtistFts(
    val name: String,
    @ColumnInfo(name = "display_name") val displayName: String,
)

@Entity(tableName = "prompts")
data class PromptEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val body: String,
    @ColumnInfo(name = "is_favorite") val isFavorite: Boolean = false,
    val notes: String = "",
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis(),
)

@Fts4(contentEntity = PromptEntity::class)
@Entity(tableName = "prompts_fts")
data class PromptFts(
    val title: String,
    val body: String,
    val notes: String,
)

@Entity(tableName = "combos")
data class ComboEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    /** JSON array of TagEntry-like objects */
    @ColumnInfo(name = "entries_json") val entriesJson: String,
    @ColumnInfo(name = "is_favorite") val isFavorite: Boolean = false,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "app_meta")
data class AppMetaEntity(
    @PrimaryKey val key: String,
    val value: String,
)
