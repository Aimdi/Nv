package dev.naicompanion.app.data.backup

import android.content.Context
import android.net.Uri
import dev.naicompanion.app.core.prompt.TagKind
import dev.naicompanion.app.data.settings.AppSettings
import dev.naicompanion.app.data.user.ComboDao
import dev.naicompanion.app.data.user.ComboEntity
import dev.naicompanion.app.data.user.ComboTagEntity
import dev.naicompanion.app.data.user.FavoriteTagDao
import dev.naicompanion.app.data.user.FavoriteTagEntity
import dev.naicompanion.app.data.user.PromptDao
import dev.naicompanion.app.data.user.PromptEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.IOException

enum class ImportMode {
    /** Adds the file's contents alongside what is already there. */
    MERGE,

    /** Wipes prompts, combos and favourites first, giving an exact restore. */
    REPLACE,
}

data class ImportSummary(
    val prompts: Int,
    val combos: Int,
    val favoriteTags: Int,
    val mode: ImportMode,
) {
    val total: Int get() = prompts + combos + favoriteTags
}

data class ExportSummary(val prompts: Int, val combos: Int, val favoriteTags: Int, val bytes: Long)

/**
 * Versioned JSON export/import of everything the user created.
 *
 * Writing through the Storage Access Framework means the user chooses the destination, so backups
 * land somewhere that survives uninstalling the app.
 */
class BackupRepository(
    private val context: Context,
    private val promptDao: PromptDao,
    private val comboDao: ComboDao,
    private val favoriteTagDao: FavoriteTagDao,
    private val appVersion: String,
    private val json: Json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    },
    private val now: () -> Long = System::currentTimeMillis,
) {

    suspend fun buildBackup(settings: AppSettings?): BackupFile = withContext(Dispatchers.IO) {
        BackupFile(
            schemaVersion = BackupFile.CURRENT_SCHEMA_VERSION,
            appVersion = appVersion,
            exportedAt = now(),
            prompts = promptDao.getAll().map { it.toDto() },
            combos = comboDao.getAll().map { record ->
                ComboDto(
                    name = record.combo.name,
                    tags = record.tags.sortedBy { it.position }.map { tag ->
                        TagEntryDto(
                            tag = tag.tag,
                            kind = tag.kind,
                            bracketCount = tag.bracketCount,
                            numericWeight = tag.numericWeight,
                            enabled = tag.enabled,
                        )
                    },
                    favorite = record.combo.isFavorite,
                    createdAt = record.combo.createdAt,
                    updatedAt = record.combo.updatedAt,
                )
            },
            favoriteTags = favoriteTagDao.getAll().map {
                FavoriteTagDto(name = it.name, kind = it.kind, addedAt = it.addedAt)
            },
            settings = settings?.toDto(),
        )
    }

    fun encode(backup: BackupFile): String = json.encodeToString(backup)

    suspend fun exportTo(uri: Uri, settings: AppSettings?): Result<ExportSummary> =
        withContext(Dispatchers.IO) {
            runCatching {
                val backup = buildBackup(settings)
                val text = encode(backup)
                context.contentResolver.openOutputStream(uri, "wt")
                    ?.use { it.write(text.toByteArray()) }
                    ?: throw IOException("Could not open the selected file for writing")
                ExportSummary(
                    prompts = backup.prompts.size,
                    combos = backup.combos.size,
                    favoriteTags = backup.favoriteTags.size,
                    bytes = text.toByteArray().size.toLong(),
                )
            }
        }

    fun decode(text: String): BackupFile {
        val backup = json.decodeFromString<BackupFile>(text)
        if (backup.schemaVersion > BackupFile.CURRENT_SCHEMA_VERSION) {
            throw IllegalArgumentException(
                "This backup was written by a newer version of the app " +
                    "(schema ${backup.schemaVersion}).",
            )
        }
        return backup
    }

    suspend fun importFrom(uri: Uri, mode: ImportMode): Result<ImportSummary> =
        withContext(Dispatchers.IO) {
            runCatching {
                val text = context.contentResolver.openInputStream(uri)
                    ?.use { it.readBytes().decodeToString() }
                    ?: throw IOException("Could not open the selected file for reading")
                apply(decode(text), mode)
            }
        }

    suspend fun apply(backup: BackupFile, mode: ImportMode): ImportSummary =
        withContext(Dispatchers.IO) {
            if (mode == ImportMode.REPLACE) {
                promptDao.deleteAll()
                comboDao.deleteAll()
                favoriteTagDao.deleteAll()
            }

            val timestamp = now()

            promptDao.insertAll(
                backup.prompts.map { dto ->
                    PromptEntity(
                        title = dto.title,
                        body = dto.body,
                        negative = dto.negative,
                        notes = dto.notes,
                        folder = dto.folder,
                        isFavorite = dto.favorite,
                        createdAt = dto.createdAt.takeIf { it > 0 } ?: timestamp,
                        updatedAt = dto.updatedAt.takeIf { it > 0 } ?: timestamp,
                    )
                },
            )

            backup.combos.forEach { dto ->
                comboDao.upsert(
                    ComboEntity(
                        name = dto.name,
                        isFavorite = dto.favorite,
                        createdAt = dto.createdAt.takeIf { it > 0 } ?: timestamp,
                        updatedAt = dto.updatedAt.takeIf { it > 0 } ?: timestamp,
                    ),
                    dto.tags.mapIndexed { index, tag ->
                        ComboTagEntity(
                            comboId = 0L,
                            position = index,
                            tag = tag.tag,
                            kind = TagKind.fromStorage(tag.kind).name,
                            bracketCount = tag.bracketCount,
                            numericWeight = tag.numericWeight,
                            enabled = tag.enabled,
                        )
                    },
                )
            }

            favoriteTagDao.insertAll(
                backup.favoriteTags.map { dto ->
                    FavoriteTagEntity(
                        name = dto.name,
                        kind = TagKind.fromStorage(dto.kind).name,
                        addedAt = dto.addedAt.takeIf { it > 0 } ?: timestamp,
                    )
                },
            )

            ImportSummary(
                prompts = backup.prompts.size,
                combos = backup.combos.size,
                favoriteTags = backup.favoriteTags.size,
                mode = mode,
            )
        }

    /** Suggested file name; the picker lets the user change it. */
    fun suggestedFileName(): String {
        val stamp = java.text.SimpleDateFormat("yyyyMMdd-HHmm", java.util.Locale.US)
            .format(java.util.Date(now()))
        return "nai-companion-backup-$stamp.json"
    }
}

private fun PromptEntity.toDto(): PromptDto = PromptDto(
    title = title,
    body = body,
    negative = negative,
    notes = notes,
    folder = folder,
    favorite = isFavorite,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

private fun AppSettings.toDto(): SettingsDto = SettingsDto(
    model = model.name,
    underscoresToSpaces = underscoresToSpaces,
    artistPrefix = artistPrefix,
    multilineSeparator = multilineSeparator,
    browserColumns = browserColumns,
    browserSort = browserSort.name,
    copyOpensNovelAi = copyOpensNovelAi,
    packManifestUrl = packManifestUrl,
    onlyWithPreview = onlyWithPreview,
)
