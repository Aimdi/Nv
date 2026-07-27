package app.promptcompanion.nai.data.repository

import android.content.Context
import android.net.Uri
import app.promptcompanion.nai.data.db.AppDatabase
import app.promptcompanion.nai.data.db.ArtistSeeder
import app.promptcompanion.nai.data.db.entity.ArtistEntity
import app.promptcompanion.nai.data.db.entity.ComboEntity
import app.promptcompanion.nai.data.db.entity.PromptEntity
import app.promptcompanion.nai.data.export.BackupExporter
import app.promptcompanion.nai.data.export.TagEntryJson
import app.promptcompanion.nai.domain.TagEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream

class PromptRepository(private val db: AppDatabase) {
    fun observePrompts(): Flow<List<PromptEntity>> = db.promptDao().observeAll()
    fun observeCombos(): Flow<List<ComboEntity>> = db.comboDao().observeAll()

    suspend fun searchPrompts(query: String): List<PromptEntity> = withContext(Dispatchers.IO) {
        val q = query.trim()
        if (q.isEmpty()) db.promptDao().getAll()
        else db.promptDao().search(sanitizeFts(q))
    }

    suspend fun savePrompt(title: String, body: String, notes: String = "", id: Long = 0): Long =
        withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            db.promptDao().upsert(
                PromptEntity(
                    id = id,
                    title = title.ifBlank { body.take(48).ifBlank { "Untitled" } },
                    body = body,
                    notes = notes,
                    createdAt = if (id == 0L) now else now,
                    updatedAt = now,
                ),
            )
        }

    suspend fun deletePrompt(id: Long) = withContext(Dispatchers.IO) {
        db.promptDao().delete(id)
    }

    suspend fun setPromptFavorite(id: Long, favorite: Boolean) = withContext(Dispatchers.IO) {
        db.promptDao().setFavorite(id, favorite)
    }

    suspend fun saveCombo(title: String, entries: List<TagEntry>, id: Long = 0): Long =
        withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            db.comboDao().upsert(
                ComboEntity(
                    id = id,
                    title = title.ifBlank { "Combo ${entries.size} tags" },
                    entriesJson = TagEntryJson.encode(entries),
                    createdAt = now,
                    updatedAt = now,
                ),
            )
        }

    suspend fun deleteCombo(id: Long) = withContext(Dispatchers.IO) {
        db.comboDao().delete(id)
    }

    suspend fun setComboFavorite(id: Long, favorite: Boolean) = withContext(Dispatchers.IO) {
        db.comboDao().setFavorite(id, favorite)
    }

    suspend fun exportJson(): String = withContext(Dispatchers.IO) {
        val prompts = db.promptDao().getAll()
        val combos = db.comboDao().getAll()
        // favorite artists collected on demand
        BackupExporter.export(prompts, combos, emptyList())
    }

    suspend fun importJson(json: String, replace: Boolean = false) = withContext(Dispatchers.IO) {
        val payload = BackupExporter.import(json)
        if (replace) {
            db.promptDao().clear()
            db.comboDao().clear()
        }
        if (payload.prompts.isNotEmpty()) db.promptDao().insertAll(payload.prompts)
        if (payload.combos.isNotEmpty()) db.comboDao().insertAll(payload.combos)
        payload.favoriteArtistIds.forEach { id ->
            db.artistDao().setFavorite(id, true)
        }
    }

    suspend fun writeExportToUri(context: Context, uri: Uri) = withContext(Dispatchers.IO) {
        val json = exportJson()
        context.contentResolver.openOutputStream(uri)?.use { out ->
            out.write(json.toByteArray(Charsets.UTF_8))
        } ?: error("Unable to open export destination")
    }

    suspend fun readImportFromUri(context: Context, uri: Uri) = withContext(Dispatchers.IO) {
        val json = context.contentResolver.openInputStream(uri)?.bufferedReader().use { it?.readText() }
            ?: error("Unable to read import file")
        importJson(json)
    }
}

class ArtistRepository(private val db: AppDatabase) {
    suspend fun ensureSeeded(context: Context) = withContext(Dispatchers.IO) {
        ArtistSeeder.seedIfNeeded(context, db)
    }

    suspend fun search(query: String, limit: Int = 200): List<ArtistEntity> =
        withContext(Dispatchers.IO) {
            val q = query.trim()
            if (q.isEmpty()) {
                db.artistDao().pageByPostCount(limit, 0)
            } else {
                db.artistDao().search(sanitizeFts(q), limit)
            }
        }

