package com.nai.promptcompanion

import com.nai.promptcompanion.novelai.NovelaiSyntax
import com.nai.promptcompanion.novelai.NovelaiSyntax.parse
import com.nai.promptcompanion.novelai.NovelaiSyntax.render
import com.nai.promptcompanion.novelai.TagEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NovelaiSyntaxTest {

    @Test
    fun `plain tag renders as-is with underscores converted to spaces`() {
        assertEquals(
            "looking at viewer",
            NovelaiSyntax.renderEntry(TagEntry("looking_at_viewer"))
        )
    }

    @Test
    fun `braces multiply attention`() {
        assertEquals("{cat ears}", NovelaiSyntax.renderEntry(TagEntry("cat ears", bracketCount = 1)))
        assertEquals("{{cat ears}}", NovelaiSyntax.renderEntry(TagEntry("cat ears", bracketCount = 2)))
        assertEquals("{{{cat ears}}}", NovelaiSyntax.renderEntry(TagEntry("cat ears", bracketCount = 3)))
    }

    @Test
    fun `brackets weaken attention`() {
        assertEquals("[worst quality]", NovelaiSyntax.renderEntry(TagEntry("worst quality", bracketCount = -1)))
        assertEquals("[[worst quality]]", NovelaiSyntax.renderEntry(TagEntry("worst quality", bracketCount = -2)))
    }

    @Test
    fun `numeric emphasis renders weight form`() {
        assertEquals("1.5::tag::", NovelaiSyntax.renderEntry(TagEntry("tag", numericWeight = 1.5)))
        assertEquals("1.3::artist:wlop::", NovelaiSyntax.renderEntry(TagEntry("artist:wlop", numericWeight = 1.3)))
        assertEquals("0.7::tag::", NovelaiSyntax.renderEntry(TagEntry("tag", numericWeight = 0.7)))
        // negative emphasis is supported on NovelAI V4.5+
        assertEquals("-1.5::nipples::", NovelaiSyntax.renderEntry(TagEntry("nipples", numericWeight = -1.5)))
    }

    @Test
    fun `weight of 1 point 0 renders plain`() {
        assertEquals("tag", NovelaiSyntax.renderEntry(TagEntry("tag", numericWeight = 1.0)))
    }

    @Test
    fun `numeric weight takes precedence over brackets`() {
        assertEquals(
            "1.5::tag::",
            NovelaiSyntax.renderEntry(TagEntry("tag", bracketCount = 2, numericWeight = 1.5))
        )
    }

    @Test
    fun `disabled entries are skipped`() {
        val entries = listOf(
            TagEntry("solo"),
            TagEntry("bad hands", enabled = false),
            TagEntry("smile"),
        )
        assertEquals("solo, smile", render(entries))
    }

    @Test
    fun `entries join with comma space`() {
        val entries = listOf(
            TagEntry("1girl"),
            TagEntry("artist:wlop", bracketCount = 2),
            TagEntry("masterpiece", numericWeight = 1.2),
        )
        assertEquals("1girl, {{artist:wlop}}, 1.2::masterpiece::", render(entries))
    }

    @Test
    fun `artist prefix is preserved`() {
        assertEquals("artist:ainiwaffles", NovelaiSyntax.renderEntry(TagEntry("artist:ainiwaffles")))
        assertEquals("artist:mizuki hitoshi", NovelaiSyntax.renderEntry(TagEntry("artist:mizuki_hitoshi")))
    }

    @Test
    fun `weight formatting trims trailing zeros`() {
        assertEquals("1.05", NovelaiSyntax.formatWeight(1.05))
        assertEquals("1.1", NovelaiSyntax.formatWeight(1.10))
        assertEquals("0.5", NovelaiSyntax.formatWeight(0.50))
        assertEquals("-1.25", NovelaiSyntax.formatWeight(-1.25))
        assertEquals("2", NovelaiSyntax.formatWeight(2.0))
    }

    @Test
    fun `effective weight uses 1 point 05 per brace level`() {
        assertEquals(1.0, TagEntry("x").effectiveWeight, 0.0001)
        assertEquals(1.05, TagEntry("x", bracketCount = 1).effectiveWeight, 0.0001)
        assertEquals(1.1025, TagEntry("x", bracketCount = 2).effectiveWeight, 0.0001)
        assertEquals(1.0 / 1.05, TagEntry("x", bracketCount = -1).effectiveWeight, 0.0001)
        assertEquals(1.5, TagEntry("x", bracketCount = 2, numericWeight = 1.5).effectiveWeight, 0.0001)
    }

    // ---- Parser ----

    @Test
    fun `parse handles plain comma separated tags`() {
        val parsed = parse("1girl, solo, looking at viewer")
        assertEquals(3, parsed.size)
        assertEquals("1girl", parsed[0].tag)
        assertEquals("looking at viewer", parsed[2].tag)
    }

    @Test
    fun `parse handles brace and bracket nesting`() {
        val parsed = parse("{{cat ears}}, [worst quality]")
        assertEquals(2, parsed.size)
        assertEquals(2, parsed[0].bracketCount)
        assertEquals("cat ears", parsed[0].tag)
        assertEquals(-1, parsed[1].bracketCount)
    }

    @Test
    fun `parse handles numeric emphasis`() {
        val parsed = parse("1.5::cat ears::, solo")
        assertEquals(2, parsed.size)
        assertEquals(1.5, parsed[0].numericWeight!!, 0.0001)
        assertEquals("cat ears", parsed[0].tag)
        assertEquals("solo", parsed[1].tag)
    }

    @Test
    fun `parse keeps commas inside numeric sections`() {
        val parsed = parse("1.5::cat ears, looking at viewer::, solo")
        assertEquals(2, parsed.size)
        assertEquals("cat ears, looking at viewer", parsed[0].tag)
        assertEquals(1.5, parsed[0].numericWeight!!, 0.0001)
        assertEquals("solo", parsed[1].tag)
    }

    @Test
    fun `parse handles negative numeric emphasis`() {
        val parsed = parse("-1.5::nipples::")
        assertEquals(1, parsed.size)
        assertEquals(-1.5, parsed[0].numericWeight!!, 0.0001)
    }

    @Test
    fun `render then parse round-trips`() {
        val entries = listOf(
            TagEntry("1girl"),
            TagEntry("artist:wlop", bracketCount = 2),
            TagEntry("masterpiece", numericWeight = 1.3),
            TagEntry("worst quality", bracketCount = -1),
            TagEntry("muted colors", numericWeight = 0.7),
        )
        val reparsed = parse(render(entries))
        assertEquals(entries.size, reparsed.size)
        entries.zip(reparsed).forEach { (a, b) ->
            assertEquals(a.tag, b.tag)
            assertEquals(a.bracketCount, b.bracketCount)
            assertEquals(a.numericWeight, b.numericWeight)
        }
    }

    @Test
    fun `parse ignores empty tokens`() {
        val parsed = parse(" , ,solo,, ")
        assertEquals(1, parsed.size)
        assertEquals("solo", parsed[0].tag)
    }

    @Test
    fun `parse is robust to unbalanced input`() {
        val parsed = parse("{{{odd, 1.5::unclosed")
        assertTrue(parsed.isNotEmpty())
    }
}
