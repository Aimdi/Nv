package dev.naicompanion.app.data.backup

import dev.naicompanion.app.core.prompt.TagEntry
import dev.naicompanion.app.core.prompt.TagKind
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Wire format for backups and for the persisted builder draft.
 *
 * Every field is optional with a default so an export written by an older or newer build still
 * imports; [BackupFile.schemaVersion] gates the changes that defaults cannot absorb.
 */
@Serializable
data class TagEntryDto(
    val tag: String,
    val kind: String = TagKind.GENERAL.name,
    @SerialName("brackets") val bracketCount: Int = 0,
    @SerialName("weight") val numericWeight: Double? = null,
    val enabled: Boolean = true,
)

@Serializable
data class ComboDto(
    val name: String,
    val tags: List<TagEntryDto> = emptyList(),
    val favorite: Boolean = false,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
)

@Serializable
data class PromptDto(
    val title: String,
    val body: String,
    val negative: String = "",
    val notes: String = "",
    val folder: String = "",
    val favorite: Boolean = false,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
)

@Serializable
data class FavoriteTagDto(
    val name: String,
    val kind: String = TagKind.ARTIST.name,
    val addedAt: Long = 0L,
)

@Serializable
data class SettingsDto(
    val model: String? = null,
    val underscoresToSpaces: Boolean? = null,
    val artistPrefix: Boolean? = null,
    val multilineSeparator: Boolean? = null,
    val browserColumns: Int? = null,
    val browserSort: String? = null,
    val copyOpensNovelAi: Boolean? = null,
    val packManifestUrl: String? = null,
    val onlyWithPreview: Boolean? = null,
)

@Serializable
data class BackupFile(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val app: String = APP_ID,
    val appVersion: String = "",
    val exportedAt: Long = 0L,
    val prompts: List<PromptDto> = emptyList(),
    val combos: List<ComboDto> = emptyList(),
    val favoriteTags: List<FavoriteTagDto> = emptyList(),
    val settings: SettingsDto? = null,
) {
    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
        const val APP_ID = "com.aimdi.nv"
        /** Older exports from the pre-rebrand package id. */
        const val LEGACY_APP_ID = "dev.naicompanion.app"
    }
}

fun TagEntry.toDto(): TagEntryDto = TagEntryDto(
    tag = tag,
    kind = kind.name,
    bracketCount = bracketCount,
    numericWeight = numericWeight,
    enabled = enabled,
)

fun TagEntryDto.toTagEntry(): TagEntry = TagEntry(
    tag = tag,
    kind = TagKind.fromStorage(kind),
    bracketCount = bracketCount,
    numericWeight = numericWeight,
    enabled = enabled,
)
