package com.naicompanion.render

import com.naicompanion.data.model.TagEntry
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Renders [TagEntry] lists into NovelAI prompt syntax.
 *
 * NovelAI rules (docs.novelai.net/en/image/strengthening-weakening):
 *  - `{tag}`  -> attention x1.05, nestable (`{{tag}}` = x1.05^2)
 *  - `[tag]`  -> attention /1.05, nestable
 *  - `1.5::tag::` -> numeric emphasis; `::` also closes open brackets
 *  - Danbooru underscores should become spaces ("can effect the results
 *    noticeably" per the dataset authors)
 */
object PromptRenderer {

    fun renderTag(raw: String): String = raw.trim().replace('_', ' ')

    /** Returns null when the entry is disabled or blank. */
    fun renderEntry(entry: TagEntry): String? {
        if (!entry.enabled) return null
        val base = renderTag(entry.tag)
        if (base.isEmpty()) return null

        entry.numericWeight?.let { w ->
            return "${formatWeight(w)}::$base::"
        }
        return when {
            entry.bracketCount > 0 ->
                "{".repeat(entry.bracketCount) + base + "}".repeat(entry.bracketCount)
            entry.bracketCount < 0 ->
                "[".repeat(-entry.bracketCount) + base + "]".repeat(-entry.bracketCount)
            else -> base
        }
    }

    fun renderCombo(entries: List<TagEntry>): String =
        entries.mapNotNull(::renderEntry).joinToString(", ")

    /** `1.5` -> "1.5", `1.0` -> "1", `0.70000001` -> "0.7" */
    fun formatWeight(weight: Float): String =
        BigDecimal(weight.toDouble())
            .setScale(2, RoundingMode.HALF_UP)
            .stripTrailingZeros()
            .toPlainString()
}
