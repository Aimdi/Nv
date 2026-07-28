package dev.naicompanion.app.core.prompt

import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.round

/**
 * Pure-Kotlin NovelAI weighting helpers.
 *
 * Serialization and parsing live in [NovelAiPromptRenderer] / [NovelAiPromptParser]; this object
 * adds the brace-depth ↔ numeric conversions used by chip weight sliders and unit tests.
 *
 * Rules (docs.novelai.net Strengthening/Weakening):
 * - Each `{…}` multiplies attention by 1.05; each `[…]` divides by 1.05.
 * - `w::tag::` applies a numeric weight to the enclosed span.
 * - Underscores become spaces on export when that setting is on.
 * - Artist tags take an `artist:` prefix on V4+.
 */
object WeightEngine {

    const val BRACE_FACTOR = 1.05

    /** Maps a multiplicative weight to NovelAI brace nesting depth (`+` → `{}`, `-` → `[]`). */
    fun weightToBraceDepth(weight: Double): Int {
        if (weight <= 0.0 || !weight.isFinite()) return 0
        if (abs(weight - 1.0) < 1e-9) return 0
        return round(ln(weight) / ln(BRACE_FACTOR)).toInt()
            .coerceIn(-TagEntry.MAX_BRACKET_COUNT, TagEntry.MAX_BRACKET_COUNT)
    }

    /** Inverse of [weightToBraceDepth]: depth `n` → `1.05^n`. */
    fun braceDepthToWeight(depth: Int): Double {
        if (depth == 0) return 1.0
        var result = 1.0
        repeat(abs(depth)) {
            result = if (depth > 0) result * BRACE_FACTOR else result / BRACE_FACTOR
        }
        return result
    }

    fun render(entries: List<TagEntry>, options: RenderOptions = RenderOptions.Default): String =
        NovelAiPromptRenderer.render(entries, options)

    fun renderWithWarnings(
        entries: List<TagEntry>,
        options: RenderOptions = RenderOptions.Default,
    ): RenderResult = NovelAiPromptRenderer.renderWithWarnings(entries, options)

    fun parse(prompt: String): List<TagEntry> = NovelAiPromptParser.parse(prompt)

    fun wrapWithBraces(text: String, depth: Int): String {
        if (depth == 0) return text
        val (open, close) = if (depth > 0) '{' to '}' else '[' to ']'
        val levels = abs(depth)
        return buildString(text.length + levels * 2) {
            repeat(levels) { append(open) }
            append(text)
            repeat(levels) { append(close) }
        }
    }

    fun wrapNumeric(text: String, weight: Double): String {
        if (abs(weight - 1.0) < 1e-9) return text
        return "${formatWeight(weight)}::$text::"
    }
}
