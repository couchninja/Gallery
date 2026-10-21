package org.fossify.gallery.helpers

import android.graphics.Color
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.RecyclerView
import com.qtalk.recyclerviewfastscroller.RecyclerViewFastScroller
import org.fossify.commons.activities.EdgeToEdgeActivity
import org.fossify.commons.extensions.adjustAlpha
import org.fossify.commons.extensions.applyColorFilter
import org.fossify.commons.extensions.beGone
import org.fossify.commons.extensions.beVisible
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.getProperBackgroundColor
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.onGlobalLayout
import org.fossify.commons.extensions.updateMarginWithBase
import org.fossify.commons.extensions.updatePaddingWithBase
import org.fossify.commons.helpers.LOWER_ALPHA
import org.fossify.commons.views.MySearchMenu
import com.qtalk.recyclerviewfastscroller.R as FastScrollerR

const val THUMBNAIL_GRID_APP_BAR_ALPHA = 0.85f
const val THUMBNAIL_GRID_NAV_BAR_ALPHA = 0.85f
const val THUMBNAIL_GRID_NAV_SCRIM_TAG = "thumbnail_grid_nav_scrim"

/** Bottom padding so the last row can scroll above system nav and the view bar slot. */
fun thumbnailGridBottomScrollPadding(contentRoot: ViewGroup, bottomChrome: View?): Int {
    if (bottomChrome != null && bottomChrome.isVisible) {
        if (bottomChrome.height > 0) {
            return bottomChrome.height
        }
        if (contentRoot.width > 0) {
            bottomChrome.measure(
                View.MeasureSpec.makeMeasureSpec(contentRoot.width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            )
            if (bottomChrome.measuredHeight > 0) {
                return bottomChrome.measuredHeight
            }
        }
    }

    val scrim = contentRoot.findViewWithTag<View>(THUMBNAIL_GRID_NAV_SCRIM_TAG)
    if (scrim != null && scrim.height > 0) {
        return scrim.height
    }
    return ViewCompat.getRootWindowInsets(contentRoot)
        ?.getInsetsIgnoringVisibility(WindowInsetsCompat.Type.systemBars())?.bottom ?: 0
}

fun EdgeToEdgeActivity.applyTransparentSystemBarsForThumbnailGrid(contentRoot: View) {
    window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
    window.statusBarColor = Color.TRANSPARENT
    window.navigationBarColor = Color.TRANSPARENT
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        window.navigationBarDividerColor = Color.TRANSPARENT
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        window.isNavigationBarContrastEnforced = false
        window.isStatusBarContrastEnforced = false
    }
    window.decorView.setBackgroundColor(Color.TRANSPARENT)
    contentRoot.setBackgroundColor(getProperBackgroundColor())
    (contentRoot as? ViewGroup)?.let { updateThumbnailGridNavBarScrim(it) }
}

fun EdgeToEdgeActivity.updateThumbnailGridNavBarScrim(contentRoot: ViewGroup) {
    val scrim = contentRoot.findViewWithTag<View>(THUMBNAIL_GRID_NAV_SCRIM_TAG) ?: return
    scrim.setBackgroundColor(getProperBackgroundColor().adjustAlpha(THUMBNAIL_GRID_NAV_BAR_ALPHA))
    val bottomChrome = contentRoot.findViewWithTag<View>(THUMBNAIL_GRID_BOTTOM_CHROME_TAG)
    if (bottomChrome?.isVisible == true) {
        bottomChrome.setBackgroundColor(getProperBackgroundColor().adjustAlpha(THUMBNAIL_GRID_NAV_BAR_ALPHA))
    }
}

fun MySearchMenu.updateThumbnailGridSearchMenuColors() {
    updateColors()
    setBackgroundColor(context.getProperBackgroundColor().adjustAlpha(THUMBNAIL_GRID_APP_BAR_ALPHA))
    binding.toolbarContainer.background?.applyColorFilter(
        context.getProperPrimaryColor().adjustAlpha(LOWER_ALPHA),
    )
}

fun EdgeToEdgeActivity.setupThumbnailGridUnderChrome(
    contentRoot: ViewGroup,
    searchMenu: MySearchMenu,
    grid: RecyclerView,
    viewsBelowSearchBar: List<View> = emptyList(),
    bottomChrome: View? = null,
    fastScroller: RecyclerViewFastScroller? = null,
) {
    applyTransparentSystemBarsForThumbnailGrid(contentRoot)
    attachThumbnailGridNavBarScrim(contentRoot, bottomChrome)
    setupEdgeToEdge(padBottomImeAndSystem = listOf(grid))
    grid.clipToPadding = false

    fun syncTopInset() {
        val topInset = searchMenu.height
        if (topInset <= 0) {
            return
        }
        grid.updatePaddingWithBase(top = topInset)
        viewsBelowSearchBar.forEach { view ->
            view.updateMarginWithBase(top = topInset)
        }
    }

    fun syncBottomInset() {
        grid.updatePaddingWithBase(bottom = thumbnailGridBottomScrollPadding(contentRoot, bottomChrome))
    }

    searchMenu.onGlobalLayout { syncTopInset() }
    searchMenu.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> syncTopInset() }

    bottomChrome?.let { chrome ->
        chrome.tag = THUMBNAIL_GRID_BOTTOM_CHROME_TAG
        val bottomChromeLayout = ThumbnailGridBottomChromeLayout.bind(chrome as ViewGroup)
        chrome.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> syncBottomInset() }
        bottomChromeLayout.attachWindowInsets { syncBottomInset() }
    }
    syncBottomInset()

    fastScroller?.let { limitFastScrollerTrackToChrome(contentRoot, searchMenu, bottomChrome, it) }
}

