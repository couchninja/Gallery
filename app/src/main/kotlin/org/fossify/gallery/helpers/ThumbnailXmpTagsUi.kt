package org.fossify.gallery.helpers

import android.view.ViewGroup
import android.widget.ImageView
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.gallery.R

data class ThumbnailXmpTagIndicators(
    val showCard: Boolean,
    val showPrivate: Boolean,
    val showFavorite: Boolean,
    val showKeep: Boolean,
    val showOtherTags: Boolean,
)

fun thumbnailXmpTagIndicators(tags: List<String>): ThumbnailXmpTagIndicators {
    var showCard = false
    var showPrivate = false
    var showFavorite = false
    var showKeep = false
    var showOtherTags = false
    for (tag in tags) {
        when {
            XmpTagDisplay.isCardTag(tag) -> showCard = true
            XmpTagDisplay.isPrivateTag(tag) -> showPrivate = true
            XmpTagDisplay.isFavoriteTag(tag) -> showFavorite = true
            XmpTagDisplay.isKeepTag(tag) -> showKeep = true
            else -> showOtherTags = true
        }
    }
    return ThumbnailXmpTagIndicators(showCard, showPrivate, showFavorite, showKeep, showOtherTags)
}

fun bindThumbnailXmpTagIndicators(container: ViewGroup?, path: String, isJpeg: Boolean) {
    if (container == null) {
        return
    }
    val card = container.findViewById<ImageView>(R.id.xmp_tag_card_indicator)
    val privateTag = container.findViewById<ImageView>(R.id.xmp_tag_private_indicator)
    val favorite = container.findViewById<ImageView>(R.id.xmp_tag_favorite_indicator)
    val keep = container.findViewById<ImageView>(R.id.xmp_tag_keep_indicator)
    val other = container.findViewById<ImageView>(R.id.xmp_tag_other_indicator)
    if (!isJpeg) {
        container.beVisibleIf(false)
        card.beVisibleIf(false)
        privateTag.beVisibleIf(false)
        favorite.beVisibleIf(false)
        keep.beVisibleIf(false)
        other.beVisibleIf(false)
        return
    }
    val indicators = thumbnailXmpTagIndicators(XmpTagIndex.getTags(path))
    container.beVisibleIf(
        indicators.showCard ||
            indicators.showPrivate ||
            indicators.showFavorite ||
            indicators.showKeep ||
            indicators.showOtherTags,
    )
    card.beVisibleIf(indicators.showCard)
    privateTag.beVisibleIf(indicators.showPrivate)
    favorite.beVisibleIf(indicators.showFavorite)
    keep.beVisibleIf(indicators.showKeep)
    other.beVisibleIf(indicators.showOtherTags)
}
