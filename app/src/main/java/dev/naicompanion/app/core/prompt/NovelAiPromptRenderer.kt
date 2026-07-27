package dev.naicompanion.app.core.prompt

import kotlin.math.abs

/**
 * Turns [TagEntry] lists into NovelAI prompt strings.
 *
 * Syntax reference (docs.novelai.net/en/image/strengthening-weakening):
 *  - `{tag}` multiplies attention by 1.05 per nesting level, `[tag]` divides by 1.05 per level.
 *  - `1.5::tag::` applies a numeric emphasis to everything between the weighted `::` and the
 *    next bare `::`. Values between 0 and 1 weaken; negative values are V4.5+ only.
 *  - Artist tags take an `artist:` prefix from V4 onwards.
 */
object NovelAiPromptRenderer {

    private const val ARTIST_PREFIX = "artist:"

    /** Weights this close to 1.0 are a no-op, so we drop the `::` wrapper rather than emit noise. */
    private const val NEUTRAL_WEIGHT_EPSILON = 1e-6

    fun render(entries: List<TagEntry>, options: RenderOptions = RenderOptions.Default): String =
        renderWithWarnings(entries, options).text

    fun renderWithWarnings(
        entries: List<TagEntry>,
        options: RenderOptions = RenderOptions.Default,
    ): RenderResult {
        val warnings = mutableListOf<RenderWarning>()
        val pieces = mutableListOf<String>()

        for (entry in entries) {
            if (!entry.enabled) continue
            val rendered = renderEntry(entry, options, warnings) ?: continue
            pieces += rendered
        }

        warnings += duplicateWarnings(entries, options)
        return RenderResult(pieces.joinToString(options.separator), warnings)
    }

    /** Renders a single entry, or null when the entry contributes nothing. */
    fun renderEntry(
        entry: TagEntry,
        options: RenderOptions = RenderOptions.Default,
        warnings: MutableList<RenderWarning> = mutableListOf(),
    ): String? {
        val normalized = normalizeTagText(entry.tag, options)
        if (normalized.isEmpty()) return null

        if (normalized.contains("::")) {
            warnings += RenderWarning(
                entry.id,
                "\"${entry.tag}\" contains \"::\", which NovelAI reads as an emphasis marker.",
            )
        }

        var text = escapeLiteralBrackets(normalized)
        text = applyArtistPrefix(text, entry.kind, options)
        text = applyBrackets(text, entry.bracketCount)
        text = applyNumericWeight(text, entry, options, warnings)
        return text
    }

    /**
     * Applies the underscore-to-space conversion and trims stray whitespace. Kept public so the
     * tag browser can show tags exactly as they will be prompted.
     */
    fun normalizeTagText(rawTag: String, options: RenderOptions = RenderOptions.Default): String {
        val trimmed = rawTag.trim()
        if (trimmed.isEmpty()) return ""
        val spaced = if (options.underscoresToSpaces) trimmed.replace('_', ' ') else trimmed
        return spaced.replace(WHITESPACE_RUN, " ").trim()
    }

    /**
     * Braces and square brackets are emphasis markers, so a tag that genuinely contains one has to
     * be backslash-escaped to survive the prompt parser.
     */
    private fun escapeLiteralBrackets(text: String): String {
        if (text.none { it in BRACKET_CHARS }) return text
        return buildString(text.length + 4) {
            for (ch in text) {
                if (ch in BRACKET_CHARS) append('\\')
                append(ch)
            }
        }
    }

    private fun applyArtistPrefix(text: String, kind: TagKind, options: RenderOptions): String {
        if (kind != TagKind.ARTIST) return text
        if (!options.artistPrefix || !options.model.supportsArtistPrefix) return text
        if (text.startsWith(ARTIST_PREFIX, ignoreCase = true)) return text
        return ARTIST_PREFIX + text
    }

    private fun applyBrackets(text: String, bracketCount: Int): String {
        if (bracketCount == 0) return text
        val depth = bracketCount.coerceIn(-TagEntry.MAX_BRACKET_COUNT, TagEntry.MAX_BRACKET_COUNT)
        val (open, close) = if (depth > 0) '{' to '}' else '[' to ']'
        val levels = abs(depth)
        return buildString(text.length + levels * 2) {
            repeat(levels) { append(open) }
            append(text)
            repeat(levels) { append(close) }
        }
    }

    private fun applyNumericWeight(
        text: String,
        entry: TagEntry,
        options: RenderOptions,
        warnings: MutableList<RenderWarning>,
    ): String {
        val weight = entry.numericWeight ?: return text
        if (abs(weight - 1.0) < NEUTRAL_WEIGHT_EPSILON) return text

        if (weight < 0 && !options.model.supportsNegativeWeights) {
            warnings += RenderWarning(
                entry.id,
                "Negative emphasis on \"${entry.tag}\" needs ${NovelAiModel.V4_5.label} or newer.",
            )
        }
        return "${formatWeight(weight)}::$text::"
    }

    private fun duplicateWarnings(
        entries: List<TagEntry>,
        options: RenderOptions,
    ): List<RenderWarning> {
        val seen = mutableSetOf<String>()
        val duplicates = linkedSetOf<String>()
        for (entry in entries) {
            if (!entry.enabled) continue
            val key = normalizeTagText(entry.tag, options).lowercase()
            if (key.isEmpty()) continue
            if (!seen.add(key)) duplicates += key
        }
        return duplicates.map { RenderWarning(null, "\"$it\" appears more than once.") }
    }

    private val BRACKET_CHARS = charArrayOf('{', '}', '[', ']')
    private val WHITESPACE_RUN = Regex("\\s+")
}
