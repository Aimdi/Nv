package dev.naicompanion.app.data.catalog

import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteQuery
import dev.naicompanion.app.core.prompt.ArtistStrength

enum class CatalogSort(val label: String) {
    /**
     * Confidence-adjusted nax.moe V4.5 community score. This is the useful default for style
     * discovery: Danbooru volume alone surfaces weak, overfitted tags.
     */
    STRENGTH_DESC("Style pull on V4.5"),
    POST_COUNT_DESC("Most used (Danbooru)"),
    NAME_ASC("Name (A-Z)"),
    UNIQUENESS_DESC("Most distinctive"),
    RANDOM("Shuffle"),
}

/** Minimum community strength the browser or suggestions should show. */
enum class StrengthFilter(val label: String) {
    ANY("Any strength"),
    RATED("Has V4.5 rating"),
    SOLID_PLUS("Solid or stronger"),
    STRONG_ONLY("Strong only"),
    HIDE_WEAK("Hide weak"),
}

/** Everything the browser can ask of the catalog, in a form that is cheap to compare and test. */
data class CatalogQuery(
    val text: String = "",
    val sources: Set<String> = emptySet(),
    val kinds: Set<String> = emptySet(),
    val requirePreview: Boolean = false,
    val minPostCount: Int = 0,
    /** Extra floor on nax.moe vote count, on top of [strengthFilter]'s own thresholds. */
    val minNaxVotes: Int = 0,
    val strengthFilter: StrengthFilter = StrengthFilter.ANY,
    val sort: CatalogSort = CatalogSort.STRENGTH_DESC,
    val limit: Int = DEFAULT_LIMIT,
) {
    companion object {
        /**
         * Comfortably above the bundled catalog's ~17k rows, so every sort order can reach every
         * artist; a lower cap would make the tail unreachable when sorting by name. The rows are
         * small and the grid only composes what is visible, so holding them all costs a few MB.
         * This is a backstop against an unexpectedly large catalog, not pagination.
         */
        const val DEFAULT_LIMIT = 50_000
    }
}

/**
 * Builds the SQL for a [CatalogQuery].
 *
 * Text search deliberately combines two strategies: an FTS4 `MATCH` for fast, relevance-ordered
 * prefix matching across name/aliases, plus a `LIKE '%needle%'` so that mid-word searches (typing
 * "waffles" to find "ainiwaffles") still hit. At catalog size the `LIKE` scan is inexpensive.
 */
object CatalogQueryBuilder {

    private const val LIKE_ESCAPE = '\\'

    /** Separator used by the `sources` column; see [ArtistEntity.sources]. */
    const val SOURCE_DELIMITER = "|"