    suspend fun filtered(
        source: String?,
        favoritesOnly: Boolean,
        sort: String,
        limit: Int = 100,
        offset: Int = 0,
    ): List<ArtistEntity> = withContext(Dispatchers.IO) {
        db.artistDao().filtered(source, favoritesOnly, sort, limit, offset)
    }

    fun observeFavorites(): Flow<List<ArtistEntity>> = db.artistDao().observeFavorites()

    suspend fun setFavorite(id: Long, favorite: Boolean) = withContext(Dispatchers.IO) {
        db.artistDao().setFavorite(id, favorite)
    }

    suspend fun count(): Int = withContext(Dispatchers.IO) { db.artistDao().count() }
}

/**
 * Downloads an optional WebP thumbnail ZIP into app-private storage.
 * Pack URL is configurable; default points at a GitHub Release asset placeholder.
 */
class PreviewPackManager(private val context: Context, private val db: AppDatabase) {

    data class PackStatus(
        val installed: Boolean,
        val version: String?,
        val thumbDir: File,
        val bytesOnDisk: Long,
    )

    companion object {
        /** Override via Settings; placeholder until a release pack is published. */
        const val DEFAULT_PACK_URL =
            "https://github.com/example/prompt-companion/releases/download/previews-v1/previews-300px.webp.zip"
        const val DEFAULT_PACK_VERSION = "1"
        private const val DIR_NAME = "preview_pack"
    }

    fun thumbDir(): File = File(context.filesDir, DIR_NAME).also { it.mkdirs() }

    fun resolveThumbnail(fileName: String?): File? {
        if (fileName.isNullOrBlank()) return null
        val f = File(thumbDir(), fileName)
        return if (f.exists()) f else null
    }

    suspend fun status(): PackStatus = withContext(Dispatchers.IO) {
        val dir = thumbDir()
        val version = db.metaDao().get(AppDatabase.META_PREVIEW_PACK_VERSION)
        val bytes = dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        PackStatus(
            installed = version != null && bytes > 0,
            version = version,
            thumbDir = dir,
            bytesOnDisk = bytes,
        )
    }

    suspend fun download(
        url: String = DEFAULT_PACK_URL,
        version: String = DEFAULT_PACK_VERSION,
        onProgress: (downloaded: Long, total: Long) -> Unit = { _, _ -> },
    ) = withContext(Dispatchers.IO) {
        val destDir = thumbDir()
        destDir.listFiles()?.forEach { it.deleteRecursively() }
        destDir.mkdirs()

        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 30_000
            readTimeout = 60_000
            instanceFollowRedirects = true
        }
        try {
            conn.connect()
            if (conn.responseCode !in 200..299) {
                error("Download failed: HTTP ${conn.responseCode}")
            }
            val total = conn.contentLengthLong
            val tmpZip = File(context.cacheDir, "preview_pack_download.zip")
            BufferedInputStream(conn.inputStream).use { input ->
                FileOutputStream(tmpZip).use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var read: Int
                    var downloaded = 0L
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        downloaded += read
                        onProgress(downloaded, total)
                    }
                }
            }
            unzip(tmpZip, destDir)
            tmpZip.delete()
            db.metaDao().put(
                app.promptcompanion.nai.data.db.entity.AppMetaEntity(
                    AppDatabase.META_PREVIEW_PACK_VERSION,
                    version,
                ),
            )
        } finally {
            conn.disconnect()
        }
    }

    suspend fun remove() = withContext(Dispatchers.IO) {
        thumbDir().listFiles()?.forEach { it.deleteRecursively() }
        db.metaDao().delete(AppDatabase.META_PREVIEW_PACK_VERSION)
    }

    private fun unzip(zipFile: File, destDir: File) {
        ZipInputStream(zipFile.inputStream().buffered()).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    val name = File(entry.name).name
                    if (name.endsWith(".webp", ignoreCase = true) ||
                        name.endsWith(".jpg", ignoreCase = true) ||
                        name.endsWith(".png", ignoreCase = true)
                    ) {
                        val outFile = File(destDir, name)
                        FileOutputStream(outFile).use { fos -> zis.copyTo(fos) }
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
    }
}

internal fun sanitizeFts(query: String): String {
    // Escape FTS special chars and use prefix match on tokens.
    val cleaned = query.replace(Regex("[\"*{}()^~:]"), " ").trim()
    if (cleaned.isEmpty()) return "\"\""
    return cleaned.split(Regex("\\s+"))
        .filter { it.isNotBlank() }
        .joinToString(" ") { token ->
            val safe = token.replace("\"", "")
            "\"$safe\"*"
        }
}
