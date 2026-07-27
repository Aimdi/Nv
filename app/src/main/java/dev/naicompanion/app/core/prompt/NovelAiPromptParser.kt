package dev.naicompanion.app.core.prompt

/**
 * Parses an existing NovelAI prompt string back into editable [TagEntry] values.
 *
 * This powers "paste a prompt into the builder" and the import path for prompts saved as plain
 * text. The scanner tracks emphasis as ambient state rather than building a tree, which means a
 * scope that spans several comma-separated tags (`1.5::a, b::`) correctly gives both `a` and `b`
 * the same weight.
 */
object NovelAiPromptParser {

    private const val ARTIST_PREFIX = "artist:"

    private val TRAILING_NUMBER = Regex("(-?\\d+(?:\\.\\d+)?)$")

    private data class NumericScope(
        val weight: Double,
        val bracesAtEntry: Int,
        val bracketsAtEntry: Int,
    )

    fun parse(prompt: String): List<TagEntry> {
        val entries = mutableListOf<TagEntry>()
        val buffer = StringBuilder()
        var braces = 0
        var brackets = 0
        val numericScopes = ArrayDeque<NumericScope>()

        fun currentWeight(): Double? {
            if (numericScopes.isEmpty()) return null
            return numericScopes.fold(1.0) { acc, scope -> acc * scope.weight }
        }

        fun flush() {
            val raw = buffer.toString().trim()
            buffer.setLength(0)
            if (raw.isEmpty()) return
            entries += buildEntry(raw, braces - brackets, currentWeight())
        }

        var index = 0
        while (index < prompt.length) {
            when (val ch = prompt[index]) {
                '\\' -> {
                    // Backslash escapes the emphasis characters so they can appear literally.
                    if (index + 1 < prompt.length) {
                        buffer.append(prompt[index + 1])
                        index++
                    } else {
                        buffer.append(ch)
                    }
                }

                '{' -> { flush(); braces++ }
                '}' -> { flush(); if (braces > 0) braces-- }
                '[' -> { flush(); brackets++ }
                ']' -> { flush(); if (brackets > 0) brackets-- }

                ':' -> {
                    if (index + 1 < prompt.length && prompt[index + 1] == ':') {
                        index++
                        val opening = takeOpeningWeight(buffer)
                        if (opening != null) {
                            flush()
                            numericScopes.addLast(NumericScope(opening, braces, brackets))
                        } else {
                            flush()
                            val scope = numericScopes.removeLastOrNull()
                            if (scope != null) {
                                // A bare `::` also closes any emphasis brackets left open inside
                                // the weighted section.
                                braces = scope.bracesAtEntry
                                brackets = scope.bracketsAtEntry
                            }
                        }
                    } else {
                        buffer.append(ch)
                    }
                }

                ',', '\n' -> flush()

                else -> buffer.append(ch)
            }
            index++
        }
        flush()
        return entries
    }

    /**
     * If the buffer ends with a standalone number then this `::` opens a weighted section; the
     * number is consumed from the buffer and returned. Otherwise the `::` is a closing marker.
     */
    private fun takeOpeningWeight(buffer: StringBuilder): Double? {
        val text = buffer.toString()
        val match = TRAILING_NUMBER.find(text) ?: return null
        val remainder = text.substring(0, match.range.first)
        val standalone = remainder.isEmpty() ||
            remainder.last().isWhitespace() ||
            remainder.last() == ','
        if (!standalone) return null
        val weight = match.groupValues[1].toDoubleOrNull() ?: return null
        buffer.setLength(0)
        buffer.append(remainder)
        return weight
    }

    private fun buildEntry(rawTag: String, bracketCount: Int, weight: Double?): TagEntry {
        val isArtist = rawTag.startsWith(ARTIST_PREFIX, ignoreCase = true)
        val tag = if (isArtist) rawTag.substring(ARTIST_PREFIX.length).trim() else rawTag
        return TagEntry(
            tag = tag,
            kind = if (isArtist) TagKind.ARTIST else TagKind.GENERAL,
            bracketCount = bracketCount.coerceIn(
                -TagEntry.MAX_BRACKET_COUNT,
                TagEntry.MAX_BRACKET_COUNT,
            ),
            numericWeight = weight,
        )
    }
}
