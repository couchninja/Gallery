package org.fossify.gallery.helpers

import androidx.annotation.DrawableRes
import java.util.Locale
import org.fossify.gallery.R

object XmpTagDisplay {
    val cardTagNamesLowercase = setOf("klantenkaart", "card")
    private const val PRIVATE_TAG_LOWER = "private"
    private const val FAVORITE_TAG_LOWER = "favorite"

    /** Canonical dc:subject value written when toggling favorite from the viewer. */
    const val FAVORITE_METADATA_TAG = "Favorite"

    fun isPrivateTag(tag: String): Boolean =
        tag.lowercase(Locale.getDefault()) == PRIVATE_TAG_LOWER

    fun isFavoriteTag(tag: String): Boolean =
        tag.lowercase(Locale.getDefault()) == FAVORITE_TAG_LOWER

    fun isKeepTag(tag: String): Boolean = tag.contains("keep", ignoreCase = true)

    fun isCardTag(tag: String): Boolean = tag.lowercase(Locale.getDefault()) in cardTagNamesLowercase

    @DrawableRes
    fun listIconResForTag(tag: String): Int = when {
        isPrivateTag(tag) -> R.drawable.ic_xmp_tag_private_vector
        isFavoriteTag(tag) -> R.drawable.ic_xmp_tag_favorite_vector
        isKeepTag(tag) -> R.drawable.ic_clock_vector
        isCardTag(tag) -> R.drawable.ic_cards_vector
        else -> R.drawable.ic_xmp_tags_vector
    }

    fun mediumHasCardTag(tags: List<String>): Boolean = tags.any { isCardTag(it) }

    fun mediumHasFavoriteTag(tags: List<String>): Boolean = tags.any { isFavoriteTag(it) }
}
