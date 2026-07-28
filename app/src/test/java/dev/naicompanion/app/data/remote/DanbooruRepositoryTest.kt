package dev.naicompanion.app.data.remote

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import okhttp3.MediaType.Companion.toMediaType

class DanbooruRepositoryTest {

    private lateinit var server: MockWebServer
    private lateinit var repository: DanbooruRepository
    private var nowMs = 1_000_000L

    @Before
    fun setUp() {
        server = MockWebServer().also { it.start() }
        val json = Json { ignoreUnknownKeys = true }
        val api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .client(OkHttpClient())
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(DanbooruApi::class.java)
        repository = DanbooruRepository(api) { nowMs }
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `autocomplete parses category and post count`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """
                [
                  {"label":"1girl","value":"1girl","category":0,"post_count":9001,"antecedent":null},
                  {"label":"artist:foo","value":"foo","category":1,"post_count":42,"antecedent":null}
                ]
                """.trimIndent(),
            ),
        )

        val items = repository.autocomplete("1g")
        assertThat(items).hasSize(2)
        assertThat(items[0].category).isEqualTo(0)
        assertThat(items[1].isArtist).isTrue()
        assertThat(server.requestCount).isEqualTo(1)
    }

    @Test
    fun `successful responses are cached`() = runTest {
        server.enqueue(MockResponse().setBody("""[{"value":"1girl","category":0,"post_count":1}]"""))
        repository.autocomplete("1g")
        repository.autocomplete("1g")
        assertThat(server.requestCount).isEqualTo(1)
    }

    @Test
    fun `rating tags are filtered unless NSFW allowed`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """
                [
                  {"value":"1girl","category":0,"post_count":1},
                  {"value":"rating:explicit","category":5,"post_count":1}
                ]
                """.trimIndent(),
            ),
        )
        val filtered = repository.autocomplete("rat", allowNsfwMeta = false)
        assertThat(filtered.map { it.value }).containsExactly("1girl")

        nowMs += DanbooruRepository.CACHE_TTL_MS + 1
        server.enqueue(
            MockResponse().setBody(
                """
                [
                  {"value":"1girl","category":0,"post_count":1},
                  {"value":"rating:explicit","category":5,"post_count":1}
                ]
                """.trimIndent(),
            ),
        )
        val allowed = repository.autocomplete("rat", allowNsfwMeta = true)
        assertThat(allowed).hasSize(2)
    }

    @Test
    fun `http 429 falls back to cache and sets backoff`() = runTest {
        server.enqueue(MockResponse().setBody("""[{"value":"blue_eyes","category":0,"post_count":9}]"""))
        assertThat(repository.autocomplete("blue")).hasSize(1)

        nowMs += DanbooruRepository.CACHE_TTL_MS + 1
        server.enqueue(MockResponse().setResponseCode(429).addHeader("Retry-After", "1"))
        val cached = repository.autocomplete("blue")
        assertThat(cached).hasSize(1)
        assertThat(cached[0].value).isEqualTo("blue_eyes")

        // While backoff is active, further calls must not hit the network.
        val before = server.requestCount
        repository.autocomplete("blue")
        assertThat(server.requestCount).isEqualTo(before)
    }
}
