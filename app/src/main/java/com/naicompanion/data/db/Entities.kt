package com.naicompanion.data.db

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.PrimaryKey

@Entity(tableName = "prompts")
data class PromptEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val body: String,
    val isFavorite: Boolean = false,
    val folder: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
)

@Fts4(contentEntity = PromptEntity::class)
@Entity(tableName = "prompts_fts")
data class PromptFts(
    val title: String,
    val body: String,
)

/**
 * A saved combo. The ordered entry list is stored as JSON in [entriesJson];
 * order is meaningful in NovelAI (earlier tags win), so it must round-trip
 * exactly.
 */
@Entity(tableName = "combos")
data class ComboEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val entriesJson: String,
    val isFavorite: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
)

/**
 * Artist tag catalog row. Treated as read-only after seeding; user state
 * (favorites) lives in [ArtistFavoriteEntity]. [thumbnailPath] is reserved
 * for the phase-4 preview image pack.
 */
@Entity(tableName = "artist_tags")
data class ArtistTagEntity(
    @PrimaryKey val id: Long,
    val name: String,
    val displayName: String,
    val postCount: Int,
    val source: String,
    val aliases: String = "",
    val thumbnailPath: String? = null,
)

@Fts4(contentEntity = ArtistTagEntity::class)
@Entity(tableName = "artist_tags_fts")
data class ArtistTagFts(
    val name: String,
    val displayName: String,
    val aliases: String,
)

@Entity(tableName = "artist_favorites")
data class ArtistFavoriteEntity(
    @PrimaryKey val artistId: Long,
    val addedAt: Long,
)

/** Join result: catalog row plus its favorite flag. */
data class ArtistWithFavorite(
    @Embedded val artist: ArtistTagEntity,
    val isFavorite: Boolean,
)
