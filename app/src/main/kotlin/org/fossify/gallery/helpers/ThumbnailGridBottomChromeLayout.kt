package org.fossify.gallery.helpers

import android.content.res.Resources
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import com.google.android.material.bottomnavigation.BottomNavigationView
import org.fossify.gallery.R

/**
 * Bottom view bar chrome in [R.layout.activity_media] (top → bottom in the layout):
 *
 * ```
 * ┌─────────────────────────┐
 * │  BottomNavigationView   │  (3) @dimen/thumbnail_grid_bottom_3_nav_top_padding
 * ├─────────────────────────┤
 * │  nav_to_background_gap  │  (2) @dimen/thumbnail_grid_bottom_2_nav_to_background_gap
 * ├─────────────────────────┤
 * │  collapsed_background   │  (1) @dimen/thumbnail_grid_bottom_1_collapsed_background_height
 * ├─────────────────────────┤
 * │  os_nav_inset_spacer    │  system nav inset (automatic, not tuned here)
 * └─────────────────────────┘
 *        OS nav / gesture bar
 * ```
 *
 * Scroll-collapsed: nav + (2) slide away; (1) + inset spacer stay on screen.
 * Tune (1)–(3) in `values/dimens.xml`.
 */
class ThumbnailGridBottomChromeLayout private constructor(
    private val chrome: ViewGroup,
    val bottomNavigation: BottomNavigationView,
    private val navToBackgroundGap: View,
    private val collapsedBackground: View,
    private val osNavInsetSpacer: View,
) {

    fun applyStaticHeights(resources: Resources) {
        navToBackgroundGap.updateLayoutParams<LinearLayout.LayoutParams> {
            height = navToBackgroundGapHeightPx(resources)
        }
        collapsedBackground.updateLayoutParams<LinearLayout.LayoutParams> {
            height = collapsedBackgroundHeightPx(resources)
        }
    }

    fun applyOsNavInsetSpacer(systemBarBottomInsetPx: Int) {
        osNavInsetSpacer.updateLayoutParams<LinearLayout.LayoutParams> {
            height = systemBarBottomInsetPx.coerceAtLeast(0)
        }
    }

    /** (1) Background strip left visible when scroll-collapsed. */
    fun collapsedBackgroundHeightPx(resources: Resources): Int {
        if (collapsedBackground.height > 0) {
            return collapsedBackground.height
        }
        return resources.getDimensionPixelSize(R.dimen.thumbnail_grid_bottom_1_collapsed_background_height)
    }

    /** (2) Gap between nav item bottoms and the top of (1). */
    fun navToBackgroundGapHeightPx(resources: Resources): Int {
        if (navToBackgroundGap.height > 0) {
            return navToBackgroundGap.height
        }
        return resources.getDimensionPixelSize(R.dimen.thumbnail_grid_bottom_2_nav_to_background_gap)
    }

    /** (1) + system inset — on-screen chrome when scroll-collapsed. */
    fun scrollCollapsedVisibleHeightPx(resources: Resources): Int {
        val insetSpacerHeight = osNavInsetSpacer.height.takeIf { it > 0 }
            ?: currentSystemBarBottomInsetPx()
        return collapsedBackgroundHeightPx(resources) + insetSpacerHeight
    }

    fun attachWindowInsets(onLayoutChanged: () -> Unit) {
        applyStaticHeights(chrome.resources)
        ViewCompat.setOnApplyWindowInsetsListener(chrome) { view, insets ->
            val bottom = insets.getInsetsIgnoringVisibility(WindowInsetsCompat.Type.systemBars()).bottom
            applyOsNavInsetSpacer(bottom)
            onLayoutChanged()
            insets
        }
        ViewCompat.requestApplyInsets(chrome)
    }

    private fun currentSystemBarBottomInsetPx(): Int {
        return ViewCompat.getRootWindowInsets(chrome)
            ?.getInsetsIgnoringVisibility(WindowInsetsCompat.Type.systemBars())?.bottom ?: 0
    }

    companion object {
        fun bind(chrome: ViewGroup): ThumbnailGridBottomChromeLayout {
            return ThumbnailGridBottomChromeLayout(
                chrome = chrome,
                bottomNavigation = chrome.findViewById(R.id.media_thumbnail_views_bar),
                navToBackgroundGap = chrome.findViewById(R.id.media_thumbnail_views_nav_to_background_gap),
                collapsedBackground = chrome.findViewById(R.id.media_thumbnail_views_collapsed_background),
                osNavInsetSpacer = chrome.findViewById(R.id.media_thumbnail_views_os_nav_inset_spacer),
            )
        }
    }
}
