package dev.naicompanion.app.core.prompt

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class NovelAiPromptRendererTest {

    private fun tag(
        tag: String,
        kind: TagKind = TagKind.GENERAL,
        brackets: Int = 0,
        weight: Double? = null,
        enabled: Boolean = true,
    ) = TagEntry(
        id = tag,
        tag = tag,
        kind = kind,
        bracketCount = brackets,
        numericWeight = weight,
        enabled = enabled,
    )

    @Test
    fun `plain tags are joined with comma space`() {
        val result = NovelAiPromptRenderer.render(
            listOf(tag("1girl"), tag("solo"), tag("looking at viewer")),
        )
        assertThat(result).isEqualTo("1girl, solo, looking at viewer")
    }

    @Test
    fun `underscores become spaces by default`() {
        assertThat(NovelAiPromptRenderer.render(listOf(tag("blue_eyes"))))
            .isEqualTo("blue eyes")
    }

    @Test
    fun `underscore conversion can be disabled`() {
        val options = RenderOptions.Default.copy(underscoresToSpaces = false)
        assertThat(NovelAiPromptRenderer.render(listOf(tag("blue_eyes")), options))
            .isEqualTo("blue_eyes")
    }

    @Test
    fun `positive bracket count emits nested braces`() {
        assertThat(NovelAiPromptRenderer.render(listOf(tag("smile", brackets = 3))))
            .isEqualTo("{{{smile}}}")
    }

    @Test
    fun `negative bracket count emits nested square brackets`() {
        assertThat(NovelAiPromptRenderer.render(listOf(tag("smile", brackets = -2))))
            .isEqualTo("[[smile]]")
    }

    @Test
    fun `numeric weight uses the double colon form`() {
        assertThat(NovelAiPromptRenderer.render(listOf(tag("detailed background", weight = 1.5))))
            .isEqualTo("1.5::detailed background::")
    }

    @Test
    fun `numeric weight drops trailing zeros`() {
        assertThat(NovelAiPromptRenderer.render(listOf(tag("sky", weight = 2.0))))
            .isEqualTo("2::sky::")
    }

    @Test
    fun `weight of exactly one is omitted`() {
        assertThat(NovelAiPromptRenderer.render(listOf(tag("sky", weight = 1.0))))
            .isEqualTo("sky")
    }

    @Test
    fun `fractional weights weaken`() {
        assertThat(NovelAiPromptRenderer.render(listOf(tag("blush", weight = 0.7))))
            .isEqualTo("0.7::blush::")
    }

    @Test
    fun `negative weight is allowed on v4_5`() {
        val result = NovelAiPromptRenderer.renderWithWarnings(
            listOf(tag("nipples", weight = -1.5)),
            RenderOptions.Default.copy(model = NovelAiModel.V4_5),
        )
        assertThat(result.text).isEqualTo("-1.5::nipples::")
        assertThat(result.warnings).isEmpty()
    }

    @Test
    fun `negative weight warns on older models`() {
        val result = NovelAiPromptRenderer.renderWithWarnings(
            listOf(tag("nipples", weight = -1.5)),
            RenderOptions.Default.copy(model = NovelAiModel.V4),
        )
        assertThat(result.text).isEqualTo("-1.5::nipples::")
        assertThat(result.warnings).hasSize(1)
        assertThat(result.warnings.first().message).contains("V4.5")
    }

    @Test
    fun `brackets nest inside the numeric section when both are set`() {
        assertThat(
            NovelAiPromptRenderer.render(listOf(tag("ocean", brackets = 2, weight = 1.3))),
        ).isEqualTo("1.3::{{ocean}}::")
    }

    @Test
    fun `artist tags get the artist prefix on v4_5`() {
        assertThat(NovelAiPromptRenderer.render(listOf(tag("ainiwaffles", kind = TagKind.ARTIST))))
            .isEqualTo("artist:ainiwaffles")
    }

    @Test
    fun `artist prefix is not applied on v3`() {
        val options = RenderOptions.Default.copy(model = NovelAiModel.V3)
        assertThat(
            NovelAiPromptRenderer.render(listOf(tag("ainiwaffles", kind = TagKind.ARTIST)), options),
        ).isEqualTo("ainiwaffles")
    }

    @Test
    fun `artist prefix is not duplicated`() {
        assertThat(
            NovelAiPromptRenderer.render(listOf(tag("artist:ainiwaffles", kind = TagKind.ARTIST))),
        ).isEqualTo("artist:ainiwaffles")
    }

    @Test
    fun `artist underscores become spaces and keep the prefix`() {
        assertThat(
            NovelAiPromptRenderer.render(listOf(tag("wlop_style", kind = TagKind.ARTIST))),
        ).isEqualTo("artist:wlop style")
    }

    @Test
    fun `artist tag with weight combines prefix and emphasis`() {
        assertThat(
            NovelAiPromptRenderer.render(
                listOf(tag("ainiwaffles", kind = TagKind.ARTIST, weight = 1.4)),
            ),
        ).isEqualTo("1.4::artist:ainiwaffles::")
    }

    @Test
    fun `disabled tags are skipped`() {
        val result = NovelAiPromptRenderer.render(
            listOf(tag("1girl"), tag("solo", enabled = false), tag("smile")),
        )
        assertThat(result).isEqualTo("1girl, smile")
    }

    @Test
    fun `blank tags are dropped`() {
        assertThat(NovelAiPromptRenderer.render(listOf(tag("1girl"), tag("   "), tag("solo"))))
            .isEqualTo("1girl, solo")
    }

    @Test
    fun `repeated whitespace is collapsed`() {
        assertThat(NovelAiPromptRenderer.render(listOf(tag("looking   at    viewer"))))
            .isEqualTo("looking at viewer")
    }

    @Test
    fun `literal braces in a tag are escaped`() {
        assertThat(NovelAiPromptRenderer.render(listOf(tag("weird{tag}"))))
            .isEqualTo("weird\\{tag\\}")
    }

    @Test
    fun `parentheses in danbooru tags are left alone`() {
        assertThat(NovelAiPromptRenderer.render(listOf(tag("hatsune_miku_(cosplay)"))))
            .isEqualTo("hatsune miku (cosplay)")
    }

    @Test
    fun `double colon inside a tag produces a warning`() {
        val result = NovelAiPromptRenderer.renderWithWarnings(listOf(tag("odd::tag")))
        assertThat(result.warnings.map { it.message }.any { it.contains("::") }).isTrue()
    }

    @Test
    fun `duplicate tags are reported once`() {
        val result = NovelAiPromptRenderer.renderWithWarnings(
            listOf(tag("1girl"), tag("solo"), tag("1girl")),
        )
        val duplicateWarnings = result.warnings.filter { it.message.contains("more than once") }
        assertThat(duplicateWarnings).hasSize(1)
    }

    @Test
    fun `duplicate detection ignores underscore and case differences`() {
        val result = NovelAiPromptRenderer.renderWithWarnings(
            listOf(tag("blue_eyes"), tag("Blue Eyes")),
        )
        assertThat(result.warnings.filter { it.message.contains("more than once") }).hasSize(1)
    }

    @Test
    fun `empty combo renders to empty string`() {
        assertThat(NovelAiPromptRenderer.renderWithWarnings(emptyList()).isEmpty).isTrue()
    }

    @Test
    fun `bracket count is clamped to the maximum nesting`() {
        val rendered = NovelAiPromptRenderer.render(listOf(tag("x", brackets = 50)))
        assertThat(rendered).isEqualTo("{".repeat(10) + "x" + "}".repeat(10))
    }

    @Test
    fun `effective weight multiplies bracket steps`() {
        assertThat(tag("x", brackets = 2).effectiveWeight).isWithin(1e-9).of(1.05 * 1.05)
        assertThat(tag("x", brackets = -1).effectiveWeight).isWithin(1e-9).of(1.0 / 1.05)
        assertThat(tag("x", weight = 1.5, brackets = 1).effectiveWeight)
            .isWithin(1e-9).of(1.5 * 1.05)
    }

    @Test
    fun `custom separator is honoured`() {
        val options = RenderOptions.Default.copy(separator = ",\n")
        assertThat(NovelAiPromptRenderer.render(listOf(tag("a"), tag("b")), options))
            .isEqualTo("a,\nb")
    }

    @Test
    fun `weights are formatted without locale decimal separators`() {
        val previous = java.util.Locale.getDefault()
        try {
            java.util.Locale.setDefault(java.util.Locale.GERMANY)
            assertThat(NovelAiPromptRenderer.render(listOf(tag("sky", weight = 1.25))))
                .isEqualTo("1.25::sky::")
        } finally {
            java.util.Locale.setDefault(previous)
        }
    }
}
