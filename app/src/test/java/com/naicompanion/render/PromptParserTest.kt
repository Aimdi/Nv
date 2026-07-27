package com.naicompanion.render

import com.naicompanion.data.model.TagEntry
import org.junit.Assert.assertEquals
import org.junit.Test

class PromptParserTest {

    @Test
    fun `parses plain comma separated tags`() {
        val entries = PromptParser.parse("1girl, solo, smile")
        assertEquals(listOf("1girl", "solo", "smile"), entries.map { it.tag })
        assertEquals(listOf(0, 0, 0), entries.map { it.bracketCount })
    }

    @Test
    fun `parses brace nesting`() {
        val entries = PromptParser.parse("{{masterpiece}}, [bad anatomy]")
        assertEquals(2, entries[0].bracketCount)
        assertEquals("masterpiece", entries[0].tag)
        assertEquals(-1, entries[1].bracketCount)
        assertEquals("bad anatomy", entries[1].tag)
    }

    @Test
    fun `parses numeric weight`() {
        val entries = PromptParser.parse("1.5::smile::")
        assertEquals(1, entries.size)
        assertEquals("smile", entries[0].tag)
        assertEquals(1.5f, entries[0].numericWeight!!, 0.0001f)
    }

    @Test
    fun `comma inside weighted section stays literal`() {
        val segments = PromptParser.splitTopLevel("1.5::very long hair, floating hair::, solo")
        assertEquals(listOf("1.5::very long hair, floating hair::", " solo"), segments)
    }

    @Test
    fun `double colon closes open brackets`() {
        // Per NovelAI docs, :: terminates an unclosed weighted/braced section.
        val segments = PromptParser.splitTopLevel("{{a, b::, c")
        assertEquals(listOf("{{a, b::", " c"), segments)
    }

    @Test
    fun `unknown constructs are preserved verbatim`() {
        val entries = PromptParser.parse("weird|construct, <lora:thing:1>")
        assertEquals("weird|construct", entries[0].tag)
        assertEquals("<lora:thing:1>", entries[1].tag)
    }

    @Test
    fun `render round trip for typical prompts`() {
        val prompts = listOf(
            "1girl, solo",
            "{masterpiece}, best quality, [bad anatomy]",
            "1.5::smile::, artist:mizuki hitoshi",
            "{{very long hair}}, -1.5::nipples::, standing",
        )
        prompts.forEach { prompt ->
            assertEquals(prompt, PromptRenderer.renderCombo(PromptParser.parse(prompt)))
        }
    }
}
