package dev.naicompanion.app.core.prompt

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class NovelAiPromptParserTest {

    @Test
    fun `splits on commas`() {
        val entries = NovelAiPromptParser.parse("1girl, solo, smile")
        assertThat(entries.map { it.tag }).containsExactly("1girl", "solo", "smile").inOrder()
        assertThat(entries.all { it.bracketCount == 0 && it.numericWeight == null }).isTrue()
    }

    @Test
    fun `splits on newlines as well`() {
        val entries = NovelAiPromptParser.parse("1girl\nsolo,\nsmile")
        assertThat(entries.map { it.tag }).containsExactly("1girl", "solo", "smile").inOrder()
    }

    @Test
    fun `reads brace nesting depth`() {
        val entries = NovelAiPromptParser.parse("{{smile}}")
        assertThat(entries).hasSize(1)
        assertThat(entries[0].tag).isEqualTo("smile")
        assertThat(entries[0].bracketCount).isEqualTo(2)
    }

    @Test
    fun `reads square bracket nesting as negative`() {
        val entries = NovelAiPromptParser.parse("[[[blurry]]]")
        assertThat(entries[0].bracketCount).isEqualTo(-3)
    }

    @Test
    fun `reads numeric emphasis`() {
        val entries = NovelAiPromptParser.parse("1.5::detailed background::")
        assertThat(entries).hasSize(1)
        assertThat(entries[0].tag).isEqualTo("detailed background")
        assertThat(entries[0].numericWeight).isEqualTo(1.5)
    }

    @Test
    fun `reads negative numeric emphasis`() {
        val entries = NovelAiPromptParser.parse("-1.5::nipples::")
        assertThat(entries[0].tag).isEqualTo("nipples")
        assertThat(entries[0].numericWeight).isEqualTo(-1.5)
    }

    @Test
    fun `a weighted section applies to every tag inside it`() {
        val entries = NovelAiPromptParser.parse("1.4::ocean, sky::, 1girl")
        assertThat(entries.map { it.tag }).containsExactly("ocean", "sky", "1girl").inOrder()
        assertThat(entries[0].numericWeight).isEqualTo(1.4)
        assertThat(entries[1].numericWeight).isEqualTo(1.4)
        assertThat(entries[2].numericWeight).isNull()
    }

    @Test
    fun `nested weighted sections multiply`() {
        val entries = NovelAiPromptParser.parse("2::a 1.5::b::::")
        val b = entries.first { it.tag == "b" }
        assertThat(b.numericWeight).isWithin(1e-9).of(3.0)
        val a = entries.first { it.tag == "a" }
        assertThat(a.numericWeight).isWithin(1e-9).of(2.0)
    }

    @Test
    fun `artist prefix is recognised and stripped`() {
        val entries = NovelAiPromptParser.parse("artist:ainiwaffles, 1girl")
        assertThat(entries[0].tag).isEqualTo("ainiwaffles")
        assertThat(entries[0].kind).isEqualTo(TagKind.ARTIST)
        assertThat(entries[1].kind).isEqualTo(TagKind.GENERAL)
    }

    @Test
    fun `combined numeric and bracket emphasis is recovered`() {
        val entries = NovelAiPromptParser.parse("1.3::{{ocean}}::")
        assertThat(entries).hasSize(1)
        assertThat(entries[0].tag).isEqualTo("ocean")
        assertThat(entries[0].bracketCount).isEqualTo(2)
        assertThat(entries[0].numericWeight).isEqualTo(1.3)
    }

    @Test
    fun `a bare double colon closes open brackets`() {
        val entries = NovelAiPromptParser.parse("1.5::{ocean::, sky")
        assertThat(entries.map { it.tag }).containsExactly("ocean", "sky").inOrder()
        assertThat(entries[1].bracketCount).isEqualTo(0)
        assertThat(entries[1].numericWeight).isNull()
    }

    @Test
    fun `escaped braces are treated as literal text`() {
        val entries = NovelAiPromptParser.parse("weird\\{tag\\}")
        assertThat(entries).hasSize(1)
        assertThat(entries[0].tag).isEqualTo("weird{tag}")
        assertThat(entries[0].bracketCount).isEqualTo(0)
    }

    @Test
    fun `a number attached to a word is not read as a weight`() {
        val entries = NovelAiPromptParser.parse("cat-1.5::tag::")
        assertThat(entries.map { it.tag }).contains("cat-1.5")
    }

    @Test
    fun `tags that merely start with a digit are preserved`() {
        val entries = NovelAiPromptParser.parse("1girl, 2boys")
        assertThat(entries.map { it.tag }).containsExactly("1girl", "2boys").inOrder()
    }

    @Test
    fun `unbalanced closing brackets do not go negative`() {
        val entries = NovelAiPromptParser.parse("}}}smile")
        assertThat(entries[0].tag).isEqualTo("smile")
        assertThat(entries[0].bracketCount).isEqualTo(0)
    }

    @Test
    fun `blank input yields no entries`() {
        assertThat(NovelAiPromptParser.parse("   \n , , ")).isEmpty()
    }

    @Test
    fun `parentheses survive parsing`() {
        val entries = NovelAiPromptParser.parse("hatsune miku (cosplay)")
        assertThat(entries[0].tag).isEqualTo("hatsune miku (cosplay)")
    }

    @Test
    fun `single colons are preserved inside a tag`() {
        val entries = NovelAiPromptParser.parse("time:noon")
        assertThat(entries[0].tag).isEqualTo("time:noon")
    }

    @Test
    fun `round trips a rendered combo`() {
        val original = listOf(
            TagEntry(tag = "ainiwaffles", kind = TagKind.ARTIST, numericWeight = 1.4),
            TagEntry(tag = "1girl"),
            TagEntry(tag = "detailed_background", bracketCount = 2),
            TagEntry(tag = "blurry", bracketCount = -1),
            TagEntry(tag = "nipples", numericWeight = -1.2),
        )
        val rendered = NovelAiPromptRenderer.render(original)
        val parsed = NovelAiPromptParser.parse(rendered)

        assertThat(parsed).hasSize(original.size)
        original.forEachIndexed { index, expected ->
            val actual = parsed[index]
            val expectedTag = NovelAiPromptRenderer.normalizeTagText(expected.tag)
            assertThat(actual.tag).isEqualTo(expectedTag)
            assertThat(actual.kind).isEqualTo(expected.kind)
            assertThat(actual.bracketCount).isEqualTo(expected.bracketCount)
            if (expected.numericWeight == null) {
                assertThat(actual.numericWeight).isNull()
            } else {
                assertThat(actual.numericWeight!!).isWithin(1e-9).of(expected.numericWeight!!)
            }
        }
        assertThat(NovelAiPromptRenderer.render(parsed)).isEqualTo(rendered)
    }

    @Test
    fun `re-rendering a parsed prompt is stable`() {
        val prompt = "artist:wlop, 1girl, {{{masterpiece}}}, 0.8::simple background::, [blurry]"
        val once = NovelAiPromptRenderer.render(NovelAiPromptParser.parse(prompt))
        val twice = NovelAiPromptRenderer.render(NovelAiPromptParser.parse(once))
        assertThat(twice).isEqualTo(once)
    }
}
