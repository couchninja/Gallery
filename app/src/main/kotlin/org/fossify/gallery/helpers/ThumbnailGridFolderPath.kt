package org.fossify.gallery.helpers

import org.fossify.commons.helpers.FAVORITES
import org.fossify.gallery.models.ThumbnailFolderPath
import org.fossify.gallery.models.ThumbnailItem

fun shouldShowFolderPathInGrid(showAll: Boolean, path: String): Boolean {
    return !showAll && path.isNotEmpty() && path != FAVORITES && path != RECYCLE_BIN
}

fun ArrayList<ThumbnailItem>.withFolderPathHeader(
    showAll: Boolean,
    path: String,
    humanizedPath: String,
): ArrayList<ThumbnailItem> {
    if (!shouldShowFolderPathInGrid(showAll, path)) {
        return this
    }
    return ArrayList<ThumbnailItem>(size + 1).apply {
        add(ThumbnailFolderPath(humanizedPath, path))
        addAll(this@withFolderPathHeader)
    }
}
