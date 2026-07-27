package com.naicompanion.render

import com.naicompanion.data.model.TagEntry

/**
 * Parses an existing NovelAI prompt string back into [TagEntry]s so a saved
 * prompt can be loaded into the builder. Tolerant by design: anything it
 * cannot understand is preserved verbatim as a plain tag, so
 * `renderCombo(parse(x))` never loses text for well-formed inputs.
 */
object PromptParser {

    private val numericWeightPattern =
        Regex("""^([+-]?\d+(?:\.\d+)?)::(.*)::$""", RegexOption.DOT_MATCHES_ALL)

    fun parse(prompt: String): List<TagEntry> =
        splitTopLevel(prompt).mapNotNull(::toEntry)

    /**
     * Splits on commas that are not inside `{ }`, `[ ]`, or a `x.x::...::`
     * weighted section. A `::` also closes any open brackets, mirroring
     * NovelAI's own behaviour.
     */
    private val weightPrefixPattern = Regex("""^[+-]?\d+(\.\d+)?$""")

    internal fun splitTopLevel(input: String): List<String> {
        val segments = mutableListOf<String>()
        val current = StringBuilder()
        var braceDepth = 0
        var bracketDepth = 0
        var inWeightedSection = false

        var i = 0
        while (i < input.length) {
            val c = input[i]
            val isColonPair = c == ':' && i + 1 < input.length && input[i + 1] == ':'
            if (isColonPair) {
                if (inWeightedSection) {
                    inWeightedSection = false
                } else if (weightPrefixPattern.matches(current.trim().toString())) {
                    // "1.5::" opens a weighted section; it also closes brackets.
                    inWeightedSection = true
                    braceDepth = 0
                    bracketDepth = 0
                } else {
                    // A bare "::" with no weight number only closes any open
                    // brackets (NovelAI docs) — it does not open a section.
                    braceDepth = 0
                    bracketDepth = 0
                }
                current.append("::")
                i += 2
                continue
            }
            when (c) {
                '{' -> braceDepth++
                '}' -> if (braceDepth > 0) braceDepth--
                '[' -> bracketDepth++
                ']' -> if (bracketDepth > 0) bracketDepth--
                ',' -> if (braceDepth == 0 && bracketDepth == 0 && !inWeightedSection) {
                    segments += current.toString()
                    current.clear()
                    i++
                    continue
                }
            }
            current.append(c)
            i++
        }
        segments += current.toString()
        return segments
    }

    internal fun toEntry(segment: String): TagEntry? {
        val trimmed = segment.trim()
        if (trimmed.isEmpty()) return null

        numericWeightPattern.matchEntire(trimmed)?.let { match ->
            val weight = match.groupValues[1].toFloatOrNull()
            val inner = match.groupValues[2].trim()
            if (weight != null && inner.isNotEmpty()) {
                return TagEntry(tag = inner, numericWeight = weight)
            }
        }

        val first = trimmed.first()
        if (first == '{' || first == '[') {
            val closing = if (first == '{') '}' else ']'
            var open = 0
            while (open < trimmed.length && trimmed[open] == first) open++
            var close = 0
            while (close < trimmed.length - open && trimmed[trimmed.length - 1 - close] == closing) close++
            val pairs = minOf(open, close)
            if (pairs > 0) {
                val inner = trimmed.substring(pairs, trimmed.length - pairs).trim()
                if (inner.isNotEmpty()) {
                    return TagEntry(
                        tag = inner,
                        bracketCount = if (first == '{') pairs else -pairs,
                    )
                }
            }
        }

        return TagEntry(tag = trimmed)
    }
}
