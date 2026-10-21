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
import androidx.core.view.updateLayoutParams
import androidx.recyclerview.widget.RecyclerView
import org.fossify.commons.activities.EdgeToEdgeActivity
import org.fossify.commons.extensions.adjustAlpha
import org.fossify.commons.extensions.applyColorFilter
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.getProperBackgroundColor
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.onGlobalLayout
import org.fossify.commons.extensions.updateMarginWithBase
import org.fossify.commons.extensions.updatePaddingWithBase
import org.fossify.commons.helpers.LOWER_ALPHA
import org.fossify.commons.views.MySearchMenu

private const val THUMBNAIL_GRID_APP_BAR_ALPHA = 0.72f
private const val THUMBNAIL_GRID_NAV_BAR_ALPHA = 0.55f
private const val THUMBNAIL_GRID_NAV_SCRIM_TAG = "thumbnail_grid_nav_scrim"

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
) {
    applyTransparentSystemBarsForThumbnailGrid(contentRoot)
    attachThumbnailGridNavBarScrim(contentRoot)
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

    searchMenu.onGlobalLayout { syncTopInset() }
    searchMenu.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> syncTopInset() }
}

private fun EdgeToEdgeActivity.attachThumbnailGridNavBarScrim(contentRoot: ViewGroup) {
    if (contentRoot.findViewWithTag<View>(THUMBNAIL_GRID_NAV_SCRIM_TAG) != null) {
        updateThumbnailGridNavBarScrim(contentRoot)
        return
    }

    val scrim = View(this).apply {
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
    contentRoot.addView(scrim, layoutParams)

    ViewCompat.setOnApplyWindowInsetsListener(scrim) { view, insets ->
        val bottom = insets.getInsetsIgnoringVisibility(WindowInsetsCompat.Type.systemBars()).bottom
        view.updateLayoutParams<CoordinatorLayout.LayoutParams> {
            height = bottom
        }
        view.beVisibleIf(bottom > 0)
        insets
    }
    ViewCompat.requestApplyInsets(scrim)
}
