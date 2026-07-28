package dev.naicompanion.app.core.prompt

import java.math.BigDecimal
import java.math.RoundingMode
import java.util.UUID

/**
 * Categories that change how a tag is emitted. Only [ARTIST] currently alters the rendered text
 * (via the `artist:` prefix on V4+), the rest are carried through for filtering and display.
 */
enum class TagKind {
    GENERAL,
    ARTIST,
    CHARACTER,
    COPYRIGHT,
    META;

    companion object {
        fun fromStorage(value: String?): TagKind =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: GENERAL
    }
}

/** NovelAI image model generations that differ in accepted prompt syntax. */
enum class NovelAiModel(val label: String) {
    V3("NAI Diffusion V3"),
    V4("NAI Diffusion V4"),
    V4_5("NAI Diffusion V4.5");

    /** V4 introduced the `artist:` prefix for artist tags. */
    val supportsArtistPrefix: Boolean get() = this != V3

    /** Negative numerical emphasis (`-1.5::tag::`) landed in V4.5. */
    val supportsNegativeWeights: Boolean get() = this == V4_5

    companion object {
        fun fromStorage(value: String?): NovelAiModel =
            entries.firstOrNull { it.name == value } ?: V4_5
    }
}

/**
 * A single tag inside a combo.
 *
 * A tag carries two independent emphasis controls because NovelAI accepts both and they compose:
 * [bracketCount] emits nested `{}`/`[]` (each level is a x1.05 / /1.05 attention step) and
 * [numericWeight] emits the `1.5::tag::` form. When both are set the brackets are nested inside
 * the numeric section, which round-trips losslessly through [NovelAiPromptParser].
 */
data class TagEntry(
    val id: String = UUID.randomUUID().toString(),
    val tag: String,
    val kind: TagKind = TagKind.GENERAL,
    val bracketCount: Int = 0,
    val numericWeight: Double? = null,
    val enabled: Boolean = true,
) {
    /**
     * Approximate attention multiplier, used for the UI readout. Bracket steps are x1.05 per
     * level; an explicit numeric weight replaces the bracket contribution only in the sense that
     * NovelAI multiplies the two, so we multiply here too.
     */
    val effectiveWeight: Double
        get() {
            val bracketFactor = Math.pow(BRACKET_STEP, bracketCount.toDouble())
            return (numericWeight ?: 1.0) * bracketFactor
        }

    companion object {
        const val BRACKET_STEP = 1.05

        /** Bracket nesting beyond this is almost never useful and makes prompts unreadable. */
        const val MAX_BRACKET_COUNT = 10

        const val MIN_NUMERIC_WEIGHT = -5.0
        const val MAX_NUMERIC_WEIGHT = 5.0
    }
}

/** An ordered, named collection of [TagEntry] values. Order matters: earlier tags bind harder. */
data class Combo(
    val id: Long = 0L,
    val name: String = "",
    val entries: List<TagEntry> = emptyList(),
    val isFavorite: Boolean = false,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
)

/** Knobs that control how a combo is turned into a NovelAI prompt string. */
data class RenderOptions(
    val model: NovelAiModel = NovelAiModel.V4_5,
    /**
     * Danbooru tags are stored with underscores but NovelAI responds noticeably differently to
     * `blue_eyes` versus `blue eyes`, so spaces are the default.
     */
    val underscoresToSpaces: Boolean = true,
    val artistPrefix: Boolean = true,
    val separator: String = ", ",
) {
    companion object {
        val Default = RenderOptions()
    }
}

/** Non-fatal problems found while rendering, surfaced in the builder UI. */
data class RenderWarning(val tagId: String?, val message: String)

data class RenderResult(val text: String, val warnings: List<RenderWarning>) {
    val isEmpty: Boolean get() = text.isEmpty()
}

/**
 * Formats a weight the way NovelAI's prompt box expects: at most two decimals, no trailing zeros
 * and no locale-specific decimal separator.
 */
fun formatWeight(weight: Double): String =
    BigDecimal(weight)
        .setScale(2, RoundingMode.HALF_UP)
        .stripTrailingZeros()
        .toPlainString()

/** Attention multiplier shown next to a tag, e.g. `x1.16`. */
fun formatMultiplier(weight: Double): String = "x" + formatWeight(weight)
