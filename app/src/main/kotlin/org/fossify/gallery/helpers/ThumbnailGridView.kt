package org.fossify.gallery.helpers

import android.content.Context
import androidx.annotation.IdRes
import androidx.annotation.StringRes
import org.fossify.gallery.R
import org.fossify.gallery.extensions.config
import org.fossify.gallery.models.Medium
import org.fossify.gallery.models.ThumbnailItem
import java.util.Locale

enum class ThumbnailGridView(
    val storageId: String,
    @IdRes val menuItemId: Int,
    @StringRes val labelRes: Int,
    val iconRes: Int,
) {
    ALL(
        storageId = "all",
        menuItemId = R.id.thumbnail_view_all,
        labelRes = R.string.thumbnail_grid_view_all,
        iconRes = org.fossify.commons.R.drawable.ic_apps_vector,
    ),
    UNRATED(
        storageId = "unrated",
        menuItemId = R.id.thumbnail_view_unrated,
        labelRes = R.string.thumbnail_grid_view_unrated,
        iconRes = org.fossify.commons.R.drawable.ic_star_outline_vector,
    ),
    VIDEOS(
        storageId = "videos",
        menuItemId = R.id.thumbnail_view_videos,
        labelRes = R.string.thumbnail_grid_view_videos,
        iconRes = org.fossify.commons.R.drawable.ic_play_vector,
    ),
    TOP_RATED(
        storageId = "top_rated",
        menuItemId = R.id.thumbnail_view_top_rated,
        labelRes = R.string.thumbnail_grid_view_top_rated,
        iconRes = org.fossify.commons.R.drawable.ic_star_vector,
    ),
    TAGS(
        storageId = "tags",
        menuItemId = R.id.thumbnail_view_tags,
        labelRes = R.string.thumbnail_grid_view_tags,
        iconRes = R.drawable.ic_xmp_tags_vector,
    ),
    CARDS(
        storageId = "cards",
        menuItemId = R.id.thumbnail_view_cards,
        labelRes = R.string.thumbnail_grid_view_cards,
        iconRes = R.drawable.ic_cards_vector,
    ),
    ;

    private val cardTagNamesLowercase = setOf("klantenkaart", "card")

    fun matchesMedium(medium: Medium, stickyUnratedPaths: Set<String>?, selectedTag: String?): Boolean {
        return when (this) {
            ALL -> true
            UNRATED -> {
                if (stickyUnratedPaths != null) {
                    medium.path in stickyUnratedPaths
                } else {
                    medium.isJpeg() && medium.exifRating == 0
                }
            }
            VIDEOS -> medium.isVideo()
            TOP_RATED -> medium.isJpeg() && medium.exifRating >= 4
            TAGS -> {
                val tag = selectedTag?.takeIf { it.isNotEmpty() } ?: return false
                medium.path in XmpTagIndex.getPathsWithTag(tag)
            }
            CARDS -> mediumHasCardTag(medium)
        }
    }

    private fun mediumHasCardTag(medium: Medium): Boolean {
        return XmpTagIndex.getTags(medium.path).any { tag ->
            tag.lowercase(Locale.getDefault()) in cardTagNamesLowercase
        }
    }

    companion object {
        val defaultOrder: List<ThumbnailGridView> = entries

        fun fromStorageId(id: String): ThumbnailGridView {
            return entries.firstOrNull { it.storageId == id } ?: ALL
        }

        fun fromMenuItemId(@IdRes itemId: Int): ThumbnailGridView? {
            return entries.firstOrNull { it.menuItemId == itemId }
        }
    }
}

object ThumbnailGridViewConfig {
    private const val ORDER_SEPARATOR = "|"

    fun getVisibleViewsInOrder(context: Context): List<ThumbnailGridView> {
        val config = context.config
        val hidden = config.hiddenThumbnailGridViews
        val order = config.thumbnailGridViewOrder
            .split(ORDER_SEPARATOR)
            .mapNotNull { token ->
                ThumbnailGridView.entries.firstOrNull { it.storageId == token }
            }
            .ifEmpty { ThumbnailGridView.defaultOrder }

        return order.filter { it.storageId !in hidden }
    }

    fun saveVisibleOrder(context: Context, visibleInOrder: List<ThumbnailGridView>, hidden: Set<ThumbnailGridView>) {
        context.config.thumbnailGridViewOrder = visibleInOrder.joinToString(ORDER_SEPARATOR) { it.storageId }
        context.config.hiddenThumbnailGridViews = hidden.joinToString(ORDER_SEPARATOR) { it.storageId }
    }

    fun getSelectedView(context: Context, folderKey: String): ThumbnailGridView {
        val view = ThumbnailGridView.fromStorageId(context.config.getThumbnailGridView(folderKey))
        // Tags view needs a session tag choice and is never restored from settings.
        return if (view == ThumbnailGridView.TAGS) ThumbnailGridView.ALL else view
    }

    fun setSelectedView(context: Context, folderKey: String, view: ThumbnailGridView) {
        context.config.setThumbnailGridView(folderKey, view.storageId)
    }

    fun resetAllPhotosGridToAll(context: Context) {
        setSelectedView(context, SHOW_ALL, ThumbnailGridView.ALL)
    }
}

fun filterMediaForThumbnailGridView(
    rawMedia: List<ThumbnailItem>,
    view: ThumbnailGridView,
    stickyUnratedPaths: Set<String>?,
    selectedTag: String? = null,
): ArrayList<Medium> {
    return rawMedia.filterIsInstance<Medium>()
        .filter { view.matchesMedium(it, stickyUnratedPaths, selectedTag) } as ArrayList
}

fun folderKeyForThumbnailGridViews(path: String, showAll: Boolean): String {
    return if (showAll || path.isEmpty()) {
        SHOW_ALL
    } else {
        path.lowercase(Locale.getDefault())
    }
}
