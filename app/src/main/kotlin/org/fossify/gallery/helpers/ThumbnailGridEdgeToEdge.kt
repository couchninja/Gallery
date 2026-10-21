package org.fossify.gallery.helpers

import android.graphics.Color
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.LinearLayout
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.RecyclerView
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

const val THUMBNAIL_GRID_APP_BAR_ALPHA = 0.72f
const val THUMBNAIL_GRID_NAV_BAR_ALPHA = 0.78f
const val THUMBNAIL_GRID_NAV_SPACER_INSET_REDUCE_DP = 10f
const val THUMBNAIL_GRID_NAV_SCRIM_TAG = "thumbnail_grid_nav_scrim"

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
        val scrim = contentRoot.findViewWithTag<View>(THUMBNAIL_GRID_NAV_SCRIM_TAG)
        val bottomInset = when {
            bottomChrome?.isVisible == true -> bottomChrome.height
            scrim?.isVisible == true -> scrim.height
            else -> 0
        }
        grid.updatePaddingWithBase(bottom = bottomInset)
    }

    searchMenu.onGlobalLayout { syncTopInset() }
    searchMenu.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> syncTopInset() }

    bottomChrome?.let { chrome ->
        chrome.tag = THUMBNAIL_GRID_BOTTOM_CHROME_TAG
        val navSpacer = chrome.findViewById<View>(org.fossify.gallery.R.id.media_thumbnail_views_nav_spacer)
        chrome.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> syncBottomInset() }
        ViewCompat.setOnApplyWindowInsetsListener(chrome) { view, insets ->
            val bottom = insets.getInsetsIgnoringVisibility(WindowInsetsCompat.Type.systemBars()).bottom
            val reducePx = (THUMBNAIL_GRID_NAV_SPACER_INSET_REDUCE_DP * view.resources.displayMetrics.density).toInt()
            navSpacer?.updateLayoutParams<LinearLayout.LayoutParams> {
                height = (bottom - reducePx).coerceAtLeast(0)
            }
            insets
        }
        ViewCompat.requestApplyInsets(chrome)
    }
    syncBottomInset()
}

private const val THUMBNAIL_GRID_BOTTOM_CHROME_TAG = "thumbnail_grid_bottom_chrome"

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
