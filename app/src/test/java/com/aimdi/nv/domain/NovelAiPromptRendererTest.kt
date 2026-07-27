package com.aimdi.nv.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class NovelAiPromptRendererTest {

    @Test
    fun plainTag_convertsUnderscores() {
        val entry = TagEntry(id = "1", tag = "long_hair")
        assertThat(NovelAiPromptRenderer.renderEntry(entry)).isEqualTo("long hair")
    }

    @Test
    fun artistPrefix_preservedAndSpaced() {
        val entry = TagEntry(id = "1", tag = "ainiwaffles", forceArtistPrefix = true)
        assertThat(NovelAiPromptRenderer.renderEntry(entry)).isEqualTo("artist:ainiwaffles")

        val underscored = TagEntry(id = "2", tag = "some_artist", forceArtistPrefix = true)
        assertThat(NovelAiPromptRenderer.renderEntry(underscored)).isEqualTo("artist:some artist")
    }

    @Test
    fun braces_strengthen() {
        val entry = TagEntry(id = "1", tag = "cat ears", bracketCount = 2)
        assertThat(NovelAiPromptRenderer.renderEntry(entry)).isEqualTo("{{cat ears}}")
    }

    @Test
    fun brackets_weaken() {
        val entry = TagEntry(id = "1", tag = "blurry", bracketCount = -1)
        assertThat(NovelAiPromptRenderer.renderEntry(entry)).isEqualTo("[blurry]")
    }

    @Test
    fun numericWeight() {
        val entry = TagEntry(id = "1", tag = "detailed eyes", numericWeight = 1.5f)
        assertThat(NovelAiPromptRenderer.renderEntry(entry)).isEqualTo("1.5::detailed eyes::")
    }

    @Test
    fun numericWeight_withBraces() {
        val entry = TagEntry(id = "1", tag = "rim light", bracketCount = 1, numericWeight = 1.2f)
        assertThat(NovelAiPromptRenderer.renderEntry(entry)).isEqualTo("1.2::{rim light}::")
    }

    @Test
    fun disabledEntries_skipped() {
        val entries = listOf(
            TagEntry(id = "1", tag = "1girl"),
            TagEntry(id = "2", tag = "nsfw", enabled = false),
            TagEntry(id = "3", tag = "smile"),
        )
        assertThat(NovelAiPromptRenderer.render(entries)).isEqualTo("1girl, smile")
    }

    @Test
    fun existingArtistPrefix_notDoubled() {
        val entry = TagEntry(id = "1", tag = "artist:foo_bar", forceArtistPrefix = true)
        assertThat(NovelAiPromptRenderer.renderEntry(entry)).isEqualTo("artist:foo bar")
    }

    @Test
    fun effectiveMultiplier_braces() {
        val entry = TagEntry(id = "1", tag = "x", bracketCount = 2)
        val m = NovelAiPromptRenderer.effectiveMultiplier(entry)
        assertThat(m).isGreaterThan(1.1f)
        assertThat(m).isLessThan(1.11f)
    }

    @Test
    fun formatWeight_trimsZeros() {
        assertThat(NovelAiPromptRenderer.formatWeight(1.50f)).isEqualTo("1.5")
        assertThat(NovelAiPromptRenderer.formatWeight(2.0f)).isEqualTo("2")
    }
}
