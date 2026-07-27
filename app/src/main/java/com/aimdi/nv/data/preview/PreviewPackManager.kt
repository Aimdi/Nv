package com.aimdi.nv.data.preview

import android.content.Context
import com.aimdi.nv.data.db.MetaDao
import com.aimdi.nv.data.model.AppMetaEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream

/**
 * Downloads an optional WebP thumbnail pack into app-private storage.
 * Pack URL is configurable; default points at a GitHub Release asset placeholder.
 */
class PreviewPackManager(
    private val context: Context,
    private val metaDao: MetaDao,
) {
    sealed class State {
        data object NotInstalled : State()
        data object Installed : State()
        data class Downloading(val progress: Float, val message: String) : State()
        data class Failed(val message: String) : State()
    }

    private val _state = MutableStateFlow<State>(State.NotInstalled)
    val state: StateFlow<State> = _state.asStateFlow()

    val packDir: File get() = File(context.filesDir, "preview_pack")
    val thumbDir: File get() = File(packDir, "thumbs")
    val detailDir: File get() = File(packDir, "detail")

    suspend fun refresh() {
        val installed = metaDao.get(META_PACK_VERSION) != null &&
            thumbDir.exists() && (thumbDir.list()?.isNotEmpty() == true)
        _state.value = if (installed) State.Installed else State.NotInstalled
    }

    fun thumbFileFor(artistName: String): File? {
        val safe = sanitize(artistName)
        val webp = File(thumbDir, "$safe.webp")
        if (webp.exists()) return webp
        val jpg = File(thumbDir, "$safe.jpg")
        if (jpg.exists()) return jpg
        return null
    }

    fun detailFileFor(artistName: String): File? {
        val safe = sanitize(artistName)
        val webp = File(detailDir, "$safe.webp")
        if (webp.exists()) return webp
        return thumbFileFor(artistName)
    }

    /**
     * Download and extract a zip pack. Expected layout:
     *   thumbs/<artist>.webp
     *   detail/<artist>.webp  (optional)
     *   manifest.json         (optional)
     */
    suspend fun downloadAndInstall(packUrl: String) = withContext(Dispatchers.IO) {
        try {
            _state.value = State.Downloading(0f, "Connecting…")
            val url = URL(packUrl)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 30_000
                readTimeout = 60_000
                instanceFollowRedirects = true
            }
            val total = conn.contentLengthLong.coerceAtLeast(1L)
            val tmpZip = File(context.cacheDir, "preview_pack_tmp.zip")
            conn.inputStream.use { input ->
                tmpZip.outputStream().use { output ->
                    val buf = ByteArray(64 * 1024)
                    var readTotal = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n <= 0) break
                        output.write(buf, 0, n)
                        readTotal += n
                        val p = (readTotal.toFloat() / total).coerceIn(0f, 0.9f)
                        _state.value = State.Downloading(p, "Downloading… ${(p * 100).toInt()}%")
                    }
                }
            }
            conn.disconnect()

            _state.value = State.Downloading(0.92f, "Extracting…")
            if (packDir.exists()) packDir.deleteRecursively()
            packDir.mkdirs()
            thumbDir.mkdirs()
            detailDir.mkdirs()

            ZipInputStream(tmpZip.inputStream().buffered()).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory) {
                        val name = entry.name.removePrefix("./")
                        val outFile = when {
                            name.startsWith("thumbs/") -> File(thumbDir, File(name).name)
                            name.startsWith("detail/") -> File(detailDir, File(name).name)
                            name == "manifest.json" -> File(packDir, "manifest.json")
                            else -> null
                        }
                        if (outFile != null) {
                            outFile.outputStream().use { zis.copyTo(it) }
                        }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
            tmpZip.delete()
            metaDao.put(AppMetaEntity(META_PACK_VERSION, "1"))
            metaDao.put(AppMetaEntity(META_PACK_URL, packUrl))
            _state.value = State.Installed
        } catch (e: Exception) {
            _state.value = State.Failed(e.message ?: "Download failed")
        }
    }

    suspend fun uninstall() = withContext(Dispatchers.IO) {
        packDir.deleteRecursively()
        metaDao.put(AppMetaEntity(META_PACK_VERSION, ""))
        _state.value = State.NotInstalled
    }

    companion object {
        const val META_PACK_VERSION = "preview_pack_version"
        const val META_PACK_URL = "preview_pack_url"
        /** Override via settings; placeholder for GitHub Release asset. */
        const val DEFAULT_PACK_URL =
            "https://github.com/Aimdi/Nv/releases/download/preview-pack-v1/preview_thumbs.zip"

        fun sanitize(name: String): String =
            name.lowercase()
                .replace(Regex("[^a-z0-9._-]+"), "_")
                .trim('_')
    }
}
