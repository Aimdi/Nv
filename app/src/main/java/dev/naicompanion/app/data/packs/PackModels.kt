package dev.naicompanion.app.data.packs

import kotlinx.serialization.Serializable

/**
 * Remote index of downloadable preview packs.
 *
 * Preview images are deliberately not bundled in the APK: the full artist sets run to hundreds of
 * megabytes, which makes a sideloaded APK unwieldy and couples image updates to app updates.
 */
@Serializable
data class PackManifest(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val packs: List<PackDescriptor> = emptyList(),
) {
    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
    }
}

@Serializable
data class PackDescriptor(
    val id: String,
    val name: String,
    val description: String = "",

    /** Must match the `source` column of the catalog rows this pack provides previews for. */
    val source: String,

    val version: Int = 1,
    val url: String = "",
    val sizeBytes: Long = 0L,

    /** Lowercase hex SHA-256 of the zip. Empty disables verification (local imports only). */
    val sha256: String = "",

    val imageCount: Int = 0,
    val thumbnailPx: Int = 0,
    val license: String = "",
    val attribution: String = "",
)

/** A pack that has been unpacked into app-private storage. */
@Serializable
data class InstalledPack(
    val id: String,
    val name: String,
    val source: String,
    val version: Int,
    val imageCount: Int,
    val bytesOnDisk: Long,
    val installedAt: Long,
    val attribution: String = "",
)

@Serializable
data class InstalledPackIndex(
    val packs: List<InstalledPack> = emptyList(),
)

/** Progress for the currently running install, surfaced by the packs screen. */
sealed interface PackInstallState {
    data object Idle : PackInstallState
    data class Downloading(val packId: String, val bytesRead: Long, val totalBytes: Long) :
        PackInstallState {
        val fraction: Float
            get() = if (totalBytes <= 0) 0f else (bytesRead.toFloat() / totalBytes).coerceIn(0f, 1f)
    }

    data class Verifying(val packId: String) : PackInstallState
    data class Extracting(val packId: String, val filesDone: Int, val filesTotal: Int) :
        PackInstallState {
        val fraction: Float
            get() = if (filesTotal <= 0) 0f else (filesDone.toFloat() / filesTotal).coerceIn(0f, 1f)
    }

    data class Done(val packId: String) : PackInstallState
    data class Failed(val packId: String, val message: String) : PackInstallState
}
