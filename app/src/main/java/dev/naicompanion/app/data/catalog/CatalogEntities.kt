package dev.naicompanion.app.data.catalog

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One browsable tag in the bundled catalog.
 *
 * This table lives in a read-only database that ships as an asset, so it never holds user state;
 * favourites are kept in the separate user database and joined by [name] in memory.
 */
@Entity(
    tableName = "artists",
    indices = [
        Index(value = ["name"], unique = true),
        Index(value = ["post_count"]),
        Index(value = ["source"]),
        Index(value = ["kind"]),
    ],
)
data class ArtistEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: Long,

    /** Danbooru tag exactly as it appears upstream, underscores included. */
    @ColumnInfo(name = "name")
    val name: String,

    /** Human-readable form with underscores replaced, precomputed for display and sorting. */
    @ColumnInfo(name = "display_name")
    val displayName: String,

    @ColumnInfo(name = "kind")
    val kind: String,

    @ColumnInfo(name = "post_count")
    val postCount: Int,

    /** Which dataset the row and its preview came from, e.g. `nai-v3` or `illustrious`. */
    @ColumnInfo(name = "source")
    val source: String,

    /** Preview file name inside the pack directory for this source, or null when unavailable. */
    @ColumnInfo(name = "preview")
    val preview: String?,

    /** Dataset-specific distinctiveness score; higher means a more unusual style. */
    @ColumnInfo(name = "uniqueness")
    val uniqueness: Double?,

    /** Space-separated alternative spellings folded into the search index. */
    @ColumnInfo(name = "aliases")
    val aliases: String?,
)

@Fts4(contentEntity = ArtistEntity::class)
@Entity(tableName = "artists_fts")
data class ArtistFtsEntity(
    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "display_name")
    val displayName: String,

    @ColumnInfo(name = "aliases")
    val aliases: String?,
)
