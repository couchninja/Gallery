package org.fossify.gallery.helpers

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import org.fossify.gallery.R

fun bindViewerXmpTagsOverlay(bar: ViewGroup, tags: List<String>) {
    bar.removeAllViews()
    if (tags.isEmpty()) {
        return
    }
    val inflater = LayoutInflater.from(bar.context)
    for (tag in tags) {
        val row = inflater.inflate(R.layout.item_viewer_xmp_tag_row, bar, false)
        row.findViewById<ImageView>(R.id.viewer_xmp_tag_row_icon)
            .setImageResource(XmpTagDisplay.listIconResForTag(tag))
        row.findViewById<TextView>(R.id.viewer_xmp_tag_row_label).text = tag
        bar.addView(row)
    }
}
