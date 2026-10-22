package org.fossify.gallery.helpers

import org.fossify.gallery.models.Medium
import org.fossify.gallery.models.ThumbnailItem
import java.util.Locale

/** XMP-tagged `private` media; shown when hidden media is shown ([Config.shouldShowHidden]). */
object PrivateTaggedMedia {
    private val privateTagLower = "private"

    fun hasPrivateTag(path: String): Boolean =
        XmpTagIndex.getTags(path).any { it.lowercase(Locale.getDefault()) == privateTagLower }

    fun hasPrivateTag(medium: Medium): Boolean = hasPrivateTag(medium.path)

    fun filter(items: List<ThumbnailItem>, showPrivateTagged: Boolean): List<ThumbnailItem> {
        if (showPrivateTagged) {
            return items
        }
        return items.filter { it !is Medium || !hasPrivateTag(it) }
    }

    fun filterMedia(media: List<Medium>, showPrivateTagged: Boolean): List<Medium> {
        if (showPrivateTagged) {
            return media
        }
        return media.filter { !hasPrivateTag(it) }
    }
}
