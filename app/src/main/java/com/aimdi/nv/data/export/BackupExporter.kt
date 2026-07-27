package com.aimdi.nv.data.export

import com.aimdi.nv.data.model.ComboEntity
import com.aimdi.nv.data.model.PromptEntity
import com.aimdi.nv.data.repository.ComboRepository
import com.aimdi.nv.data.repository.PromptRepository
import com.google.gson.GsonBuilder
import com.google.gson.annotations.SerializedName
import java.io.InputStream
import java.io.OutputStream

/**
 * Versioned JSON backup of user data (prompts + combos).
 * Schema version 1.
 */
data class UserDataExport(
    @SerializedName("schemaVersion") val schemaVersion: Int = 1,
    @SerializedName("exportedAt") val exportedAt: Long = System.currentTimeMillis(),
    @SerializedName("prompts") val prompts: List<PromptExport> = emptyList(),
    @SerializedName("combos") val combos: List<ComboExport> = emptyList(),
)

data class PromptExport(
    val title: String,
    val body: String,
    val isFavorite: Boolean = false,
    val tagsCsv: String = "",
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
)

data class ComboExport(
    val title: String,
    val entriesJson: String,
    val isFavorite: Boolean = false,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
)

class BackupExporter(
    private val prompts: PromptRepository,
    private val combos: ComboRepository,
) {
    private val gson = GsonBuilder().setPrettyPrinting().create()

    suspend fun exportTo(stream: OutputStream) {
        val payload = UserDataExport(
            prompts = prompts.getAll().map {
                PromptExport(it.title, it.body, it.isFavorite, it.tagsCsv, it.createdAt, it.updatedAt)
            },
            combos = combos.getAll().map {
                ComboExport(it.title, it.entriesJson, it.isFavorite, it.createdAt, it.updatedAt)
            },
        )
        stream.bufferedWriter().use { it.write(gson.toJson(payload)) }
    }

    /**
     * @param merge when true, appends imported items; when false, replaces all user data.
     */
    suspend fun importFrom(stream: InputStream, merge: Boolean = true): ImportResult {
        val json = stream.bufferedReader().use { it.readText() }
        val payload = gson.fromJson(json, UserDataExport::class.java)
            ?: return ImportResult(0, 0, "Invalid JSON")

        if (payload.schemaVersion > 1) {
            return ImportResult(0, 0, "Unsupported schema version ${payload.schemaVersion}")
        }

        val promptEntities = payload.prompts.map {
            PromptEntity(
                title = it.title,
                body = it.body,
                isFavorite = it.isFavorite,
                tagsCsv = it.tagsCsv,
                createdAt = it.createdAt.takeIf { t -> t > 0 } ?: System.currentTimeMillis(),
                updatedAt = it.updatedAt.takeIf { t -> t > 0 } ?: System.currentTimeMillis(),
            )
        }
        val comboEntities = payload.combos.map {
            ComboEntity(
                title = it.title,
                entriesJson = it.entriesJson,
                isFavorite = it.isFavorite,
                createdAt = it.createdAt.takeIf { t -> t > 0 } ?: System.currentTimeMillis(),
                updatedAt = it.updatedAt.takeIf { t -> t > 0 } ?: System.currentTimeMillis(),
            )
        }

        if (!merge) {
            prompts.replaceAll(promptEntities)
            combos.replaceAll(comboEntities)
        } else {
            promptEntities.forEach { prompts.save(it.title, it.body, it.isFavorite) }
            comboEntities.forEach {
                combos.save(it.title, combos.parseEntries(it))
            }
        }

        return ImportResult(promptEntities.size, comboEntities.size, null)
    }
}

data class ImportResult(
    val promptsImported: Int,
    val combosImported: Int,
    val error: String?,
) {
    val ok get() = error == null
}
