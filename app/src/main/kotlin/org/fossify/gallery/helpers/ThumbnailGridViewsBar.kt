package org.fossify.gallery.helpers

import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.view.ContextThemeWrapper
import androidx.annotation.DrawableRes
import androidx.appcompat.content.res.AppCompatResources
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.view.menu.MenuBuilder
import androidx.appcompat.widget.PopupMenu
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.qtalk.recyclerviewfastscroller.RecyclerViewFastScroller
import com.google.android.material.navigation.NavigationBarMenuView
import com.google.android.material.navigation.NavigationBarView
import org.fossify.commons.activities.BaseSimpleActivity
import org.fossify.commons.extensions.adjustAlpha
import org.fossify.commons.extensions.beGone
import org.fossify.commons.extensions.beVisible
import org.fossify.commons.extensions.getPopupMenuTheme
import org.fossify.commons.extensions.getProperBackgroundColor
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.views.MySearchMenu
import org.fossify.gallery.R
import org.fossify.gallery.dialogs.ManageThumbnailGridViewsDialog

class ThumbnailGridViewsBar(
    private val activity: BaseSimpleActivity,
    private val container: View,
    private val bottomNavigation: BottomNavigationView,
    private val searchMenu: MySearchMenu?,
    private val onViewSelected: (ThumbnailGridView) -> Unit,
    private val currentView: () -> ThumbnailGridView,
    private val onViewReselected: ((ThumbnailGridView) -> Unit)? = null,
    private val topChrome: View? = null,
) {
    private var scrollOffset = 0
    private var barVisible = true
    private var onBottomInsetChanged: ((Int) -> Unit)? = null
    private val bottomChromeLayout = ThumbnailGridBottomChromeLayout.bind(container as ViewGroup)
    private val scrollHideTopChrome: View?
        get() = topChrome ?: searchMenu

    private val itemReselectedListener = NavigationBarView.OnItemReselectedListener { item ->
        when (item.itemId) {
            R.id.thumbnail_view_more -> showMorePopup()
            else -> ThumbnailGridView.fromMenuItemId(item.itemId)?.let { view ->
                onViewReselected?.invoke(view)
            }
        }
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

    init {
        (container as ViewGroup).apply {
            clipChildren = true
            clipToPadding = true
        }
        ViewCompat.setOnApplyWindowInsetsListener(bottomNavigation) { _, insets -> insets }
        bottomNavigation.setOnItemReselectedListener(itemReselectedListener)
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

        val maxPrimary = maxPrimaryNavItems()
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
        bottomNavigation.setOnItemReselectedListener(null)
        if (bottomNavigation.selectedItemId != targetId) {
            bottomNavigation.selectedItemId = targetId
        } else {
            // Re-apply checked state when switching between two overflow-only views.
            bottomNavigation.menu.findItem(targetId)?.isChecked = true
        }
        bottomNavigation.setOnItemSelectedListener(itemSelectedListener)
        bottomNavigation.setOnItemReselectedListener(itemReselectedListener)
    }

    fun show() {
        if (!container.isVisible) {
            container.beVisible()
            onBottomInsetChanged?.invoke(container.height)
        }
        if (!barVisible) {
            barVisible = true
            animateNavItemsVisible(visible = true)
            animateSearchChromeVisible(visible = true)
            container.animate().translationY(0f).setDuration(SCROLL_ANIM_DURATION_MS).start()
            scrollHideTopChrome?.animate()?.translationY(0f)?.setDuration(SCROLL_ANIM_DURATION_MS)?.start()
        }
    }

    /** After leaving the photo viewer, undo scroll-hide so the view bar is visible again. */
    fun revealAfterReturningToGrid() {
        scrollOffset = 0
        container.animate().cancel()
        bottomNavigation.animate().cancel()
        scrollHideTopChrome?.animate()?.cancel()
        searchMenu?.binding?.toolbarContainer?.animate()?.cancel()
        barVisible = true
        animateNavItemsVisible(visible = true, animate = false)
        animateSearchChromeVisible(visible = true, animate = false)
        container.translationY = 0f
        scrollHideTopChrome?.translationY = 0f
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
        animateSearchChromeVisible(visible = false)
        container.animate()
            .translationY(scrollHideTranslationY())
            .setDuration(SCROLL_ANIM_DURATION_MS)
            .start()
        scrollHideTopChrome?.animate()
            ?.translationY(-scrollHideSearchTranslationY())
            ?.setDuration(SCROLL_ANIM_DURATION_MS)
            ?.start()
    }

    /** Slide the view bar down, leaving [ThumbnailGridBottomChromeLayout.scrollCollapsedVisibleHeightPx]. */
    private fun scrollHideTranslationY(): Float {
        val reservedBottom = bottomChromeLayout.scrollCollapsedVisibleHeightPx(activity.resources)
        if (container.height > reservedBottom) {
            return (container.height - reservedBottom).toFloat()
        }
        if (bottomNavigation.height > 0) {
            return bottomNavigation.height.toFloat()
        }
        return container.height.toFloat()
    }

    /** Slide the top chrome up, leaving the status-bar inset strip on screen (mirrors bottom chrome). */
    private fun scrollHideSearchTranslationY(): Float {
        val chrome = scrollHideTopChrome ?: return 0f
        val reservedTop = ViewCompat.getRootWindowInsets(chrome)
            ?.getInsetsIgnoringVisibility(WindowInsetsCompat.Type.statusBars())?.top ?: 0
        if (chrome.height > reservedTop) {
            return (chrome.height - reservedTop).toFloat()
        }
        return chrome.height.toFloat()
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

    private fun animateSearchChromeVisible(visible: Boolean, animate: Boolean = true) {
        val toolbarContainer = searchMenu?.binding?.toolbarContainer ?: return
        val targetAlpha = if (visible) 1f else 0f
        toolbarContainer.animate().cancel()
        if (!animate) {
            toolbarContainer.alpha = targetAlpha
            toolbarContainer.isEnabled = visible
            return
        }
        if (visible) {
            toolbarContainer.isEnabled = true
        }
        if (toolbarContainer.alpha == targetAlpha) {
            toolbarContainer.isEnabled = visible
            return
        }
        toolbarContainer.animate()
            .alpha(targetAlpha)
            .setDuration(SCROLL_ANIM_DURATION_MS)
            .withEndAction {
                if (!visible) {
                    toolbarContainer.isEnabled = false
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
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    revealChromeIfAtScrollStart(recyclerView)
                }
            }
        })
    }

    /**
     * Fast-scroll drags move the list without [RecyclerView.SCROLL_STATE_IDLE], so scroll-hide
     * may not undo when the user scrubs back to the top.
     */
    fun attachFastScrollerRevealBehavior(recyclerView: RecyclerView, fastScroller: RecyclerViewFastScroller) {
        fastScroller.setHandleStateListener(object : RecyclerViewFastScroller.HandleStateListener {
            override fun onEngaged() {
                recyclerView.stopScroll()
            }

            override fun onDragged(offset: Float, position: Int) {
                if (position == 0) {
                    recyclerView.post { revealChromeIfAtScrollStart(recyclerView) }
                }
            }

            override fun onReleased() {
                recyclerView.post { revealChromeIfAtScrollStart(recyclerView) }
            }
        })
    }

    private fun revealChromeIfAtScrollStart(recyclerView: RecyclerView) {
        if (!recyclerView.canScrollVertically(-1)) {
            scrollOffset = 0
            show()
        }
    }

    fun notifyLayoutChanged() {
        if (container.isVisible && barVisible) {
            onBottomInsetChanged?.invoke(container.height)
        }
    }

    private fun showMorePopup() {
        val anchor = findMoreMenuAnchor()
        val theme = activity.getPopupMenuTheme()
        val contextTheme = ContextThemeWrapper(activity, theme)
        val visibleViews = ThumbnailGridViewConfig.getVisibleViewsInOrder(activity)
        val maxPrimary = maxPrimaryNavItems()
        val overflowViews = visibleViews.drop(maxPrimary)

        PopupMenu(contextTheme, anchor, Gravity.END).apply {
            var order = 0
            overflowViews.forEach { view ->
                menu.add(0, view.menuItemId, order++, activity.getString(view.labelRes))
                    .setIcon(navBarSizedMenuIcon(view.iconRes))
            }
            menu.add(0, R.id.thumbnail_view_customize, order, activity.getString(R.string.thumbnail_grid_view_manage))
                .setIcon(navBarSizedMenuIcon(org.fossify.commons.R.drawable.ic_settings_cog_vector))

            (menu as? MenuBuilder)?.setOptionalIconsVisible(true)

            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.thumbnail_view_customize -> ManageThumbnailGridViewsDialog(activity) { buildMenu() }
                    else -> ThumbnailGridView.fromMenuItemId(item.itemId)?.let(onViewSelected)
                }
                true
            }
            show()
        }
    }

    /** Popup menus use the vector intrinsic size (24dp); the bar uses [R.dimen.thumbnail_grid_bottom_nav_icon_size]. */
    private fun navBarSizedMenuIcon(@DrawableRes iconRes: Int): Drawable {
        val sizePx = activity.resources.getDimensionPixelSize(R.dimen.thumbnail_grid_bottom_nav_icon_size)
        val paddingStartPx = activity.resources.getDimensionPixelSize(R.dimen.thumbnail_grid_more_menu_icon_padding_start)
        val source = requireNotNull(AppCompatResources.getDrawable(activity, iconRes)).mutate()
        return NavBarSizedMenuIconDrawable(source, sizePx, paddingStartPx)
    }

    private fun findMoreMenuAnchor(): View {
        val menuView = bottomNavigation.getChildAt(0) as? NavigationBarMenuView
        return menuView?.findItemView(R.id.thumbnail_view_more) ?: bottomNavigation
    }

    /** One bar slot is reserved for the overflow ("More") action. */
    private fun maxPrimaryNavItems(): Int = bottomNavigation.maxItemCount - 1

    fun setVisible(visible: Boolean) {
        if (visible) {
            container.beVisible()
            notifyLayoutChanged()
        } else {
            container.beGone()
            onBottomInsetChanged?.invoke(0)
            revealAfterReturningToGrid()
        }
    }

    companion object {
        private const val SCROLL_HIDE_THRESHOLD = 12
        private const val SCROLL_ANIM_DURATION_MS = 150L
    }

    private class NavBarSizedMenuIconDrawable(
        private val wrapped: Drawable,
        private val sizePx: Int,
        private val paddingStartPx: Int,
    ) : Drawable() {
        override fun draw(canvas: Canvas) {
            val iconLeft = bounds.left + paddingStartPx
            val iconRight = iconLeft + sizePx.coerceAtMost(bounds.width() - paddingStartPx)
            val iconBottom = bounds.top + sizePx.coerceAtMost(bounds.height())
            wrapped.setBounds(iconLeft, bounds.top, iconRight, iconBottom)
            wrapped.draw(canvas)
        }

        override fun setAlpha(alpha: Int) {
            wrapped.alpha = alpha
        }

        override fun setColorFilter(colorFilter: ColorFilter?) {
            wrapped.colorFilter = colorFilter
        }

        @Deprecated("Deprecated in Java")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

        override fun getIntrinsicWidth(): Int = sizePx + paddingStartPx

        override fun getIntrinsicHeight(): Int = sizePx
    }
}
