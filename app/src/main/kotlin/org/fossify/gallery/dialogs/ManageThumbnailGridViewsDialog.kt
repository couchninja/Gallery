package org.fossify.gallery.dialogs

import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.fossify.commons.activities.BaseSimpleActivity
import org.fossify.commons.extensions.getAlertDialogBuilder
import org.fossify.commons.extensions.setupDialogStuff
import org.fossify.commons.views.MyTextView
import org.fossify.gallery.R
import org.fossify.gallery.adapters.ManageThumbnailGridViewsAdapter
import org.fossify.gallery.helpers.ThumbnailGridView
import org.fossify.gallery.helpers.ThumbnailGridViewConfig

class ManageThumbnailGridViewsDialog(
    private val activity: BaseSimpleActivity,
    private val callback: () -> Unit,
) {
    init {
        val views = ThumbnailGridViewConfig.getViewsInOrder(activity).toMutableList()
        val horizontalPadding = activity.resources.getDimension(org.fossify.commons.R.dimen.activity_margin).toInt()
        val rowHeight = activity.resources.getDimensionPixelSize(R.dimen.manage_thumbnail_grid_view_row_height)
        val adapter = ManageThumbnailGridViewsAdapter(activity, views)

        val recyclerView = RecyclerView(activity).apply {
            layoutManager = LinearLayoutManager(activity)
            this.adapter = adapter
            overScrollMode = RecyclerView.OVER_SCROLL_NEVER
            isNestedScrollingEnabled = false
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                rowHeight * views.size,
            )
        }

        val itemTouchHelper = ItemTouchHelper(
            object : ItemTouchHelper.Callback() {
                override fun isLongPressDragEnabled(): Boolean = false

                override fun isItemViewSwipeEnabled(): Boolean = false

                override fun getMovementFlags(
                    recyclerView: RecyclerView,
                    viewHolder: RecyclerView.ViewHolder,
                ): Int {
                    val dragFlags = ItemTouchHelper.UP or ItemTouchHelper.DOWN
                    return makeMovementFlags(dragFlags, 0)
                }

                override fun onMove(
                    recyclerView: RecyclerView,
                    viewHolder: RecyclerView.ViewHolder,
                    target: RecyclerView.ViewHolder,
                ): Boolean {
                    val from = viewHolder.bindingAdapterPosition
                    val to = target.bindingAdapterPosition
                    if (from == RecyclerView.NO_POSITION || to == RecyclerView.NO_POSITION) {
                        return false
                    }
                    adapter.moveItem(from, to)
                    return true
                }

                override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) = Unit
            },
        )
        itemTouchHelper.attachToRecyclerView(recyclerView)
        adapter.setStartDragListener { holder -> itemTouchHelper.startDrag(holder) }

        val resetButton = MyTextView(activity).apply {
            text = activity.getString(R.string.thumbnail_grid_view_manage_reset)
            setPadding(0, horizontalPadding, 0, 0)
            setOnClickListener {
                views.clear()
                views.addAll(ThumbnailGridView.defaultOrder)
                adapter.notifyDataSetChanged()
            }
        }

        val hint = MyTextView(activity).apply {
            text = activity.getString(R.string.thumbnail_grid_view_manage_hint)
            alpha = 0.75f
            setPadding(0, horizontalPadding, 0, horizontalPadding)
        }

        val holder = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(horizontalPadding, horizontalPadding, horizontalPadding, 0)
            addView(recyclerView)
            addView(resetButton)
            addView(hint)
        }

        activity.getAlertDialogBuilder()
            .setTitle(R.string.thumbnail_grid_view_manage)
            .setPositiveButton(org.fossify.commons.R.string.ok) { _, _ ->
                ThumbnailGridViewConfig.saveViewOrder(activity, views)
                callback()
            }
            .setNegativeButton(org.fossify.commons.R.string.cancel, null)
            .apply {
                activity.setupDialogStuff(holder, this)
            }
    }
}
