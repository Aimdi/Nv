package app.promptcompanion.nai.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class NovelAiSyntaxTest {

    @Test
    fun normalizeTag_convertsUnderscoresToSpaces() {
        assertThat(NovelAiSyntax.normalizeTag("ainiwaffles")).isEqualTo("ainiwaffles")
        assertThat(NovelAiSyntax.normalizeTag("some_artist_name"))
            .isEqualTo("some artist name")
        assertThat(NovelAiSyntax.normalizeTag("artist:ainiwaffles"))
            .isEqualTo("artist:ainiwaffles")
        assertThat(NovelAiSyntax.normalizeTag("artist:some_artist"))
            .isEqualTo("artist:some artist")
        assertThat(NovelAiSyntax.normalizeTag("foo_bar", forceArtistPrefix = true))
            .isEqualTo("artist:foo bar")
    }

    @Test
    fun renderTag_braceAndBracketNesting() {
        assertThat(NovelAiSyntax.renderTag("cat", bracketCount = 2))
            .isEqualTo("{{cat}}")
        assertThat(NovelAiSyntax.renderTag("cat", bracketCount = -2))
            .isEqualTo("[[cat]]")
        assertThat(NovelAiSyntax.renderTag("cat", bracketCount = 0))
            .isEqualTo("cat")
    }

    @Test
    fun renderTag_numericWeightTakesPrecedence() {
        assertThat(NovelAiSyntax.renderTag("cat", bracketCount = 3, numericWeight = 1.5))
            .isEqualTo("1.5::cat::")
        assertThat(NovelAiSyntax.renderTag("nipples", numericWeight = -1.5))
            .isEqualTo("-1.5::nipples::")
        assertThat(NovelAiSyntax.renderTag("soft", numericWeight = 0.8))
            .isEqualTo("0.8::soft::")
    }

    @Test
    fun renderTag_disabledReturnsNull() {
        assertThat(NovelAiSyntax.renderTag("cat", enabled = false)).isNull()
    }

    @Test
    fun renderPrompt_joinsEnabledEntries() {
        val entries = listOf(
            TagEntry(tag = "1girl"),
            TagEntry(tag = "artist:ainiwaffles", bracketCount = 1),
            TagEntry(tag = "noise", enabled = false),
            TagEntry(tag = "looking at viewer", numericWeight = 1.2),
        )
        assertThat(NovelAiSyntax.renderPrompt(entries))
            .isEqualTo("1girl, {artist:ainiwaffles}, 1.2::looking at viewer::")
    }

    @Test
    fun parsePrompt_roundTripsCommonForms() {
        val original = "1girl, {{best quality}}, [[sketch]], 1.3::dramatic lighting::"
        val parsed = NovelAiSyntax.parsePrompt(original)
        assertThat(parsed).hasSize(4)
        assertThat(parsed[0].tag).isEqualTo("1girl")
        assertThat(parsed[1].bracketCount).isEqualTo(2)
        assertThat(parsed[1].tag).isEqualTo("best quality")
        assertThat(parsed[2].bracketCount).isEqualTo(-2)
        assertThat(parsed[3].numericWeight).isEqualTo(1.3)
        assertThat(NovelAiSyntax.renderPrompt(parsed)).isEqualTo(original)
    }

    @Test
    fun formatWeight_trimsTrailingZeros() {
        assertThat(NovelAiSyntax.formatWeight(1.0)).isEqualTo("1")
        assertThat(NovelAiSyntax.formatWeight(1.5)).isEqualTo("1.5")
        assertThat(NovelAiSyntax.formatWeight(1.25)).isEqualTo("1.25")
    }
}
