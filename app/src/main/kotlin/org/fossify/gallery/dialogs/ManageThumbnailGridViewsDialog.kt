package org.fossify.gallery.dialogs

import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.LinearLayout
import org.fossify.commons.activities.BaseSimpleActivity
import org.fossify.commons.extensions.getAlertDialogBuilder
import org.fossify.commons.extensions.setupDialogStuff
import org.fossify.commons.views.MyAppCompatCheckbox
import org.fossify.commons.views.MyTextView
import org.fossify.gallery.R
import org.fossify.gallery.extensions.config
import org.fossify.gallery.helpers.ThumbnailGridView
import org.fossify.gallery.helpers.ThumbnailGridViewConfig

class ManageThumbnailGridViewsDialog(
    private val activity: BaseSimpleActivity,
    private val callback: () -> Unit,
) {
    private data class Row(
        val view: ThumbnailGridView,
        var visible: Boolean,
    )

    init {
        val hidden = activity.config.hiddenThumbnailGridViews.split("|").filter { it.isNotEmpty() }.toSet()
        val order = ThumbnailGridViewConfig.getVisibleViewsInOrder(activity)
        val orderedViews = (order + ThumbnailGridView.entries.filter { it !in order }).distinct()
        val rows = orderedViews.map { Row(it, it.storageId !in hidden) }.toMutableList()

        val horizontalPadding = activity.resources.getDimension(org.fossify.commons.R.dimen.activity_margin).toInt()
        val holder = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(horizontalPadding, horizontalPadding, horizontalPadding, 0)
        }

        fun refreshRows() {
            holder.removeAllViews()
            rows.forEachIndexed { index, row ->
                val rowLayout = LinearLayout(activity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    )
                }

                val checkbox = MyAppCompatCheckbox(activity).apply {
                    isChecked = row.visible
                    text = activity.getString(row.view.labelRes)
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                    setOnCheckedChangeListener { _, isChecked -> row.visible = isChecked }
                }

                val up = ImageButton(activity).apply {
                    setImageResource(org.fossify.commons.R.drawable.ic_chevron_up_vector)
                    background = null
                    contentDescription = activity.getString(row.view.labelRes)
                    isEnabled = index > 0
                    setOnClickListener {
                        if (index > 0) {
                            rows.removeAt(index)
                            rows.add(index - 1, row)
                            refreshRows()
                        }
                    }
                }

                val down = ImageButton(activity).apply {
                    setImageResource(org.fossify.commons.R.drawable.ic_chevron_down_vector)
                    background = null
                    contentDescription = activity.getString(row.view.labelRes)
                    isEnabled = index < rows.lastIndex
                    setOnClickListener {
                        if (index < rows.lastIndex) {
                            rows.removeAt(index)
                            rows.add(index + 1, row)
                            refreshRows()
                        }
                    }
                }

                rowLayout.addView(checkbox)
                rowLayout.addView(up)
                rowLayout.addView(down)
                holder.addView(rowLayout)
            }

            val hint = MyTextView(activity).apply {
                text = activity.getString(R.string.thumbnail_grid_view_manage_hint)
                alpha = 0.75f
                setPadding(0, horizontalPadding, 0, horizontalPadding)
            }
            holder.addView(hint)
        }

        refreshRows()

        activity.getAlertDialogBuilder()
            .setTitle(R.string.thumbnail_grid_view_manage)
            .setPositiveButton(org.fossify.commons.R.string.ok) { _, _ ->
                val visible = rows.filter { it.visible }.map { it.view }
                val hiddenViews = rows.filter { !it.visible }.map { it.view }.toSet()
                if (visible.isEmpty()) {
                    return@setPositiveButton
                }
                ThumbnailGridViewConfig.saveVisibleOrder(activity, visible, hiddenViews)
                callback()
            }
            .setNegativeButton(org.fossify.commons.R.string.cancel, null)
            .apply {
                activity.setupDialogStuff(holder, this)
            }
    }
}
