package dev.naicompanion.app.data.remote

import coil.ImageLoader
import coil.decode.DataSource
import coil.decode.ImageSource
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.fetch.SourceResult
import coil.key.Keyer
import coil.request.Options
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.Buffer

/** Coil model: load an artist preview, trying both HuggingFace image folders. */
data class HfArtistPreview(
    val name: String,
    val diskCacheKey: String = name,
)

class HfPreviewKeyer : Keyer<HfArtistPreview> {
    override fun key(data: HfArtistPreview, options: Options): String =
        "hf-artist-preview:${data.diskCacheKey}"
}

/**
 * Tries `1_10000/{name}.jpg` then `2_5000/{name}.jpg` so the browser does not need a stored path.
 * Bytes are buffered before the HTTP response is closed so Coil owns a stable source.
 */
class HfPreviewFetcher(
    private val data: HfArtistPreview,
    private val options: Options,
    private val httpClient: OkHttpClient,
) : Fetcher {

    override suspend fun fetch(): FetchResult? = withContext(Dispatchers.IO) {
        for (url in ArtistPreviewUrls.candidates(data.name)) {
            val request = Request.Builder().url(url).get().build()
            val response = httpClient.newCall(request).execute()
            try {
                if (!response.isSuccessful) continue
                val body = response.body ?: continue
                val buffer = Buffer()
                body.source().readAll(buffer)
                val mime = body.contentType()?.toString()
                return@withContext SourceResult(
                    source = ImageSource(source = buffer, context = options.context),
                    mimeType = mime,
                    dataSource = DataSource.NETWORK,
                )
            } finally {
                response.close()
            }
        }
        null
    }

    class Factory(private val httpClient: OkHttpClient) : Fetcher.Factory<HfArtistPreview> {
        override fun create(
            data: HfArtistPreview,
            options: Options,
            imageLoader: ImageLoader,
        ): Fetcher = HfPreviewFetcher(data, options, httpClient)
    }
}
