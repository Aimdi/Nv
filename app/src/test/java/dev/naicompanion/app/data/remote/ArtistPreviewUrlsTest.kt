package dev.naicompanion.app.data.remote

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ArtistPreviewUrlsTest {

    @Test
    fun `candidates try primary folder then fallback`() {
        val urls = ArtistPreviewUrls.candidates("hammer_(sunset_beach)")
        assertThat(urls).hasSize(2)
        assertThat(urls[0]).contains("/images/1_10000/")
        assertThat(urls[1]).contains("/images/2_5000/")
        assertThat(urls[0]).contains("hammer_")
        assertThat(urls[0]).endsWith(".jpg")
    }

    @Test
    fun `names are URL-encoded`() {
        val url = ArtistPreviewUrls.primary("foo bar")
        assertThat(url).contains("foo%20bar")
        assertThat(url).doesNotContain(" ")
    }
}
