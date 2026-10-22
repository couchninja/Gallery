package org.fossify.gallery.helpers

import org.fossify.gallery.models.Medium

/** Media rows whose files are no longer on disk (e.g. deleted in another app). */
internal fun mediaMissingOnDisk(
    media: Collection<Medium>,
    fileExists: (String) -> Boolean,
): List<Medium> = media.filter { !fileExists(it.path) }

/** Whether a MediaStore change for [path] should remove the cached row instead of indexing it. */
internal fun shouldRemoveCachedMediaForPath(path: String, fileExists: (String) -> Boolean): Boolean =
    !fileExists(path)
