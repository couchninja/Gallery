package org.fossify.gallery.adapters

import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import org.fossify.commons.activities.BaseSimpleActivity
import org.fossify.commons.extensions.applyColorFilter
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.gallery.databinding.ItemManageThumbnailGridViewBinding
import org.fossify.gallery.helpers.ThumbnailGridView

class ManageThumbnailGridViewsAdapter(
    private val activity: BaseSimpleActivity,
    private val views: MutableList<ThumbnailGridView>,
) : RecyclerView.Adapter<ManageThumbnailGridViewsAdapter.ViewHolder>() {

    private var startDragListener: ((RecyclerView.ViewHolder) -> Unit)? = null

    class ViewHolder(val binding: ItemManageThumbnailGridViewBinding) : RecyclerView.ViewHolder(binding.root)

    fun setStartDragListener(listener: (RecyclerView.ViewHolder) -> Unit) {
        startDragListener = listener
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemManageThumbnailGridViewBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false,
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val view = views[position]
        holder.binding.manageThumbnailGridViewLabel.text = activity.getString(view.labelRes)
        holder.binding.manageThumbnailGridViewDragHandle.applyColorFilter(activity.getProperTextColor())
        holder.binding.manageThumbnailGridViewDragHandle.setOnTouchListener { _, event ->
            if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                startDragListener?.invoke(holder)
            }
            false
        }
    }

    override fun getItemCount(): Int = views.size

    fun moveItem(from: Int, to: Int) {
        if (from == to || from !in views.indices || to !in views.indices) {
            return
        }
        val item = views.removeAt(from)
        views.add(to, item)
        notifyItemMoved(from, to)
    }
}
