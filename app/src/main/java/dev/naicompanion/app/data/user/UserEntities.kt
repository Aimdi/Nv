package dev.naicompanion.app.data.user

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Fts4
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

/** A saved prompt: free text plus the light metadata the library screen needs. */
@Entity(
    tableName = "prompts",
    indices = [Index("updated_at"), Index("is_favorite"), Index("folder")],
)
data class PromptEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0L,

    @ColumnInfo(name = "title")
    val title: String,

    @ColumnInfo(name = "body")
    val body: String,

    /** Optional negative prompt ("undesired content" in NovelAI's UI). */
    @ColumnInfo(name = "negative", defaultValue = "")
    val negative: String = "",

    @ColumnInfo(name = "notes", defaultValue = "")
    val notes: String = "",

    /** Flat grouping label; a full folder tree is overkill on a phone. */
    @ColumnInfo(name = "folder", defaultValue = "")
    val folder: String = "",

    @ColumnInfo(name = "is_favorite", defaultValue = "0")
    val isFavorite: Boolean = false,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
)

@Fts4(contentEntity = PromptEntity::class)
@Entity(tableName = "prompts_fts")
data class PromptFtsEntity(
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "body") val body: String,
    @ColumnInfo(name = "notes") val notes: String,
    @ColumnInfo(name = "folder") val folder: String,
)

/** A reusable, ordered tag set that can be loaded back into the builder. */
@Entity(tableName = "combos", indices = [Index("updated_at"), Index("is_favorite")])
data class ComboEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0L,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "is_favorite", defaultValue = "0")
    val isFavorite: Boolean = false,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
)

@Entity(
    tableName = "combo_tags",
    foreignKeys = [
        ForeignKey(
            entity = ComboEntity::class,
            parentColumns = ["id"],
            childColumns = ["combo_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("combo_id")],
)
data class ComboTagEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0L,

    @ColumnInfo(name = "combo_id")
    val comboId: Long,

    /** Explicit ordering column: NovelAI weights earlier tags more heavily. */
    @ColumnInfo(name = "position")
    val position: Int,

    @ColumnInfo(name = "tag")
    val tag: String,

    @ColumnInfo(name = "kind")
    val kind: String,

    @ColumnInfo(name = "bracket_count", defaultValue = "0")
    val bracketCount: Int = 0,

    @ColumnInfo(name = "numeric_weight")
    val numericWeight: Double? = null,

    @ColumnInfo(name = "enabled", defaultValue = "1")
    val enabled: Boolean = true,
)

data class ComboWithTags(
    @Embedded val combo: ComboEntity,
    @Relation(parentColumn = "id", entityColumn = "combo_id")
    val tags: List<ComboTagEntity>,
)

/** A starred catalog tag. Stored by name because the catalog lives in a separate database. */
@Entity(tableName = "favorite_tags")
data class FavoriteTagEntity(
    @PrimaryKey
    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "kind")
    val kind: String,

    @ColumnInfo(name = "added_at")
    val addedAt: Long,
)
