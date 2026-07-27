package com.nai.promptcompanion.novelai

import kotlinx.serialization.Serializable
import java.util.Locale
import kotlin.math.abs

/**
 * One tag inside a combo being built.
 *
 * @param tag raw tag text. Underscores are converted to spaces at render time
 *   (Danbooru-style tags like `looking_at_viewer` must be written with spaces
 *   in NovelAI prompts; this noticeably affects results).
 * @param bracketCount > 0 wraps the tag in `{ }` (x1.05 attention per brace),
 *   < 0 wraps in `[ ]` (/1.05 per bracket).
 * @param numericWeight when set (and != 1.0) renders as `w::tag::`. Values in
 *   (0.0, 1.0) weaken; negative values require NovelAI V4.5+.
 *   `::` also closes any open brackets, which is why numeric emphasis and
 *   brace nesting are treated as mutually exclusive here.
 * @param enabled disabled entries are skipped when rendering.
 */
@Serializable
data class TagEntry(
    val tag: String,
    val bracketCount: Int = 0,
    val numericWeight: Double? = null,
    val enabled: Boolean = true,
    /** Stable identity for list animations/drag-reorder; assigned on creation. */
    val id: String = "",
) {
    val effectiveWeight: Double
        get() = numericWeight?.takeIf { it != 1.0 }
            ?: (1.05).powTimes(bracketCount)

    fun withId(newId: String = java.util.UUID.randomUUID().toString()): TagEntry =
        copy(id = newId)

    companion object {
        /** Entries from older drafts/backups may lack ids. */
        fun ensureIds(entries: List<TagEntry>): List<TagEntry> =
            entries.map { if (it.id.isEmpty()) it.withId() else it }
    }
}

private fun Double.powTimes(n: Int): Double {
    var result = 1.0
    repeat(abs(n)) { result *= this }
    return if (n < 0) 1.0 / result else result
}

object NovelaiSyntax {

    const val NOVELAI_URL = "https://novelai.net/image"

    fun formatWeight(weight: Double): String {
        val s = String.format(Locale.US, "%.2f", weight)
            .trimEnd('0')
            .trimEnd('.')
        return if (s == "-0") "0" else s
    }

    /** Danbooru tags use underscores; NovelAI wants spaces. */
    fun normalizeTagText(tag: String): String =
        tag.trim().replace('_', ' ')

    fun renderEntry(entry: TagEntry): String {
        if (!entry.enabled) return ""
        val base = normalizeTagText(entry.tag)
        if (base.isEmpty()) return ""

        val weight = entry.numericWeight
        if (weight != null && weight != 1.0) {
            return "${formatWeight(weight)}::$base::"
        }
        return when {
            entry.bracketCount > 0 ->
                "{".repeat(entry.bracketCount) + base + "}".repeat(entry.bracketCount)
            entry.bracketCount < 0 ->
                "[".repeat(-entry.bracketCount) + base + "]".repeat(-entry.bracketCount)
            else -> base
        }
    }

    fun render(entries: List<TagEntry>): String =
        entries.map { renderEntry(it) }
            .filter { it.isNotEmpty() }
            .joinToString(", ")

    /**
     * Parses NovelAI prompt text back into entries so an existing prompt can be
     * loaded into the builder. Handles brace/bracket nesting, numeric emphasis
     * (including commas inside `w::...::` sections), and top-level comma splits.
     */
    fun parse(input: String): List<TagEntry> =
        splitTopLevel(input).mapNotNull { parseToken(it) }

    private fun splitTopLevel(input: String): List<String> {
        val tokens = mutableListOf<String>()
        val current = StringBuilder()
        var i = 0
        var inNumeric = false
        while (i < input.length) {
            val c = input[i]
            if (!inNumeric && isNumericSectionStart(input, i)) {
                // Consume the whole `w::` opener so it isn't mistaken for a closer.
                inNumeric = true
                while (i < input.length &&
                    !(input[i] == ':' && i + 1 < input.length && input[i + 1] == ':')
                ) {
                    current.append(input[i])
                    i++
                }
                if (i + 1 < input.length) {
                    current.append("::")
                    i += 2
                }
            } else if (inNumeric && c == ':' && i + 1 < input.length && input[i + 1] == ':') {
                inNumeric = false
                current.append("::")
                i += 2
            } else if (c == ',' && !inNumeric) {
                tokens.add(current.toString())
                current.clear()
                i++
            } else {
                current.append(c)
                i++
            }
        }
        tokens.add(current.toString())
        return tokens
    }

    /** A numeric section starts with [sign] digits [.digits] followed by `::`. */
    private fun isNumericSectionStart(input: String, from: Int): Boolean {
        var i = from
        if (i < input.length && (input[i] == '-' || input[i] == '+')) i++
        val digitStart = i
        while (i < input.length && input[i].isDigit()) i++
        if (i == digitStart) return false
        if (i < input.length && input[i] == '.') {
            i++
            val fracStart = i
            while (i < input.length && input[i].isDigit()) i++
            if (i == fracStart) return false
        }
        return i + 1 < input.length && input[i] == ':' && input[i + 1] == ':'
    }

    private fun parseToken(raw: String): TagEntry? {
        var text = raw.trim()
        if (text.isEmpty()) return null

        var braces = 0
        var brackets = 0
        while (text.startsWith("{")) { braces++; text = text.removePrefix("{") }
        while (text.startsWith("[")) { brackets++; text = text.removePrefix("[") }
        repeat(braces) {
            if (text.endsWith("}")) text = text.removeSuffix("}")
        }
        repeat(brackets) {
            if (text.endsWith("]")) text = text.removeSuffix("]")
        }
        text = text.trim()
        if (text.isEmpty()) return null

        val numeric = NUMERIC_TOKEN.matchEntire(text)
        if (numeric != null) {
            val weight = numeric.groupValues[1].toDoubleOrNull() ?: return null
            val inner = numeric.groupValues[2].trim()
            if (inner.isEmpty()) return null
            return TagEntry(
                tag = inner,
                bracketCount = braces - brackets,
                numericWeight = weight,
            )
        }
        return TagEntry(tag = text, bracketCount = braces - brackets)
    }

    private val NUMERIC_TOKEN = Regex("""^(-?\d+(?:\.\d+)?)::(.*)::$""", RegexOption.DOT_MATCHES_ALL)
}
