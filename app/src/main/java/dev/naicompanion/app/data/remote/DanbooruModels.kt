package dev.naicompanion.app.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Danbooru autocomplete row.
 *
 * Category codes: 0 general, 1 artist, 3 copyright, 4 character, 5 meta (no category 2).
 */
@Serializable
data class DanbooruAutocompleteItem(
    val label: String? = null,
    val value: String,
    val category: Int = 0,
    @SerialName("post_count") val postCount: Int = 0,
    val antecedent: String? = null,
) {
    val isArtist: Boolean get() = category == CATEGORY_ARTIST

    /** Prefer the resolved tag name; fall back to the raw query match. */
    val tagName: String get() = value.trim()

    companion object {
        const val CATEGORY_GENERAL = 0
        const val CATEGORY_ARTIST = 1
        const val CATEGORY_COPYRIGHT = 3
        const val CATEGORY_CHARACTER = 4
        const val CATEGORY_META = 5
    }
}
