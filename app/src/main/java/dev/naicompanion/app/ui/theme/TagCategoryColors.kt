package dev.naicompanion.app.ui.theme

import androidx.compose.ui.graphics.Color
import dev.naicompanion.app.data.remote.DanbooruAutocompleteItem

/** Danbooru category accent colors used in search suggestions. */
object TagCategoryColors {
    val General = Color(0xFF7A8494)
    val Artist = Color(0xFFC45C26)
    val Copyright = Color(0xFFA12FAD)
    val Character = Color(0xFF2F8F46)
    val Meta = Color(0xFFB8860B)
    val Unknown = Color(0xFF6B7280)

    fun forCategory(category: Int): Color = when (category) {
        DanbooruAutocompleteItem.CATEGORY_GENERAL -> General
        DanbooruAutocompleteItem.CATEGORY_ARTIST -> Artist
        DanbooruAutocompleteItem.CATEGORY_COPYRIGHT -> Copyright
        DanbooruAutocompleteItem.CATEGORY_CHARACTER -> Character
        DanbooruAutocompleteItem.CATEGORY_META -> Meta
        else -> Unknown
    }

    fun label(category: Int): String = when (category) {
        DanbooruAutocompleteItem.CATEGORY_GENERAL -> "general"
        DanbooruAutocompleteItem.CATEGORY_ARTIST -> "artist"
        DanbooruAutocompleteItem.CATEGORY_COPYRIGHT -> "copyright"
        DanbooruAutocompleteItem.CATEGORY_CHARACTER -> "character"
        DanbooruAutocompleteItem.CATEGORY_META -> "meta"
        else -> "tag"
    }
}
