package app.promptcompanion.nai.domain

/**
 * Pure NovelAI prompt rendering utilities.
 *
 * Syntax (from NovelAI docs):
 * - `{tag}` strengthens ×1.05 per brace nest
 * - `[tag]` weakens ×1.05 per bracket nest
 * - `1.5::tag::` numeric emphasis; values <1 weaken; negative supported on V4.5+
 * - Danbooru underscores should become spaces in the prompt
 * - Artist tags for V4/V4.5 use `artist:` prefix
 */
object NovelAiSyntax {

    fun normalizeTag(raw: String, forceArtistPrefix: Boolean = false): String {
        var tag = raw.trim()
        if (tag.isEmpty()) return tag

        // Preserve explicit artist: prefix casing/content after normalizing underscores.
        val hasArtistPrefix = tag.startsWith("artist:", ignoreCase = true)
        if (hasArtistPrefix) {
            val body = tag.substringAfter(':').trim().replace('_', ' ')
            return "artist:$body"
        }

        tag = tag.replace('_', ' ')
        return if (forceArtistPrefix) "artist:$tag" else tag
    }

    /**
     * Render a single tag entry to NovelAI syntax.
     * Numeric weight takes precedence over brace/bracket nesting when set.
     */
    fun renderTag(
        tag: String,
        bracketCount: Int = 0,
        numericWeight: Double? = null,
        enabled: Boolean = true,
        forceArtistPrefix: Boolean = false,
    ): String? {
        if (!enabled) return null
        val normalized = normalizeTag(tag, forceArtistPrefix)
        if (normalized.isEmpty()) return null

        if (numericWeight != null) {
            val weightText = formatWeight(numericWeight)
            return "$weightText::$normalized::"
        }

        return when {
            bracketCount > 0 -> "{".repeat(bracketCount) + normalized + "}".repeat(bracketCount)
            bracketCount < 0 -> "[".repeat(-bracketCount) + normalized + "]".repeat(-bracketCount)
            else -> normalized
        }
    }

    fun renderPrompt(entries: List<TagEntry>): String {
        return entries.mapNotNull { entry ->
            renderTag(
                tag = entry.tag,
                bracketCount = entry.bracketCount,
                numericWeight = entry.numericWeight,
                enabled = entry.enabled,
                forceArtistPrefix = entry.forceArtistPrefix,
            )
        }.joinToString(", ")
    }

    fun formatWeight(weight: Double): String {
        // Avoid trailing .0 noise while keeping one decimal when needed.
        val asLong = weight.toLong()
        return if (weight == asLong.toDouble()) {
            asLong.toString()
        } else {
            String.format(java.util.Locale.US, "%.2f", weight)
                .trimEnd('0')
                .trimEnd('.')
        }
    }

    /**
     * Parse a simple NovelAI prompt string into tag entries.
     * Handles commas, brace/bracket nesting, and numeric `w::tag::` segments.
     * Not a full tokenizer — good enough for round-trip of app-generated prompts.
     */
    fun parsePrompt(prompt: String): List<TagEntry> {
        if (prompt.isBlank()) return emptyList()
        val parts = splitTopLevel(prompt)
        return parts.mapNotNull { parseSingle(it.trim()) }.filter { it.tag.isNotBlank() }
    }

    private fun splitTopLevel(prompt: String): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var i = 0
        var depth = 0
        while (i < prompt.length) {
            val c = prompt[i]
            // Numeric emphasis blocks may contain commas; treat :: ... :: as atomic.
            if (c == ':' && i + 1 < prompt.length && prompt[i + 1] == ':') {
                // find matching closing ::
                current.append("::")
                i += 2
                while (i < prompt.length) {
                    if (prompt[i] == ':' && i + 1 < prompt.length && prompt[i + 1] == ':') {
                        current.append("::")
                        i += 2
                        break
                    }
                    current.append(prompt[i])
                    i++
                }
                continue
            }
            when (c) {
                '{', '[' -> {
                    depth++
                    current.append(c)
                }
                '}', ']' -> {
                    depth = (depth - 1).coerceAtLeast(0)
                    current.append(c)
                }
                ',' -> {
                    if (depth == 0) {
                        result += current.toString()
                        current.clear()
                    } else {
                        current.append(c)
                    }
                }
                else -> current.append(c)
            }
            i++
        }
        if (current.isNotEmpty()) result += current.toString()
        return result
    }

    private fun parseSingle(raw: String): TagEntry? {
        if (raw.isBlank()) return null

        // Numeric: 1.5::tag:: or -1.5::tag::
        val numeric = Regex("""^\s*(-?\d+(?:\.\d+)?)\s*::\s*(.*?)\s*::\s*$""").matchEntire(raw)
        if (numeric != null) {
            val weight = numeric.groupValues[1].toDoubleOrNull()
            val tag = numeric.groupValues[2].trim()
            return TagEntry(tag = tag, numericWeight = weight, bracketCount = 0)
        }

        var text = raw.trim()
        var strengthen = 0
        var weaken = 0
        while (text.startsWith('{') && text.endsWith('}') && text.length >= 2) {
            text = text.substring(1, text.length - 1).trim()
            strengthen++
        }
        while (text.startsWith('[') && text.endsWith(']') && text.length >= 2) {
            text = text.substring(1, text.length - 1).trim()
            weaken++
        }
        val bracket = strengthen - weaken
        val forceArtist = text.startsWith("artist:", ignoreCase = true)
        return TagEntry(
            tag = text,
            bracketCount = bracket,
            forceArtistPrefix = forceArtist && text.startsWith("artist:", ignoreCase = true),
        )
    }
}

data class TagEntry(
    val id: String = java.util.UUID.randomUUID().toString(),
    val tag: String,
    /** Positive = `{}` nests, negative = `[]` nests. Ignored when [numericWeight] is set. */
    val bracketCount: Int = 0,
    val numericWeight: Double? = null,
    val enabled: Boolean = true,
    val forceArtistPrefix: Boolean = false,
)
