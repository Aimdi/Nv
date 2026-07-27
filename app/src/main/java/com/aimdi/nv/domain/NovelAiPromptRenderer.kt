package com.aimdi.nv.domain

/**
 * Renders tag entries into NovelAI prompt syntax.
 *
 * Weighting rules (from NovelAI docs):
 * - `{tag}` multiplies attention by ×1.05 per brace pair
 * - `[tag]` divides attention by ×1.05 per bracket pair
 * - `1.5::tag::` applies numeric emphasis; close with `::`
 * - Underscores in Danbooru tags should become spaces
 * - Artist tags on V4/V4.5 use the `artist:` prefix
 */
object NovelAiPromptRenderer {

    fun render(entries: List<TagEntry>): String {
        return entries
            .filter { it.enabled }
            .joinToString(", ") { renderEntry(it) }
    }

    fun renderEntry(entry: TagEntry): String {
        val body = normalizeTag(entry.tag, entry.forceArtistPrefix)
        return when {
            entry.numericWeight != null -> {
                val w = formatWeight(entry.numericWeight)
                "$w::${applyBrackets(body, entry.bracketCount)}::"
            }
            entry.bracketCount != 0 -> applyBrackets(body, entry.bracketCount)
            else -> body
        }
    }

    /**
     * Convert underscores to spaces. Optionally ensure `artist:` prefix
     * when [forceArtistPrefix] is true and the tag does not already have one.
     */
    fun normalizeTag(raw: String, forceArtistPrefix: Boolean = false): String {
        var tag = raw.trim()
        if (tag.isEmpty()) return tag

        val lower = tag.lowercase()
        val hasArtistPrefix = lower.startsWith("artist:")

        if (forceArtistPrefix && !hasArtistPrefix) {
            tag = "artist:$tag"
        }

        // Convert underscores to spaces, but preserve the artist: prefix colon
        return if (tag.startsWith("artist:", ignoreCase = true)) {
            val prefix = tag.substring(0, 7) // "artist:"
            val name = tag.substring(7).replace('_', ' ')
            prefix + name
        } else {
            tag.replace('_', ' ')
        }
    }

    fun applyBrackets(body: String, bracketCount: Int): String {
        if (bracketCount == 0) return body
        return if (bracketCount > 0) {
            "{".repeat(bracketCount) + body + "}".repeat(bracketCount)
        } else {
            val n = -bracketCount
            "[".repeat(n) + body + "]".repeat(n)
        }
    }

    fun formatWeight(weight: Float): String {
        // Prefer compact representation: 1.5 not 1.50, 0.8 not 0.80
        val rounded = (weight * 100f).toInt() / 100f
        return if (rounded == rounded.toInt().toFloat()) {
            rounded.toInt().toString()
        } else {
            String.format("%.2f", rounded).trimEnd('0').trimEnd('.')
        }
    }

    /**
     * Estimate the effective attention multiplier for UI display.
     * Brace/bracket nesting is multiplicative ×1.05 each level.
     * Numeric weight replaces brace nesting when present.
     */
    fun effectiveMultiplier(entry: TagEntry): Float {
        entry.numericWeight?.let { return it }
        if (entry.bracketCount == 0) return 1f
        val factor = 1.05f
        var result = 1f
        repeat(kotlin.math.abs(entry.bracketCount)) {
            result *= factor
        }
        return if (entry.bracketCount > 0) result else 1f / result
    }
}

data class TagEntry(
    val id: String,
    val tag: String,
    val bracketCount: Int = 0,
    val numericWeight: Float? = null,
    val enabled: Boolean = true,
    val forceArtistPrefix: Boolean = false,
)
