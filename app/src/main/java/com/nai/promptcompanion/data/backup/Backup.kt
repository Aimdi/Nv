package com.nai.promptcompanion.data.backup

import android.content.Context
import android.net.Uri
import com.nai.promptcompanion.data.user.ComboEntity
import com.nai.promptcompanion.data.user.FavoriteTagEntity
import com.nai.promptcompanion.data.user.PromptEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Versioned JSON backup schema for export/import via the Storage Access
 * Framework. Keep [BackupFile.version] bumped when fields change and make the
 * importer tolerant of older versions.
 */
@Serializable
data class BackupFile(
    val version: Int = CURRENT_VERSION,
    val app: String = APP_ID,
    val exportedAt: String = "",
    val prompts: List<BackupPrompt> = emptyList(),
    val combos: List<BackupCombo> = emptyList(),
    val favoriteTags: List<BackupFavoriteTag> = emptyList(),
) {
    companion object {
        const val CURRENT_VERSION = 1
        const val APP_ID = "nai-prompt-companion"
    }
}

@Serializable
data class BackupPrompt(
    val title: String,
    val body: String,
    val favorite: Boolean = false,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
)

@Serializable
data class BackupCombo(
    val name: String,
    val entriesJson: String,
    val favorite: Boolean = false,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
)

@Serializable
data class BackupFavoriteTag(
    val tag: String,
    val addedAt: Long = 0,
)

data class ImportResult(
    val prompts: Int,
    val combos: Int,
    val favoriteTags: Int,
)

private val backupJson = Json {
    ignoreUnknownKeys = true
    prettyPrint = true
}

fun buildBackup(
    prompts: List<PromptEntity>,
    combos: List<ComboEntity>,
    favoriteTags: List<FavoriteTagEntity>,
): BackupFile = BackupFile(
    exportedAt = isoNow(),
    prompts = prompts.map {
        BackupPrompt(it.title, it.body, it.favorite, it.createdAt, it.updatedAt)
    },
    combos = combos.map {
        BackupCombo(it.name, it.entriesJson, it.favorite, it.createdAt, it.updatedAt)
    },
    favoriteTags = favoriteTags.map { BackupFavoriteTag(it.tag, it.addedAt) },
)

fun encodeBackup(backup: BackupFile): String = backupJson.encodeToString(backup)

fun decodeBackup(raw: String): BackupFile = backupJson.decodeFromString(raw)

private fun isoNow(): String =
    SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }.format(Date())

/** Reads/writes backup payloads through Storage Access Framework URIs. */
class BackupIo(private val context: Context) {

    suspend fun write(uri: Uri, contents: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.openOutputStream(uri, "wt")?.bufferedWriter()?.use {
                it.write(contents)
            } != null
        }.getOrDefault(false)
    }

    suspend fun read(uri: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
        }.getOrNull()
    }
}
