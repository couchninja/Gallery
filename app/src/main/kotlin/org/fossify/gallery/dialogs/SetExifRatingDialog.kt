package org.fossify.gallery.dialogs

import android.view.View
import android.widget.ImageView
import org.fossify.commons.activities.BaseSimpleActivity
import org.fossify.commons.extensions.getAlertDialogBuilder
import org.fossify.commons.extensions.setupDialogStuff
import org.fossify.gallery.R
import org.fossify.gallery.helpers.bindViewerExifRating

class SetExifRatingDialog(
    val activity: BaseSimpleActivity,
    val initialRating: Int,
    val callback: (rating: Int) -> Unit
) {
    private var selectedRating = initialRating.coerceIn(0, 5)

    init {
        val bar = activity.layoutInflater.inflate(R.layout.viewer_exif_rating_bar, null)
        bar.visibility = View.VISIBLE
        updateStars(bar)

        val starClick = { rating: Int ->
            selectedRating = if (selectedRating == rating) {
                0
            } else {
                rating
            }
            updateStars(bar)
        }

        bar.findViewById<ImageView>(R.id.viewer_rating_star_1).setOnClickListener { starClick(1) }
        bar.findViewById<ImageView>(R.id.viewer_rating_star_2).setOnClickListener { starClick(2) }
        bar.findViewById<ImageView>(R.id.viewer_rating_star_3).setOnClickListener { starClick(3) }
        bar.findViewById<ImageView>(R.id.viewer_rating_star_4).setOnClickListener { starClick(4) }
        bar.findViewById<ImageView>(R.id.viewer_rating_star_5).setOnClickListener { starClick(5) }

        activity.getAlertDialogBuilder()
            .setPositiveButton(org.fossify.commons.R.string.ok) { _, _ ->
                callback(selectedRating)
            }
            .setNegativeButton(org.fossify.commons.R.string.cancel, null)
            .apply {
                activity.setupDialogStuff(bar, this, R.string.set_exif_rating)
            }
    }

    private fun updateStars(bar: View) {
        bindViewerExifRating(bar, selectedRating)
    }
}
