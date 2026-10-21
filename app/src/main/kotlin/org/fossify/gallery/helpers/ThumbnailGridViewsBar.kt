package org.fossify.gallery.helpers

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.navigation.NavigationBarView
import org.fossify.commons.activities.BaseSimpleActivity
import org.fossify.commons.extensions.adjustAlpha
import org.fossify.commons.extensions.beGone
import org.fossify.commons.extensions.beVisible
import org.fossify.commons.extensions.getAlertDialogBuilder
import org.fossify.commons.extensions.getProperBackgroundColor
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.gallery.R
import org.fossify.gallery.dialogs.ManageThumbnailGridViewsDialog

class ThumbnailGridViewsBar(
    private val activity: BaseSimpleActivity,
    private val container: View,
    private val bottomNavigation: BottomNavigationView,
    private val onViewSelected: (ThumbnailGridView) -> Unit,
) {
    private var scrollOffset = 0
    private var barVisible = true
    private var onBottomInsetChanged: ((Int) -> Unit)? = null

    init {
        (container as? ViewGroup)?.apply {
            clipChildren = true
            clipToPadding = true
        }
        ViewCompat.setOnApplyWindowInsetsListener(bottomNavigation) { _, insets -> insets }
    }

    private val itemSelectedListener = NavigationBarView.OnItemSelectedListener { item ->
        when (item.itemId) {
            R.id.thumbnail_view_more -> {
                showMorePopup()
                false
            }
            else -> {
                ThumbnailGridView.fromMenuItemId(item.itemId)?.let(onViewSelected)
                true
            }
        }
    }

    fun setOnBottomInsetChangedListener(listener: (Int) -> Unit) {
        onBottomInsetChanged = listener
    }

    fun updateColors() {
        val background = activity.getProperBackgroundColor().adjustAlpha(THUMBNAIL_GRID_NAV_BAR_ALPHA)
        container.setBackgroundColor(background)
        bottomNavigation.setBackgroundColor(android.graphics.Color.TRANSPARENT)
        val textColor = activity.getProperTextColor()
        val primaryColor = activity.getProperPrimaryColor()
        // Default theme uses a solid primary pill with primary-tinted icons — icon vanishes on selection.
        bottomNavigation.itemActiveIndicatorColor = ColorStateList.valueOf(Color.TRANSPARENT)
        val tint = ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
            intArrayOf(primaryColor, textColor.adjustAlpha(0.65f)),
        )
        bottomNavigation.itemIconTintList = tint
        bottomNavigation.itemTextColor = tint
    }

    fun buildMenu() {
        val visibleViews = ThumbnailGridViewConfig.getVisibleViewsInOrder(activity)
        val menu = bottomNavigation.menu
        menu.clear()

        val maxPrimary = minOf(4, bottomNavigation.maxItemCount - 1)
        visibleViews.take(maxPrimary).forEach { view ->
            menu.add(0, view.menuItemId, 0, activity.getString(view.labelRes)).apply {
                setIcon(view.iconRes)
            }
        }

        if (visibleViews.size > maxPrimary || visibleViews.isNotEmpty()) {
            menu.add(0, R.id.thumbnail_view_more, 0, activity.getString(R.string.thumbnail_grid_view_more)).apply {
                setIcon(org.fossify.commons.R.drawable.ic_three_dots_vector)
            }
        }

        bottomNavigation.setOnItemSelectedListener(itemSelectedListener)
    }

    fun updateSelection(view: ThumbnailGridView) {
        val menu = bottomNavigation.menu
        val targetId = if (menu.findItem(view.menuItemId) != null) {
            view.menuItemId
        } else {
            R.id.thumbnail_view_more
        }
        bottomNavigation.setOnItemSelectedListener(null)
        bottomNavigation.selectedItemId = targetId
        bottomNavigation.setOnItemSelectedListener(itemSelectedListener)
    }

    fun show() {
        if (!container.isVisible) {
            container.beVisible()
            onBottomInsetChanged?.invoke(container.height)
        }
        if (!barVisible) {
            barVisible = true
            animateNavItemsVisible(visible = true)
            container.animate().translationY(0f).setDuration(SCROLL_ANIM_DURATION_MS).start()
        }
    }

    /** After leaving the photo viewer, undo scroll-hide so the view bar is visible again. */
    fun revealAfterReturningToGrid() {
        scrollOffset = 0
        container.animate().cancel()
        bottomNavigation.animate().cancel()
        barVisible = true
        animateNavItemsVisible(visible = true, animate = false)
        container.translationY = 0f
        if (container.isVisible) {
            onBottomInsetChanged?.invoke(container.height)
        }
    }

    fun hide() {
        if (!barVisible || !container.isVisible) {
            return
        }
        barVisible = false
        animateNavItemsVisible(visible = false)
        container.animate()
            .translationY(scrollHideTranslationY())
            .setDuration(SCROLL_ANIM_DURATION_MS)
            .start()
    }

    /** Slide the view bar down, leaving collapse padding + system-nav spacer above the OS buttons. */
    private fun scrollHideTranslationY(): Float {
        val reservedBottom = collapsedReservedBottomHeight()
        if (container.height > reservedBottom) {
            return (container.height - reservedBottom).toFloat()
        }
        if (bottomNavigation.height > 0) {
            return bottomNavigation.height.toFloat()
        }
        return container.height.toFloat()
    }

    private fun collapsedReservedBottomHeight(): Int {
        val navSpacer = container.findViewById<View>(R.id.media_thumbnail_views_nav_spacer)
        val collapsePadding = container.findViewById<View>(R.id.media_thumbnail_views_nav_collapse_padding)
        val spacerHeight = navSpacer?.height ?: 0
        val paddingHeight = collapsePadding?.height?.takeIf { it > 0 }
            ?: (THUMBNAIL_GRID_NAV_COLLAPSED_PADDING_DP * activity.resources.displayMetrics.density).toInt()
        return spacerHeight + paddingHeight
    }

    private fun animateNavItemsVisible(visible: Boolean, animate: Boolean = true) {
        val targetAlpha = if (visible) 1f else 0f
        bottomNavigation.animate().cancel()
        if (!animate) {
            bottomNavigation.alpha = targetAlpha
            bottomNavigation.isEnabled = visible
            return
        }
        if (visible) {
            bottomNavigation.isEnabled = true
        }
        if (bottomNavigation.alpha == targetAlpha) {
            bottomNavigation.isEnabled = visible
            return
        }
        bottomNavigation.animate()
            .alpha(targetAlpha)
            .setDuration(SCROLL_ANIM_DURATION_MS)
            .withEndAction {
                if (!visible) {
                    bottomNavigation.isEnabled = false
                }
            }
            .start()
    }

    fun attachScrollHideBehavior(recyclerView: RecyclerView) {
        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (!container.isVisible) {
                    return
                }

                scrollOffset += dy
                if (dy > 0 && scrollOffset > SCROLL_HIDE_THRESHOLD) {
                    hide()
                } else if (dy < 0) {
                    show()
                }
            }

            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                if (newState == RecyclerView.SCROLL_STATE_IDLE && !recyclerView.canScrollVertically(-1)) {
                    scrollOffset = 0
                    show()
                }
            }
        })
    }

    fun notifyLayoutChanged() {
        if (container.isVisible && barVisible) {
            onBottomInsetChanged?.invoke(container.height)
        }
    }

    private fun showMorePopup() {
        val visibleViews = ThumbnailGridViewConfig.getVisibleViewsInOrder(activity)
        val maxPrimary = minOf(4, bottomNavigation.maxItemCount - 1)
        val overflowViews = visibleViews.drop(maxPrimary)

        val labels = buildList {
            overflowViews.forEach { add(activity.getString(it.labelRes)) }
            add(activity.getString(R.string.thumbnail_grid_view_manage))
        }

        activity.getAlertDialogBuilder()
            .setItems(labels.toTypedArray()) { _, which ->
                if (which < overflowViews.size) {
                    onViewSelected(overflowViews[which])
                } else {
                    ManageThumbnailGridViewsDialog(activity) {
                        buildMenu()
                    }
                }
            }
            .show()
    }

    fun setVisible(visible: Boolean) {
        if (visible) {
            container.beVisible()
            notifyLayoutChanged()
        } else {
            container.beGone()
            onBottomInsetChanged?.invoke(0)
        }
    }

    companion object {
        private const val SCROLL_HIDE_THRESHOLD = 12
        private const val SCROLL_ANIM_DURATION_MS = 150L
    }
}
