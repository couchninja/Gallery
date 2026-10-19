package org.fossify.gallery.dialogs

import android.view.View
import org.fossify.commons.activities.BaseSimpleActivity
import org.fossify.commons.extensions.getAlertDialogBuilder
import org.fossify.commons.extensions.setupDialogStuff
import org.fossify.gallery.R
import org.fossify.gallery.helpers.bindViewerExifRating
import org.fossify.gallery.helpers.setupViewerExifRatingInput

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

        setupViewerExifRatingInput(
            bar = bar,
            getRating = { selectedRating },
            onCommit = { rating, _ -> selectedRating = rating },
        )

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
