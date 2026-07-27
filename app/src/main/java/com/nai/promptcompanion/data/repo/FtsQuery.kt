package com.nai.promptcompanion.data.repo

/**
 * Builds a safe FTS4 MATCH expression from free-form user input.
 * Each whitespace-separated term becomes a quoted prefix term, so input like
 * `mizuki hito` matches "mizuki_hitoshi" without FTS syntax errors from
 * special characters (parens, quotes, colons).
 */
fun ftsQuery(raw: String): String? {
    val terms = raw.trim()
        .split(Regex("\\s+"))
        .map { it.replace("\"", "") }
        .filter { it.isNotBlank() }
    if (terms.isEmpty()) return null
    return terms.joinToString(" ") { "\"$it\"*" }
}
