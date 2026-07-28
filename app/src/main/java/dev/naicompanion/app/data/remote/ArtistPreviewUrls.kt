package dev.naicompanion.app.data.remote

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * On-demand preview URLs for the HuggingFace NovelAI v3 artist-comparison dataset.
 *
 * Previews are named `{name}.jpg` and split across `images/1_10000/` (top 10k by post count)
 * and `images/2_5000/`. [artists.json](https://huggingface.co/datasets/deus-ex-machina/novelai-anime-v3-artist-comparison)
 * has no path field, so clients try the primary folder then fall back.
 */
object ArtistPreviewUrls {

    const val DATASET =
        "deus-ex-machina/novelai-anime-v3-artist-comparison"

    private const val BASE =
        "https://huggingface.co/datasets/$DATASET/resolve/main/images"

    const val FOLDER_PRIMARY = "1_10000"
    const val FOLDER_FALLBACK = "2_5000"

    fun primary(name: String): String = url(FOLDER_PRIMARY, name)

    fun fallback(name: String): String = url(FOLDER_FALLBACK, name)

    fun candidates(name: String): List<String> = listOf(primary(name), fallback(name))

    /** Prefer the primary folder; [HfPreviewFetcher] falls back to the second folder on 404. */
    fun preferred(name: String, @Suppress("UNUSED_PARAMETER") postCount: Int = 0): String =
        primary(name)

    private fun url(folder: String, name: String): String {
        val encoded = URLEncoder.encode(name, StandardCharsets.UTF_8)
            .replace("+", "%20")
        return "$BASE/$folder/$encoded.jpg"
    }
}
