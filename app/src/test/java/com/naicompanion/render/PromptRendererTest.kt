package com.naicompanion.render

import com.naicompanion.data.model.TagEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PromptRendererTest {

    @Test
    fun `underscores become spaces`() {
        assertEquals("mizuki hitoshi", PromptRenderer.renderTag("mizuki_hitoshi"))
    }

    @Test
    fun `plain tag renders as-is`() {
        assertEquals("1girl", PromptRenderer.renderEntry(TagEntry(tag = "1girl")))
    }

    @Test
    fun `single brace pair`() {
        assertEquals("{masterpiece}", PromptRenderer.renderEntry(TagEntry(tag = "masterpiece", bracketCount = 1)))
    }

    @Test
    fun `nested braces`() {
        assertEquals("{{masterpiece}}", PromptRenderer.renderEntry(TagEntry(tag = "masterpiece", bracketCount = 2)))
    }

    @Test
    fun `square brackets for weakening`() {
        assertEquals("[[bad anatomy]]", PromptRenderer.renderEntry(TagEntry(tag = "bad_anatomy", bracketCount = -2)))
    }

    @Test
    fun `numeric weight uses double colon form`() {
        assertEquals("1.5::smile::", PromptRenderer.renderEntry(TagEntry(tag = "smile", numericWeight = 1.5f)))
    }

    @Test
    fun `numeric weight overrides brackets`() {
        val entry = TagEntry(tag = "smile", bracketCount = 2, numericWeight = 1.5f)
        assertEquals("1.5::smile::", PromptRenderer.renderEntry(entry))
    }

    @Test
    fun `negative numeric weight for v4_5`() {
        assertEquals("-1.5::nipples::", PromptRenderer.renderEntry(TagEntry(tag = "nipples", numericWeight = -1.5f)))
    }

    @Test
    fun `weight formatting trims trailing zeros`() {
        assertEquals("1", PromptRenderer.formatWeight(1.0f))
        assertEquals("1.5", PromptRenderer.formatWeight(1.5f))
        assertEquals("0.7", PromptRenderer.formatWeight(0.70000001f))
        assertEquals("-2", PromptRenderer.formatWeight(-2.0f))
    }

    @Test
    fun `disabled entries are skipped`() {
        assertNull(PromptRenderer.renderEntry(TagEntry(tag = "1girl", enabled = false)))
    }

    @Test
    fun `combo joins enabled entries with comma space and preserves order`() {
        val entries = listOf(
            TagEntry(tag = "1girl"),
            TagEntry(tag = "artist:mizuki_hitoshi"),
            TagEntry(tag = "bad_hands", enabled = false),
            TagEntry(tag = "smile", numericWeight = 1.2f),
        )
        assertEquals(
            "1girl, artist:mizuki hitoshi, 1.2::smile::",
            PromptRenderer.renderCombo(entries),
        )
    }

    @Test
    fun `empty combo renders empty string`() {
        assertEquals("", PromptRenderer.renderCombo(emptyList()))
    }
}
