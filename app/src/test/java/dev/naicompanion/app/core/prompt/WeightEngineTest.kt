package dev.naicompanion.app.core.prompt

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import kotlin.math.abs

class WeightEngineTest {

    @Test
    fun `brace depth maps to 1_05 powers`() {
        assertThat(WeightEngine.braceDepthToWeight(0)).isEqualTo(1.0)
        assertThat(WeightEngine.braceDepthToWeight(1)).isWithin(1e-9).of(1.05)
        assertThat(WeightEngine.braceDepthToWeight(2)).isWithin(1e-9).of(1.1025)
        assertThat(WeightEngine.braceDepthToWeight(-2)).isWithin(1e-6).of(1.0 / (1.05 * 1.05))
    }

    @Test
    fun `weight to brace depth round trips common values`() {
        assertThat(WeightEngine.weightToBraceDepth(1.0)).isEqualTo(0)
        assertThat(WeightEngine.weightToBraceDepth(1.05)).isEqualTo(1)
        assertThat(WeightEngine.weightToBraceDepth(1.1025)).isEqualTo(2)
        assertThat(WeightEngine.weightToBraceDepth(1.0 / 1.05)).isEqualTo(-1)
        assertThat(WeightEngine.weightToBraceDepth(1.0 / (1.05 * 1.05))).isEqualTo(-2)
    }

    @Test
    fun `wrap helpers emit NovelAI syntax`() {
        assertThat(WeightEngine.wrapWithBraces("tag", 2)).isEqualTo("{{tag}}")
        assertThat(WeightEngine.wrapWithBraces("tag", -2)).isEqualTo("[[tag]]")
        assertThat(WeightEngine.wrapNumeric("tag", 1.3)).isEqualTo("1.3::tag::")
        assertThat(WeightEngine.wrapNumeric("tag", 1.0)).isEqualTo("tag")
    }

    @Test
    fun `render applies artist prefix spaces and braces`() {
        val entries = listOf(
            TagEntry(tag = "hammer_(sunset_beach)", kind = TagKind.ARTIST, bracketCount = 1),
            TagEntry(tag = "1girl", bracketCount = 0),
        )
        val text = WeightEngine.render(
            entries,
            RenderOptions(model = NovelAiModel.V4_5, underscoresToSpaces = true, artistPrefix = true),
        )
        assertThat(text).isEqualTo("{artist:hammer (sunset beach)}, 1girl")
    }

    @Test
    fun `numeric syntax round trips through parse`() {
        val original = listOf(TagEntry(tag = "monochrome", numericWeight = 1.3))
        val rendered = WeightEngine.render(original)
        assertThat(rendered).contains("1.3::")
        val parsed = WeightEngine.parse(rendered)
        assertThat(parsed).hasSize(1)
        assertThat(parsed[0].tag).isEqualTo("monochrome")
        assertThat(abs((parsed[0].numericWeight ?: 0.0) - 1.3)).isLessThan(1e-9)
    }

    @Test
    fun `double brackets parse to depth -2`() {
        val parsed = WeightEngine.parse("[[tag]]")
        assertThat(parsed).hasSize(1)
        assertThat(parsed[0].bracketCount).isEqualTo(-2)
        assertThat(WeightEngine.braceDepthToWeight(parsed[0].bracketCount))
            .isWithin(1e-6).of(0.907029478)
    }
}
