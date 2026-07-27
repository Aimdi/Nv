package dev.naicompanion.app.data.packs

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import java.util.zip.ZipInputStream

/**
 * Downloads, verifies, unpacks and removes preview-image packs.
 *
 * Packs live in app-private storage (`filesDir/packs`), which is not subject to the quota and
 * LRU-eviction rules that make a browser-based cache unsuitable for a few hundred megabytes of
 * images.
 */
class PackRepository(
    private val context: Context,
    private val httpClient: OkHttpClient,
    private val json: Json = Json { ignoreUnknownKeys = true; prettyPrint = true },
) {

    private val _installState = MutableStateFlow<PackInstallState>(PackInstallState.Idle)
    val installState: StateFlow<PackInstallState> = _installState.asStateFlow()

    private val _installed = MutableStateFlow(readIndex().packs)
    val installed: StateFlow<List<InstalledPack>> = _installed.asStateFlow()

    private val packsRoot: File get() = File(context.filesDir, PACKS_DIR).apply { mkdirs() }

    private val indexFile: File get() = File(packsRoot, INDEX_FILE)

    // region lookup

    /**
     * Resolves a catalog row's preview file to an on-disk image.
     *
     * Packs all name their files after the artist tag, so any installed pack can supply an image
     * for any row. The pack matching [source] is preferred because that is the model the row's
     * metadata describes, but a pack from another dataset is better than an empty tile.
     */
    fun resolvePreview(source: String, fileName: String?, size: PreviewSize): File? {
        if (fileName.isNullOrBlank()) return null
        val packs = _installed.value.sortedByDescending { it.source == source }
        for (pack in packs) {
            val packDir = File(packsRoot, pack.id)
            File(packDir, "${size.dirName}/$fileName").takeIf { it.exists() }?.let { return it }
            // Detail images are optional; fall back to the thumbnail so something still shows.
            if (size == PreviewSize.FULL) {
                File(packDir, "${PreviewSize.THUMB.dirName}/$fileName")
                    .takeIf { it.exists() }
                    ?.let { return it }
            }
        }
        return null
    }

    fun hasPackForSource(source: String): Boolean =
        _installed.value.any { it.source == source }

    fun totalBytesOnDisk(): Long = _installed.value.sumOf { it.bytesOnDisk }

    // endregion

    // region manifest

    suspend fun fetchManifest(url: String): Result<PackManifest> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder().url(url).build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("Manifest request failed with HTTP ${response.code}")
                }
                val body = response.body?.string().orEmpty()
                json.decodeFromString<PackManifest>(body)
            }
        }
    }

    // endregion

    // region install

    suspend fun install(descriptor: PackDescriptor): Result<InstalledPack> =
        withContext(Dispatchers.IO) {
            runCatching {
                require(descriptor.url.isNotBlank()) { "Pack has no download URL" }
                val archive = download(descriptor)
                try {
                    if (descriptor.sha256.isNotBlank()) {
                        _installState.value = PackInstallState.Verifying(descriptor.id)
                        val actual = sha256(archive)
                        if (!actual.equals(descriptor.sha256, ignoreCase = true)) {
                            throw IOException(
                                "Checksum mismatch: expected ${descriptor.sha256}, got $actual",
                            )
                        }
                    }
                    archive.inputStream().use { extract(descriptor, it, descriptor.imageCount) }
                } finally {
                    archive.delete()
                }
            }.onSuccess {
                _installState.value = PackInstallState.Done(descriptor.id)
            }.onFailure { error ->
                _installState.value = PackInstallState.Failed(
                    descriptor.id,
                    error.message ?: "Install failed",
                )
            }
        }

    /**
     * Installs from a zip the user picked with the system file picker. This is the offline path:
     * build a pack on a desktop, copy it to the phone, import it without any hosting.
     */
    suspend fun installFromUri(uri: Uri, fallback: PackDescriptor? = null): Result<InstalledPack> =
        withContext(Dispatchers.IO) {
            runCatching {
                val stream = context.contentResolver.openInputStream(uri)
                    ?: throw IOException("Could not open the selected file")
                stream.use { input ->
                    val descriptor = fallback ?: PackDescriptor(
                        id = "imported-${System.currentTimeMillis()}",
                        name = "Imported pack",
                        source = IMPORTED_SOURCE,
                    )
                    extract(descriptor, input, descriptor.imageCount)
                }
            }.onFailure { error ->
                _installState.value = PackInstallState.Failed(
                    fallback?.id ?: "import",
                    error.message ?: "Import failed",
                )
            }
        }

    private suspend fun download(descriptor: PackDescriptor): File {
        val target = File(context.cacheDir, "pack-${descriptor.id}.zip")
        val existing = if (target.exists()) target.length() else 0L

        val requestBuilder = Request.Builder().url(descriptor.url)
        if (existing > 0) {
            // Resume where a previous attempt stopped; large packs on mobile data need this.
            requestBuilder.header("Range", "bytes=$existing-")
        }

        httpClient.newCall(requestBuilder.build()).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Download failed with HTTP ${response.code}")
            }
            val resuming = response.code == 206 && existing > 0
            val body = response.body ?: throw IOException("Empty response body")
            val declaredTotal = if (descriptor.sizeBytes > 0) {
                descriptor.sizeBytes
            } else {
                body.contentLength().let { if (it > 0) it + (if (resuming) existing else 0) else 0 }
            }

            var written = if (resuming) existing else 0L
            _installState.value =
                PackInstallState.Downloading(descriptor.id, written, declaredTotal)

            body.byteStream().use { input ->
                java.io.FileOutputStream(target, resuming).use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var lastReported = 0L
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        written += read
                        if (written - lastReported >= PROGRESS_STEP_BYTES) {
                            lastReported = written
                            _installState.value = PackInstallState.Downloading(
                                descriptor.id,
                                written,
                                declaredTotal,
                            )
                        }
                    }
                }
            }
        }
        return target
    }

    private fun extract(
        descriptor: PackDescriptor,
        input: InputStream,
        expectedFiles: Int,
    ): InstalledPack {
        val destination = File(packsRoot, descriptor.id)
        val staging = File(packsRoot, "${descriptor.id}$STAGING_SUFFIX")
        staging.deleteRecursively()
        staging.mkdirs()

        var files = 0
        var bytes = 0L
        val canonicalStaging = staging.canonicalPath + File.separator

        ZipInputStream(input.buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val outFile = File(staging, entry.name)
                // Zip-slip guard: reject entries that would escape the pack directory.
                if (!outFile.canonicalPath.startsWith(canonicalStaging)) {
                    throw IOException("Pack contains an unsafe path: ${entry.name}")
                }
                if (entry.isDirectory) {
                    outFile.mkdirs()
                } else {
                    outFile.parentFile?.mkdirs()
                    outFile.outputStream().use { output ->
                        bytes += zip.copyTo(output)
                    }
                    files++
                    if (files % EXTRACT_PROGRESS_STEP == 0) {
                        _installState.value = PackInstallState.Extracting(
                            descriptor.id,
                            files,
                            maxOf(expectedFiles, files),
                        )
                    }
                }
                zip.closeEntry()
            }
        }

        val embedded = File(staging, PACK_METADATA_FILE).takeIf { it.exists() }?.let { file ->
            runCatching { json.decodeFromString<PackDescriptor>(file.readText()) }.getOrNull()
        }
        val effective = embedded ?: descriptor

        destination.deleteRecursively()
        if (!staging.renameTo(destination)) {
            staging.deleteRecursively()
            throw IOException("Could not move the unpacked files into place")
        }

        val installed = InstalledPack(
            id = effective.id.ifBlank { descriptor.id },
            name = effective.name.ifBlank { descriptor.name },
            source = effective.source.ifBlank { descriptor.source },
            version = effective.version,
            imageCount = countImages(destination),
            bytesOnDisk = bytes,
            installedAt = System.currentTimeMillis(),
            attribution = effective.attribution,
        )
        // The embedded metadata may declare a different id than the directory we just wrote, so
        // record the pack under the directory name that actually exists on disk.
        val recorded = installed.copy(id = descriptor.id)
        writeIndex(readIndex().packs.filterNot { it.id == recorded.id } + recorded)
        return recorded
    }

    fun remove(packId: String) {
        File(packsRoot, packId).deleteRecursively()
        writeIndex(readIndex().packs.filterNot { it.id == packId })
    }

    fun clearInstallState() {
        _installState.value = PackInstallState.Idle
    }

    // endregion

    // region index persistence

    private fun readIndex(): InstalledPackIndex {
        val file = File(File(context.filesDir, PACKS_DIR), INDEX_FILE)
        if (!file.exists()) return InstalledPackIndex()
        return runCatching { json.decodeFromString<InstalledPackIndex>(file.readText()) }
            .getOrDefault(InstalledPackIndex())
    }

    private fun writeIndex(packs: List<InstalledPack>) {
        val sorted = packs.sortedBy { it.name }
        indexFile.writeText(json.encodeToString(InstalledPackIndex(sorted)))
        _installed.value = sorted
    }

    private fun countImages(dir: File): Int =
        dir.walkTopDown().count { it.isFile && it.extension.lowercase() in IMAGE_EXTENSIONS }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read == -1) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    // endregion

    enum class PreviewSize(val dirName: String) {
        THUMB("thumbs"),
        FULL("full"),
    }

    companion object {
        const val PACKS_DIR = "packs"
        const val INDEX_FILE = "index.json"
        const val PACK_METADATA_FILE = "pack.json"
        const val IMPORTED_SOURCE = "imported"
        private const val STAGING_SUFFIX = ".staging"
        private const val PROGRESS_STEP_BYTES = 256L * 1024L
        private const val EXTRACT_PROGRESS_STEP = 50
        private val IMAGE_EXTENSIONS = setOf("webp", "jpg", "jpeg", "png", "avif")
    }
}
