package dev.naicompanion.app.data.catalog

import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteQuery

enum class CatalogSort(val label: String) {
    POST_COUNT_DESC("Most used"),
    NAME_ASC("Name (A-Z)"),
    UNIQUENESS_DESC("Most distinctive"),
    RANDOM("Shuffle"),
}

/** Everything the browser can ask of the catalog, in a form that is cheap to compare and test. */
data class CatalogQuery(
    val text: String = "",
    val sources: Set<String> = emptySet(),
    val kinds: Set<String> = emptySet(),
    val requirePreview: Boolean = false,
    val minPostCount: Int = 0,
    val sort: CatalogSort = CatalogSort.POST_COUNT_DESC,
    val limit: Int = DEFAULT_LIMIT,
) {
    companion object {
        /**
         * Enough rows to scroll through for a long time while keeping the in-memory list small.
         * The catalog is only ~16k rows, so this is a safety net rather than real pagination.
         */
        const val DEFAULT_LIMIT = 5_000
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
            where += "source IN (${placeholders(query.sources.size)})"
            args.addAll(query.sources.sorted())
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
        CatalogSort.POST_COUNT_DESC -> "post_count DESC, display_name COLLATE NOCASE ASC"
        CatalogSort.NAME_ASC -> "display_name COLLATE NOCASE ASC"
        // SQLite on older Android lacks NULLS LAST, so sort the null flag explicitly.
        CatalogSort.UNIQUENESS_DESC ->
            "uniqueness IS NULL, uniqueness DESC, post_count DESC"
        CatalogSort.RANDOM -> "RANDOM()"
    }

    /**
     * Turns free text into an FTS4 MATCH expression, quoting each token so punctuation cannot be
     * read as FTS syntax and appending `*` for search-as-you-type prefix matching.
     */
    fun toFtsMatchQuery(input: String): String? {
        val tokens = input.split(TOKEN_SPLIT).filter { it.isNotBlank() }
        if (tokens.isEmpty()) return null
        return tokens.joinToString(" ") { "\"${it.replace("\"", "")}\"*" }
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
