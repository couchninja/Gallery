package org.fossify.gallery.helpers

import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.gallery.R

fun bindThumbnailExifRating(bar: LinearLayout?, rating: Int, visible: Boolean) {
    if (bar == null) {
        return
    }
    bar.beVisibleIf(visible && rating > 0)
    if (!visible || rating <= 0) {
        return
    }
    val stars = arrayOf(
        bar.findViewById<ImageView>(R.id.exif_rating_star_1),
        bar.findViewById(R.id.exif_rating_star_2),
        bar.findViewById(R.id.exif_rating_star_3),
        bar.findViewById(R.id.exif_rating_star_4),
        bar.findViewById(R.id.exif_rating_star_5)
    )
    stars.forEachIndexed { index, star ->
        setStarFilled(star, index + 1 <= rating)
    }
}

fun bindViewerExifRating(bar: View, rating: Int) {
    val stars = arrayOf(
        bar.findViewById<ImageView>(R.id.viewer_rating_star_1),
        bar.findViewById(R.id.viewer_rating_star_2),
        bar.findViewById(R.id.viewer_rating_star_3),
        bar.findViewById(R.id.viewer_rating_star_4),
        bar.findViewById(R.id.viewer_rating_star_5)
    )
    stars.forEachIndexed { index, star ->
        setStarFilled(star, index + 1 <= rating)
    }
}

private fun setStarFilled(star: ImageView, filled: Boolean) {
    star.setImageResource(
        if (filled) {
            org.fossify.commons.R.drawable.ic_star_vector
        } else {
            org.fossify.commons.R.drawable.ic_star_outline_vector
        }
    )
}
