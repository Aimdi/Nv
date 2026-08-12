package dev.naicompanion.app.core.prompt

/**
 * How strongly an artist tag tends to pull NovelAI V4.5 toward its style.
 *
 * Rankings come from community votes on [nax.moe](https://nax.moe) V4.5 artist galleries
 * (constrained + loose prompts), not from Danbooru post counts. High-post artists are often
 * stylistically weak; the votes measure whether the tag actually changes the image.
 */
enum class ArtistStrength(val label: String, val shortLabel: String) {
    STRONG("Strong on V4.5", "Strong"),
    SOLID("Solid on V4.5", "Solid"),
    MIXED("Mixed on V4.5", "Mixed"),
    WEAK("Weak on V4.5", "Weak"),
    UNKNOWN("Untested on V4.5", "—");

    /**
     * Optional starter numeric emphasis when adding the tag.
     *
     * Strong/solid tags already pull style — leave them neutral. Mixed gets a light nudge.
     * Weak tags are not auto-boosted (forcing them often looks worse); the UI warns instead.
     */
    val recommendedWeight: Double?
        get() = when (this) {
            STRONG -> null
            SOLID -> null
            MIXED -> 1.1
            WEAK -> null
            UNKNOWN -> null
        }

    companion object {
        /** Minimum total votes before a score is treated as meaningful. */
        const val MIN_VOTES = 3

        fun fromVotes(score: Int?, votes: Int?): ArtistStrength {
            if (score == null || votes == null || votes < MIN_VOTES) return UNKNOWN
            return when {
                score >= 15 -> STRONG
                score >= 5 -> SOLID
                score <= -3 -> WEAK
                else -> MIXED
            }
        }
    }
}

/**
 * Confidence-adjusted score used for ranking.
 *
 * Bayesian shrinkage toward zero: `score * votes / (votes + prior)`.
 * Same raw score with more votes ranks higher; low-vote spikes shrink.
 */
fun strengthRankScore(score: Int?, votes: Int?): Double {
    if (score == null || votes == null || votes <= 0) return Double.NEGATIVE_INFINITY
    return score.toDouble() * votes / (votes + 10.0)
}