    fun build(query: CatalogQuery): SupportSQLiteQuery {
        val args = mutableListOf<Any>()
        val where = mutableListOf<String>()

        val trimmedText = query.text.trim()
        if (trimmedText.isNotEmpty()) {
            val clauses = mutableListOf<String>()
            toFtsMatchQuery(trimmedText)?.let { match ->
                clauses += "id IN (SELECT docid FROM artists_fts WHERE artists_fts MATCH ?)"
                args += match
            }
            val contains = "%${escapeLike(trimmedText)}%"
            clauses += "name LIKE ? ESCAPE '$LIKE_ESCAPE'"
            args += contains
            clauses += "display_name LIKE ? ESCAPE '$LIKE_ESCAPE'"
            args += contains
            where += clauses.joinToString(" OR ", prefix = "(", postfix = ")")
        }

        if (query.sources.isNotEmpty()) {
            val sorted = query.sources.sorted()
            where += sorted.joinToString(" OR ", prefix = "(", postfix = ")") {
                "sources LIKE ? ESCAPE '$LIKE_ESCAPE'"
            }
            args.addAll(sorted.map { "%${SOURCE_DELIMITER}${escapeLike(it)}${SOURCE_DELIMITER}%" })
        }

        if (query.kinds.isNotEmpty()) {
            where += "kind IN (${placeholders(query.kinds.size)})"
            args.addAll(query.kinds.sorted())
        }

        if (query.requirePreview) {
            where += "preview IS NOT NULL AND preview != ''"
        }

        if (query.minPostCount > 0) {
            where += "post_count >= ?"
            args += query.minPostCount
        }

        if (query.minNaxVotes > 0) {
            where += "nax_votes IS NOT NULL AND nax_votes >= ?"
            args += query.minNaxVotes
        }

        when (query.strengthFilter) {
            StrengthFilter.ANY -> Unit
            StrengthFilter.RATED -> {
                where += "nax_votes IS NOT NULL AND nax_votes >= ?"
                args += ArtistStrength.MIN_VOTES
            }
            StrengthFilter.SOLID_PLUS -> {
                where += "nax_votes IS NOT NULL AND nax_votes >= ? AND nax_score >= ?"
                args += ArtistStrength.MIN_VOTES
                args += 5
            }
            StrengthFilter.STRONG_ONLY -> {
                where += "nax_votes IS NOT NULL AND nax_votes >= ? AND nax_score >= ?"
                args += ArtistStrength.MIN_VOTES
                args += 15
            }
            StrengthFilter.HIDE_WEAK -> {
                where += "(nax_votes IS NULL OR nax_votes < ? OR nax_score > ?)"
                args += ArtistStrength.MIN_VOTES
                args += -3
            }
        }

        val sql = buildString {
            append("SELECT * FROM artists")
            if (where.isNotEmpty()) {
                append(" WHERE ")
                append(where.joinToString(" AND "))
            }
            append(" ORDER BY ")
            if (trimmedText.isNotEmpty()) {
                // Exact hits first, then prefix hits, then everything else.
                append("CASE WHEN name = ? THEN 0 WHEN display_name = ? THEN 0 ")
                append("WHEN name LIKE ? ESCAPE '$LIKE_ESCAPE' THEN 1 ")
                append("WHEN display_name LIKE ? ESCAPE '$LIKE_ESCAPE' THEN 1 ELSE 2 END, ")
                val lowered = trimmedText.lowercase()
                args += lowered
                args += lowered
                val prefix = "${escapeLike(trimmedText)}%"
                args += prefix
                args += prefix
            }
            append(sortExpression(query.sort))
            if (query.limit > 0) {
                append(" LIMIT ?")
                args += query.limit
            }
        }

        return SimpleSQLiteQuery(sql, args.toTypedArray())
    }

    private fun sortExpression(sort: CatalogSort): String = when (sort) {
        // Confidence-adjusted: score/(votes+10). Unrated artists sink below anything with votes.
        CatalogSort.STRENGTH_DESC ->
            // Bayesian shrinkage: score * votes / (votes + prior). Matches strengthRankScore().
            "nax_votes IS NULL OR nax_votes = 0, " +
                "CAST(nax_score AS REAL) * nax_votes / (nax_votes + 10.0) DESC, " +
                "nax_score DESC, post_count DESC"
        CatalogSort.POST_COUNT_DESC -> "post_count DESC, display_name COLLATE NOCASE ASC"
        CatalogSort.NAME_ASC -> "display_name COLLATE NOCASE ASC"
        // SQLite on older Android lacks NULLS LAST, so sort the null flag explicitly.
        CatalogSort.UNIQUENESS_DESC ->
            "uniqueness IS NULL, uniqueness DESC, post_count DESC"
        CatalogSort.RANDOM -> "RANDOM()"
    }

    /**
     * Turns free text into an FTS4 MATCH expression for search-as-you-type.
     *
     * Tokens are emitted bare with a trailing `*`. The quoted form (`"wlo"*`) must not be used:
     * unlike FTS5, FTS4 treats that as an exact phrase and silently ignores the prefix operator,
     * so every partially typed search would return nothing. Splitting on non-alphanumerics first
     * means the tokens cannot contain FTS syntax, and the trailing `*` also stops a token like
     * `OR` or `NEAR` from being read as an operator.
     */
    fun toFtsMatchQuery(input: String): String? {
        val tokens = input.split(TOKEN_SPLIT).filter { it.isNotBlank() }
        if (tokens.isEmpty()) return null
        return tokens.joinToString(" ") { "$it*" }
    }

    /** Escapes the `LIKE` wildcards so a literal `%` or `_` in a tag is searched for verbatim. */
    fun escapeLike(input: String): String = buildString(input.length) {
        for (ch in input) {
            if (ch == '%' || ch == '_' || ch == LIKE_ESCAPE) append(LIKE_ESCAPE)
            append(ch)
        }
    }

    private fun placeholders(count: Int): String = List(count) { "?" }.joinToString(", ")

    private val TOKEN_SPLIT = Regex("[^\\p{L}\\p{N}]+")
}
