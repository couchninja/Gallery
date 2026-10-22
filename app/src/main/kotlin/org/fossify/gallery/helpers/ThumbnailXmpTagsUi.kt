package org.fossify.gallery.helpers

import android.view.ViewGroup
import android.widget.ImageView
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.gallery.R

data class ThumbnailXmpTagIndicators(val showKeep: Boolean, val showOtherTags: Boolean)

fun thumbnailXmpTagIndicators(tags: List<String>): ThumbnailXmpTagIndicators {
    var showKeep = false
    var showOtherTags = false
    for (tag in tags) {
        if (tag.contains("keep", ignoreCase = true)) {
            showKeep = true
        } else {
            showOtherTags = true
        }
    }
    return ThumbnailXmpTagIndicators(showKeep, showOtherTags)
}

fun bindThumbnailXmpTagIndicators(container: ViewGroup?, path: String, isJpeg: Boolean) {
    if (container == null) {
        return
    }
    val keep = container.findViewById<ImageView>(R.id.xmp_tag_keep_indicator)
    val other = container.findViewById<ImageView>(R.id.xmp_tag_other_indicator)
    if (!isJpeg) {
        container.beVisibleIf(false)
        keep.beVisibleIf(false)
        other.beVisibleIf(false)
        return
    }
    val indicators = thumbnailXmpTagIndicators(XmpTagIndex.getTags(path))
    container.beVisibleIf(indicators.showKeep || indicators.showOtherTags)
    keep.beVisibleIf(indicators.showKeep)
    other.beVisibleIf(indicators.showOtherTags)
}
