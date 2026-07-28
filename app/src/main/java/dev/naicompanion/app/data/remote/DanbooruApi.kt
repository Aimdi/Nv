package dev.naicompanion.app.data.remote

import retrofit2.http.GET
import retrofit2.http.Query

/** Live Danbooru tag autocomplete. Reads only — never used for generation. */
interface DanbooruApi {

    @GET("autocomplete.json")
    suspend fun autocomplete(
        @Query("search[query]") query: String,
        @Query("search[type]") type: String = "tag_query",
        @Query("version") version: Int = 1,
        @Query("limit") limit: Int = 20,
    ): List<DanbooruAutocompleteItem>

    companion object {
        const val BASE_URL = "https://danbooru.donmai.us/"
    }
}
