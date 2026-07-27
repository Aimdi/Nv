package app.promptcompanion.nai.data.db

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ArtistSeederTest {

    @Test
    fun parseArtistsJson_skipsBannedAndMapsFields() {
        val json = """
            [
              {"id": 1, "name": "banned_artist", "post_count": 99},
              {"id": 2, "name": "some_artist", "post_count": 1200},
              {"id": 3, "name": "another", "post_count": 5}
            ]
        """.trimIndent()
        val artists = ArtistSeeder.parseArtistsJson(json)
        assertThat(artists).hasSize(2)
        assertThat(artists[0].name).isEqualTo("some_artist")
        assertThat(artists[0].displayName).isEqualTo("some artist")
        assertThat(artists[0].postCount).isEqualTo(1200)
        assertThat(artists[0].thumbnailFile).isEqualTo("some_artist.webp")
        assertThat(artists[0].source).isEqualTo("nai_v3")
    }

    @Test
    fun sanitizeFileName_safe() {
        assertThat(ArtistSeeder.sanitizeFileName("A.B/C name!")).isEqualTo("a.b_c_name")
    }
}
