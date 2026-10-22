package org.fossify.gallery.helpers

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import org.fossify.commons.helpers.isNougatMR1Plus
import org.fossify.gallery.R
import org.fossify.gallery.activities.MainActivity

object ThumbnailGridViewShortcuts {
    private val quickLaunchViews = listOf(
        ThumbnailGridView.UNRATED to R.drawable.ic_shortcut_unrated,
        ThumbnailGridView.CARDS to R.drawable.ic_shortcut_cards,
    )

    fun refresh(context: Context) {
        if (!isNougatMR1Plus()) {
            return
        }

        val manager = context.getSystemService(ShortcutManager::class.java) ?: return
        val shortcuts = quickLaunchViews.mapIndexed { index, (view, iconRes) ->
            ShortcutInfo.Builder(context, shortcutId(view))
                .setShortLabel(context.getString(view.labelRes))
                .setLongLabel(context.getString(view.labelRes))
                .setIcon(Icon.createWithResource(context, iconRes))
                .setIntent(createIntent(context, view))
                .setRank(index)
                .build()
        }
        manager.dynamicShortcuts = shortcuts
    }

    fun createIntent(context: Context, view: ThumbnailGridView): Intent {
        return Intent(context, MainActivity::class.java).apply {
            action = ACTION_THUMBNAIL_GRID_VIEW_SHORTCUT
            putExtra(THUMBNAIL_GRID_VIEW_SHORTCUT_ID, view.storageId)
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK
                    or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    or Intent.FLAG_ACTIVITY_CLEAR_TOP,
            )
        }
    }

    private fun shortcutId(view: ThumbnailGridView): String = "thumbnail_grid_${view.storageId}"
}