private const val THUMBNAIL_GRID_BOTTOM_CHROME_TAG = "thumbnail_grid_bottom_chrome"

/**
 * Keeps the fast scroller track between the bottom of the search bar and the top of the bottom
 * view bar. The bar's slot is reserved even while it is scrolled away or hidden, so the track
 * does not jump around.
 */
private fun limitFastScrollerTrackToChrome(
    contentRoot: ViewGroup,
    searchMenu: View,
    bottomChrome: View?,
    fastScroller: RecyclerViewFastScroller,
) {
    val trackView = fastScroller.findViewById<View>(FastScrollerR.id.trackView)
    val handleView = fastScroller.findViewById<View>(FastScrollerR.id.thumbIV)
    val location = IntArray(2)
    var knownBottomChromeHeight = 0

    fun windowTop(view: View): Int {
        view.getLocationInWindow(location)
        return location[1]
    }

    /** Window Y of [view]'s bottom edge as if [View.getTranslationY] were zero (scroll-hide safe). */
    fun restingBottomInWindow(view: View): Int {
        return windowTop(view) + view.height - view.translationY.toInt()
    }

    /** Window Y of [view]'s top edge as if [View.getTranslationY] were zero (scroll-hide safe). */
    fun restingTopInWindow(view: View): Int {
        return windowTop(view) - view.translationY.toInt()
    }

    fun reservedBottomChromeHeight(): Int {
        val padding = thumbnailGridBottomScrollPadding(contentRoot, bottomChrome)
        if (padding > 0) {
            knownBottomChromeHeight = padding
        }
        return knownBottomChromeHeight.takeIf { it > 0 } ?: padding
    }

    fun syncTrackMargins() {
        if (fastScroller.height == 0 || searchMenu.height == 0) {
            return
        }

        val isVertical = fastScroller.fastScrollDirection == RecyclerViewFastScroller.FastScrollDirection.VERTICAL
        val scrollerTop = windowTop(fastScroller)
        val top = if (isVertical) {
            (restingBottomInWindow(searchMenu) - scrollerTop).coerceAtLeast(0)
        } else {
            0
        }
        val bottom = if (isVertical) {
            val barTop = when {
                bottomChrome != null && bottomChrome.height > 0 -> restingTopInWindow(bottomChrome)
                else -> windowTop(contentRoot) + contentRoot.height - reservedBottomChromeHeight()
            }
            (scrollerTop + fastScroller.height - barTop).coerceAtLeast(0)
        } else {
            0
        }

        if (fastScroller.trackMarginStart != top || fastScroller.trackMarginEnd != bottom) {
            fastScroller.trackMarginStart = top
            fastScroller.trackMarginEnd = bottom
            fastScroller.post { fastScroller.requestLayout() }
        }
    }

    // The library positions the popup in scroller coordinates, ignoring the track margin.
    fun alignPopupWithHandle() {
        val popup = fastScroller.popupTextView
        if (trackView == null || handleView == null || popup.height == 0 ||
            fastScroller.fastScrollDirection != RecyclerViewFastScroller.FastScrollDirection.VERTICAL
        ) {
            return
        }

        val maxOffset = (trackView.height - popup.height).toFloat().coerceAtLeast(0f)
        val desiredY = trackView.top + (handleView.y - popup.height).coerceIn(0f, maxOffset)
        if (popup.y != desiredY) {
            popup.y = desiredY
        }
    }

    fastScroller.viewTreeObserver.addOnPreDrawListener {
        syncTrackMargins()
        alignPopupWithHandle()
        true
    }
}

private fun EdgeToEdgeActivity.attachThumbnailGridNavBarScrim(contentRoot: ViewGroup, bottomChrome: View?) {
    val scrim = contentRoot.findViewWithTag<View>(THUMBNAIL_GRID_NAV_SCRIM_TAG) ?: run {
        val created = View(this).apply {
            tag = THUMBNAIL_GRID_NAV_SCRIM_TAG
            isClickable = false
            isFocusable = false
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            setBackgroundColor(getProperBackgroundColor().adjustAlpha(THUMBNAIL_GRID_NAV_BAR_ALPHA))
        }
        val layoutParams = CoordinatorLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            0,
        ).apply {
            gravity = Gravity.BOTTOM
        }
        contentRoot.addView(created, layoutParams)

        ViewCompat.setOnApplyWindowInsetsListener(created) { view, insets ->
            val bottom = insets.getInsetsIgnoringVisibility(WindowInsetsCompat.Type.systemBars()).bottom
            view.updateLayoutParams<CoordinatorLayout.LayoutParams> {
                height = bottom
            }
            val chromeVisible = bottomChrome?.isVisible == true
            view.beVisibleIf(bottom > 0 && !chromeVisible)
            insets
        }
        ViewCompat.requestApplyInsets(created)
        created
    }

    updateThumbnailGridNavBarScrim(contentRoot)
    bottomChrome?.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
        val bottom = ViewCompat.getRootWindowInsets(scrim)?.getInsetsIgnoringVisibility(WindowInsetsCompat.Type.systemBars())?.bottom ?: 0
        scrim.beVisibleIf(bottom > 0 && bottomChrome.isVisible != true)
    }
}

fun EdgeToEdgeActivity.setThumbnailGridBottomChromeVisible(contentRoot: ViewGroup, bottomChrome: View?, visible: Boolean) {
    val scrim = contentRoot.findViewWithTag<View>(THUMBNAIL_GRID_NAV_SCRIM_TAG) ?: return
    if (visible) {
        bottomChrome?.beVisible()
        scrim.beGone()
    } else {
        bottomChrome?.beGone()
        ViewCompat.requestApplyInsets(scrim)
    }
}
