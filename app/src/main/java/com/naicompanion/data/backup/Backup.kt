package com.naicompanion.data.backup

import com.naicompanion.data.model.TagEntry
import kotlinx.serialization.Serializable

/**
 * Versioned JSON export schema. Version 1 covers prompts, saved combos and
 * artist favorites. Favorite artists are exported by tag name (not database
 * id) so backups survive catalog rebuilds.
 */
@Serializable
data class BackupFile(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val app: String = APP_ID,
    val exportedAt: Long,
    val prompts: List<BackupPrompt> = emptyList(),
    val combos: List<BackupCombo> = emptyList(),
    val favoriteArtists: List<String> = emptyList(),
) {
    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
        const val APP_ID = "nai-prompt-companion"
    }
}

@Serializable
data class BackupPrompt(
    val title: String,
    val body: String,
    val isFavorite: Boolean = false,
    val folder: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
)

@Serializable
data class BackupCombo(
    val title: String,
    val entries: List<TagEntry>,
    val isFavorite: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
)

data class ImportResult(
    val promptsImported: Int,
    val promptsSkipped: Int,
    val combosImported: Int,
    val combosSkipped: Int,
    val favoritesImported: Int,
) {
    val total: Int get() = promptsImported + combosImported + favoritesImported

    fun summary(): String =
        "Imported $promptsImported prompts, $combosImported combos, $favoritesImported favorites" +
            if (promptsSkipped + combosSkipped > 0)
                " (skipped ${promptsSkipped + combosSkipped} duplicates)"
            else ""
}
