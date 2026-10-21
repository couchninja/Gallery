package org.fossify.gallery.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import org.fossify.commons.activities.BaseSimpleActivity
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.extensions.setupViewBackground
import org.fossify.gallery.databinding.ItemThumbnailGridTagBinding

class ThumbnailGridTagsAdapter(
    private val activity: BaseSimpleActivity,
    private var tags: List<String>,
    private val onTagClick: (String) -> Unit,
) : RecyclerView.Adapter<ThumbnailGridTagsAdapter.TagViewHolder>() {

    fun updateTags(newTags: List<String>) {
        tags = newTags
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TagViewHolder {
        val binding = ItemThumbnailGridTagBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return TagViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TagViewHolder, position: Int) {
        holder.bind(tags[position])
    }

    override fun getItemCount() = tags.size

    inner class TagViewHolder(private val binding: ItemThumbnailGridTagBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(tag: String) {
            binding.thumbnailGridTagHolder.setupViewBackground(activity)
            binding.thumbnailGridTagTitle.apply {
                text = tag
                setTextColor(context.getProperTextColor())
            }
            binding.root.setOnClickListener { onTagClick(tag) }
        }
    }
}
