package com.nai.promptcompanion.data.imagepack

import android.content.Context
import com.nai.promptcompanion.data.prefs.SettingsStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * Manifest describing a downloadable preview-image pack. The pack lives on any
 * static host (e.g. a GitHub Release asset bundle) and is fetched on first run
 * into app-private storage — keeping the sideloaded APK small while avoiding
 * the quota/eviction problems browser storage would have.
 *
 * Convention: `thumb/<tag>.webp` (~256–384 px, grid) and `detail/<tag>.webp`
 * (~768 px, detail view). Tags keep their Danbooru spelling (underscores).
 */
@Serializable
data class PackManifest(
    val version: Int,
    val name: String = "",
    val files: List<PackFile> = emptyList(),
)

@Serializable
data class PackFile(
    val path: String,
    val size: Long = 0,
    val sha256: String? = null,
)

sealed interface ImagePackState {
    data object NotInstalled : ImagePackState

    data class Downloading(
        val downloadedBytes: Long,
        val totalBytes: Long,
        val filesDone: Int,
        val fileCount: Int,
    ) : ImagePackState {
        val fraction: Float
            get() = if (totalBytes > 0) (downloadedBytes.toFloat() / totalBytes).coerceIn(0f, 1f) else 0f
    }

    data class Installed(val version: Int, val fileCount: Int) : ImagePackState
    data class Failed(val message: String) : ImagePackState
}

class ImagePackManager(
    private val context: Context,
    private val settings: SettingsStore,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val packDir: File
        get() = File(context.filesDir, "imagepack")

    private val _state = MutableStateFlow<ImagePackState>(ImagePackState.NotInstalled)
    val state: StateFlow<ImagePackState> = _state

    private var downloadJob: Job? = null
    @Volatile private var cancelRequested = false

    /** Reflects the persisted installation state on app start. */
    suspend fun refresh() {
        val installedVersion = settings.installedPackVersion.first()
        _state.value = if (installedVersion > 0 && packDir.isDirectory) {
            val count = packDir.walkTopDown().count { it.isFile && it.extension == "webp" }
            ImagePackState.Installed(installedVersion, count)
        } else {
            ImagePackState.NotInstalled
        }
    }

    fun thumbFile(tag: String): File? = existingFile("thumb", tag)
    fun detailFile(tag: String): File? = existingFile("detail", tag) ?: existingFile("thumb", tag)

    private fun existingFile(kind: String, tag: String): File? {
        if (_state.value !is ImagePackState.Installed) return null
        val f = File(packDir, "$kind/$tag.webp")
        return f.takeIf { it.isFile }
    }

    fun cancelDownload() {
        cancelRequested = true
        downloadJob?.cancel()
    }

    suspend fun removePack() = withContext(Dispatchers.IO) {
        cancelDownload()
        packDir.deleteRecursively()
        settings.setInstalledPackVersion(0)
        _state.value = ImagePackState.NotInstalled
    }

    /**
     * Downloads the manifest and every pack file with integrity checks.
     * Safe to call only from the UI layer's confirmation flow.
     */
    suspend fun download(manifestUrl: String): Unit = withContext(Dispatchers.IO) {
        cancelRequested = false
        try {
            val manifestRaw = httpGet(manifestUrl)
                ?: throw IllegalStateException("Could not download pack manifest")
            val manifest = json.decodeFromString<PackManifest>(manifestRaw)
            if (manifest.files.isEmpty()) throw IllegalStateException("Pack manifest is empty")

            val baseUrl = manifestUrl.substringBeforeLast('/')
            val totalBytes = manifest.files.sumOf { it.size }.takeIf { it > 0 } ?: -1L
            var downloaded = 0L
            var filesDone = 0
            _state.value = ImagePackState.Downloading(0, totalBytes, 0, manifest.files.size)

            packDir.mkdirs()
            for (file in manifest.files) {
                throwIfCancelled()
                val target = File(packDir, file.path)
                if (target.isFile && target.length() == file.size && verifySha(target, file.sha256)) {
                    downloaded += file.size
                    filesDone++
                    _state.value = ImagePackState.Downloading(downloaded, totalBytes, filesDone, manifest.files.size)
                    continue
                }
                target.parentFile?.mkdirs()
                val partial = File(target.parentFile, target.name + ".partial")
                downloadTo("$baseUrl/${file.path}", partial) { delta ->
                    downloaded += delta
                    _state.value = ImagePackState.Downloading(downloaded, totalBytes, filesDone, manifest.files.size)
                }
                if (!verifySha(partial, file.sha256)) {
                    partial.delete()
                    throw IllegalStateException("Integrity check failed for ${file.path}")
                }
                partial.renameTo(target)
                filesDone++
                _state.value = ImagePackState.Downloading(downloaded, totalBytes, filesDone, manifest.files.size)
            }

            settings.setInstalledPackVersion(manifest.version)
            _state.value = ImagePackState.Installed(manifest.version, manifest.files.size)
        } catch (e: CancellationException) {
            _state.value = ImagePackState.NotInstalled
            throw e
        } catch (e: Exception) {
            _state.value = ImagePackState.Failed(e.message ?: "Download failed")
        } finally {
            downloadJob = null
        }
    }

    private fun throwIfCancelled() {
        if (cancelRequested) throw CancellationException("Image pack download cancelled")
    }

    private fun httpGet(url: String): String? =
        runCatching {
            client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                if (!response.isSuccessful) return null
                response.body?.string()
            }
        }.getOrNull()

    private fun downloadTo(url: String, target: File, onProgress: (Long) -> Unit) {
        client.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (!response.isSuccessful) throw IllegalStateException("HTTP ${response.code} for $url")
            val body = response.body ?: throw IllegalStateException("Empty body for $url")
            body.byteStream().use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        throwIfCancelled()
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        onProgress(read.toLong())
                    }
                }
            }
        }
    }

    private fun verifySha(file: File, expected: String?): Boolean {
        if (expected.isNullOrBlank()) return true
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) } == expected.lowercase()
    }
}
