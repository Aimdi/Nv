package dev.naicompanion.app.data.remote

import kotlinx.coroutines.delay
import retrofit2.HttpException
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

/**
 * Debounced, cached Danbooru autocomplete with HTTP 429 backoff.
 *
 * Callers should still debounce UI input (~250 ms); this layer caches successful responses and
 * backs off on rate limits so rapid re-queries do not hammer the public API.
 */
class DanbooruRepository(
    private val api: DanbooruApi,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {

    private data class CacheEntry(
        val items: List<DanbooruAutocompleteItem>,
        val storedAtMs: Long,
    )

    private val cache = ConcurrentHashMap<String, CacheEntry>()
    @Volatile private var backoffUntilMs: Long = 0L

    suspend fun autocomplete(
        query: String,
        limit: Int = 20,
        allowNsfwMeta: Boolean = false,
    ): List<DanbooruAutocompleteItem> {
        val normalized = query.trim().lowercase()
        if (normalized.length < MIN_QUERY_LENGTH) return emptyList()

        val now = clock()
        if (now < backoffUntilMs) return cachedOrEmpty(normalized)

        cache[normalized]?.let { entry ->
            if (now - entry.storedAtMs < CACHE_TTL_MS) {
                return filterNsfw(entry.items, allowNsfwMeta)
            }
        }

        return try {
            val items = api.autocomplete(query = normalized, limit = limit)
            cache[normalized] = CacheEntry(items, now)
            filterNsfw(items, allowNsfwMeta)
        } catch (http: HttpException) {
            if (http.code() == 429) {
                backoffUntilMs = now + backoffMs(http)
                delay(50)
            }
            cachedOrEmpty(normalized).let { filterNsfw(it, allowNsfwMeta) }
        } catch (_: IOException) {
            cachedOrEmpty(normalized).let { filterNsfw(it, allowNsfwMeta) }
        }
    }

    private fun cachedOrEmpty(key: String): List<DanbooruAutocompleteItem> =
        cache[key]?.items.orEmpty()

    private fun backoffMs(http: HttpException): Long {
        val header = http.response()?.headers()?.get("retry-after")?.toLongOrNull()
        return ((header ?: 2L) * 1000L).coerceIn(500L, 30_000L)
    }

    /**
     * Conservative NSFW filter: drop obvious meta ratings tags unless the user opted in.
     * Danbooru autocomplete itself is not image content; this only hides rating:* suggestions.
     */
    private fun filterNsfw(
        items: List<DanbooruAutocompleteItem>,
        allowNsfwMeta: Boolean,
    ): List<DanbooruAutocompleteItem> {
        if (allowNsfwMeta) return items
        return items.filterNot { item ->
            val name = item.tagName.lowercase()
            name.startsWith("rating:") && name != "rating:general" && name != "rating:g"
        }
    }

    companion object {
        const val MIN_QUERY_LENGTH = 2
        const val CACHE_TTL_MS = 5 * 60 * 1000L
    }
}
