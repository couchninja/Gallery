package org.fossify.gallery.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import org.fossify.commons.activities.BaseSimpleActivity
import org.fossify.commons.extensions.adjustAlpha
import org.fossify.commons.extensions.applyColorFilter
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.extensions.setupViewBackground
import org.fossify.gallery.databinding.ItemThumbnailGridTagBinding
import org.fossify.gallery.helpers.XmpTagDisplay

data class ThumbnailGridTagEntry(val name: String, val photoCount: Int)

class ThumbnailGridTagsAdapter(
    private val activity: BaseSimpleActivity,
    private var tags: List<ThumbnailGridTagEntry>,
    private val onTagClick: (String) -> Unit,
) : RecyclerView.Adapter<ThumbnailGridTagsAdapter.TagViewHolder>() {

    fun updateTags(newTags: List<ThumbnailGridTagEntry>) {
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
        fun bind(entry: ThumbnailGridTagEntry) {
            binding.thumbnailGridTagHolder.setupViewBackground(activity)
            val textColor = binding.root.context.getProperTextColor()
            binding.thumbnailGridTagTitle.apply {
                text = entry.name
                setTextColor(textColor)
            }
            binding.thumbnailGridTagCount.apply {
                text = entry.photoCount.toString()
                setTextColor(textColor.adjustAlpha(0.65f))
            }
            binding.thumbnailGridTagIcon.apply {
                setImageResource(XmpTagDisplay.listIconResForTag(entry.name))
                applyColorFilter(textColor)
            }
            binding.thumbnailGridTagHolder.setOnClickListener { onTagClick(entry.name) }
        }
    }
}
