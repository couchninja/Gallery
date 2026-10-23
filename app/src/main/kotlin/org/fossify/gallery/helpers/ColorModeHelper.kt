package org.fossify.gallery.helpers

import android.animation.ValueAnimator
import android.app.Activity
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.os.Build
import android.view.animation.LinearInterpolator
import androidx.annotation.RequiresApi
import org.fossify.commons.helpers.isUpsideDownCakePlus

/**
 * Helper class to manage color modes for HDR and wide color gamut images.
 */
object ColorModeHelper {

    private const val VIEWER_HDR_HEADROOM_ANIM_MS = 300L

    private var viewerHdrHeadroomAnimator: ValueAnimator? = null

    fun isGainmapSupported() = isUpsideDownCakePlus()

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    fun hasHdrContent(bitmap: Bitmap?): Boolean {
        return bitmap?.hasGainmap() == true
    }

    fun hasWideColorGamut(bitmap: Bitmap?): Boolean {
        return bitmap?.colorSpace?.isWideGamut == true
    }

    fun setColorMode(activity: Activity, colorMode: Int) {
        SwipeFrameMonitor.mark("colorMode ${activity.window.colorMode} -> $colorMode")
        activity.window.setColorMode(colorMode)
    }

    fun setColorModeForImage(activity: Activity, bitmap: Bitmap?, ultraHdr: Boolean = true) {
        setColorMode(
            activity = activity,
            colorMode = colorModeForImage(bitmap, ultraHdr),
        )
    }

    fun viewerUsesUltraHdrHeadroomWhenChromeVisible(bitmap: Bitmap?, ultraHdr: Boolean): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM &&
            ultraHdr &&
            isGainmapSupported() &&
            hasHdrContent(bitmap)
    }

    /** Photo viewer: HDR when chrome hidden; DEFAULT or headroom 1.0 when chrome visible (Ultra HDR, API 35+). */
    fun applyViewerWindowColorMode(
        activity: Activity,
        bitmap: Bitmap?,
        ultraHdr: Boolean,
        chromeVisible: Boolean,
        animateTransition: Boolean = false,
    ) {
        if (chromeVisible && viewerUsesUltraHdrHeadroomWhenChromeVisible(bitmap, ultraHdr)) {
            applyViewerUltraHdrHeadroomForVisibleChrome(activity, animateTransition)
            return
        }
        viewerHdrHeadroomAnimator?.cancel()
        if (chromeVisible) {
            resetColorMode(activity)
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                activity.window.setDesiredHdrHeadroom(0f)
            }
            setColorMode(activity, colorModeForImage(bitmap, ultraHdr))
        }
    }

    /** Ultra HDR with chrome visible: ramp headroom down to 1.0 (hide-chrome uses immediate HDR mode). */
    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    private fun applyViewerUltraHdrHeadroomForVisibleChrome(
        activity: Activity,
        animateTransition: Boolean,
    ) {
        setColorMode(activity, ActivityInfo.COLOR_MODE_HDR)
        val window = activity.window
        if (!animateTransition) {
            viewerHdrHeadroomAnimator?.cancel()
            window.setDesiredHdrHeadroom(1.0f)
            return
        }
        viewerHdrHeadroomAnimator?.cancel()
        val start = window.desiredHdrHeadroom.let { current ->
            when {
                current >= 1.0f -> current
                current == 0.0f -> activity.display?.hdrSdrRatio?.coerceAtLeast(1.0f) ?: 1.0f
                else -> 1.0f
            }
        }
        if (start <= 1.0f) {
            window.setDesiredHdrHeadroom(1.0f)
            return
        }
        viewerHdrHeadroomAnimator = ValueAnimator.ofFloat(start, 1.0f).apply {
            duration = VIEWER_HDR_HEADROOM_ANIM_MS
            interpolator = LinearInterpolator()
            addUpdateListener { window.setDesiredHdrHeadroom(it.animatedValue as Float) }
            start()
        }
    }

    fun requestedViewerWindowColorMode(bitmap: Bitmap?, ultraHdr: Boolean, chromeVisible: Boolean): Int {
        return if (chromeVisible) {
            ActivityInfo.COLOR_MODE_DEFAULT
        } else {
            colorModeForImage(bitmap, ultraHdr)
        }
    }

    private fun colorModeForImage(bitmap: Bitmap?, ultraHdr: Boolean): Int {
        return when {
            ultraHdr && isGainmapSupported() && hasHdrContent(bitmap) -> ActivityInfo.COLOR_MODE_HDR
            hasWideColorGamut(bitmap) -> ActivityInfo.COLOR_MODE_WIDE_COLOR_GAMUT
            else -> ActivityInfo.COLOR_MODE_DEFAULT
        }
    }

    fun resetColorMode(activity: Activity?) {
        SwipeFrameMonitor.mark("colorMode ${activity?.window?.colorMode} -> reset")
        viewerHdrHeadroomAnimator?.cancel()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            activity?.window?.setDesiredHdrHeadroom(0f)
        }
        activity?.window?.setColorMode(ActivityInfo.COLOR_MODE_DEFAULT)
    }
}
