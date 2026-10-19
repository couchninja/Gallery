package org.fossify.gallery.helpers

import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import kotlin.math.abs
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
        val filled = index + 1 <= rating
        star.beVisibleIf(filled)
        if (filled) {
            star.setImageResource(org.fossify.commons.R.drawable.ic_star_vector)
        }
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

private fun viewerRatingStars(bar: View): Array<ImageView> = arrayOf(
    bar.findViewById(R.id.viewer_rating_star_1),
    bar.findViewById(R.id.viewer_rating_star_2),
    bar.findViewById(R.id.viewer_rating_star_3),
    bar.findViewById(R.id.viewer_rating_star_4),
    bar.findViewById(R.id.viewer_rating_star_5)
)

/** Maps a touch x (in [bar] coordinates) to a rating 0–5; x left of the first star is 0. */
fun viewerRatingFromTouchX(bar: View, x: Float): Int {
    val stars = viewerRatingStars(bar)
    if (x < stars.first().left) {
        return 0
    }
    stars.forEachIndexed { index, star ->
        if (x <= star.right) {
            return index + 1
        }
    }
    return 5
}

/**
 * Tap or horizontal swipe on the viewer star bar to set rating (including 0 by swiping left of star 1).
 * [getRating] supplies the persisted rating for revert on cancel; [onCommit] runs on ACTION_UP
 * with [fromTap] true when the gesture stayed within touch slop (not a swipe).
 */
fun setupViewerExifRatingInput(
    bar: View,
    getRating: () -> Int,
    onCommit: (rating: Int, fromTap: Boolean) -> Unit,
) {
    var dragging = false
    var isSwipe = false
    var downXInBar = 0f
    var downYInBar = 0f
    val touchSlop = ViewConfiguration.get(bar.context).scaledTouchSlop
    val stars = viewerRatingStars(bar)
    stars.forEach { star ->
        star.isClickable = false
        star.isFocusable = false
    }

    val handler = View.OnTouchListener { view, event ->
        val xInBar = if (view === bar) {
            event.x
        } else {
            view.left + event.x
        }
        val yInBar = if (view === bar) {
            event.y
        } else {
            view.top + event.y
        }
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                dragging = true
                isSwipe = false
                downXInBar = xInBar
                downYInBar = yInBar
                bar.parent?.requestDisallowInterceptTouchEvent(true)
                bindViewerExifRating(bar, viewerRatingFromTouchX(bar, xInBar))
                true
            }
            MotionEvent.ACTION_MOVE -> {
                if (dragging) {
                    if (!isSwipe && (abs(xInBar - downXInBar) > touchSlop || abs(yInBar - downYInBar) > touchSlop)) {
                        isSwipe = true
                    }
                    bindViewerExifRating(bar, viewerRatingFromTouchX(bar, xInBar))
                }
                true
            }
            MotionEvent.ACTION_UP -> {
                dragging = false
                bar.parent?.requestDisallowInterceptTouchEvent(false)
                onCommit(viewerRatingFromTouchX(bar, xInBar), !isSwipe)
                true
            }
            MotionEvent.ACTION_CANCEL -> {
                dragging = false
                bar.parent?.requestDisallowInterceptTouchEvent(false)
                bindViewerExifRating(bar, getRating())
                true
            }
            else -> false
        }
    }
    bar.setOnTouchListener(handler)
    stars.forEach { it.setOnTouchListener(handler) }
}
