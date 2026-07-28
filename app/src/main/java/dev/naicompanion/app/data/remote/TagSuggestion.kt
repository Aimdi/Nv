package dev.naicompanion.app.data.remote

/**
 * Unified suggestion row shown in the builder search list.
 *
 * Local catalog hits appear first; live Danbooru results fill in tags the seed DB does not have.
 */
data class TagSuggestion(
    val name: String,
    val displayName: String,
    val category: Int,
    val postCount: Int,
    val source: Source,
    val kindStorage: String,
) {
    enum class Source { LOCAL, DANBOORU }

    val isArtist: Boolean
        get() = category == DanbooruAutocompleteItem.CATEGORY_ARTIST ||
            kindStorage.equals("ARTIST", ignoreCase = true)

    companion object {
        fun fromLocal(
            name: String,
            displayName: String,
            kind: String,
            postCount: Int,
        ): TagSuggestion = TagSuggestion(
            name = name,
            displayName = displayName,
            category = categoryFromKind(kind),
            postCount = postCount,
            source = Source.LOCAL,
            kindStorage = kind,
        )

        fun fromDanbooru(item: DanbooruAutocompleteItem): TagSuggestion = TagSuggestion(
            name = item.tagName,
            displayName = item.tagName.replace('_', ' '),
            category = item.category,
            postCount = item.postCount,
            source = Source.DANBOORU,
            kindStorage = kindFromCategory(item.category),
        )

        fun categoryFromKind(kind: String): Int = when (kind.uppercase()) {
            "ARTIST" -> DanbooruAutocompleteItem.CATEGORY_ARTIST
            "COPYRIGHT" -> DanbooruAutocompleteItem.CATEGORY_COPYRIGHT
            "CHARACTER" -> DanbooruAutocompleteItem.CATEGORY_CHARACTER
            "META" -> DanbooruAutocompleteItem.CATEGORY_META
            else -> DanbooruAutocompleteItem.CATEGORY_GENERAL
        }

        fun kindFromCategory(category: Int): String = when (category) {
            DanbooruAutocompleteItem.CATEGORY_ARTIST -> "ARTIST"
            DanbooruAutocompleteItem.CATEGORY_COPYRIGHT -> "COPYRIGHT"
            DanbooruAutocompleteItem.CATEGORY_CHARACTER -> "CHARACTER"
            DanbooruAutocompleteItem.CATEGORY_META -> "META"
            else -> "GENERAL"
        }
    }
}
