package org.fossify.gallery.extensions

import android.app.Activity
import org.fossify.gallery.dialogs.MediaPropertiesDialog

fun Activity.showMediaProperties(path: String, countHiddenItems: Boolean = false) {
    MediaPropertiesDialog(this, path, countHiddenItems)
}

fun Activity.showMediaProperties(paths: List<String>, countHiddenItems: Boolean = false) {
    MediaPropertiesDialog(this, paths, countHiddenItems)
}
