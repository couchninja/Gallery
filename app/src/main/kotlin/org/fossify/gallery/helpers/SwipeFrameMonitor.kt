package org.fossify.gallery.helpers

import android.app.Activity
import android.graphics.Color
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.util.TypedValue
import android.view.FrameMetrics
import android.view.Gravity
import android.view.ViewGroup
import android.view.Window
import android.widget.FrameLayout
import android.widget.TextView
import org.fossify.gallery.extensions.config
import kotlin.math.roundToInt

/**
 * Frame timing for viewer swipes, enabled by [Config.showSwipeFpsOverlay]. Filter logcat on [TAG].
 * Main-thread only, except [onFrameMetrics] which runs on [metricsHandler]'s thread.
 */
object SwipeFrameMonitor {
    const val TAG = "SwipeFps"

    private const val SLOW_MESSAGE_MS = 8L
    private const val OVERLAY_REFRESH_MS = 250L
    private const val MAX_LOGGED_FRAMES = 12

    private class Frame(
        val vsyncMs: Long,
        val totalMs: Double,
        val inputMs: Double,
        val animationMs: Double,
        val layoutMs: Double,
        val drawMs: Double,
        val syncMs: Double,
        val commandMs: Double,
        val swapMs: Double,
        val gpuMs: Double,
        val deadlineMs: Double,
    )

    private var window: Window? = null
    private var overlay: TextView? = null
    private var metricsHandler: Handler? = null
    private val metricsListener = Window.OnFrameMetricsAvailableListener { _, metrics, _ -> onFrameMetrics(metrics) }

    @Volatile
    private var active = false
    private var swipeStartMs = 0L
    private val frames = ArrayList<Frame>()
    private val events = ArrayList<String>()
    private val framesLock = Any()

    private var messageStartMs = 0L
    private var messageDescription = ""

    private val overlayUpdater = object : Runnable {
        override fun run() {
            updateOverlay()
            if (active) {
                overlay?.postDelayed(this, OVERLAY_REFRESH_MS)
            }
        }
    }

    fun attach(activity: Activity) {
        detach()
        if (!activity.config.showSwipeFpsOverlay) return
        window = activity.window
        metricsHandler = Handler(Looper.getMainLooper())
        activity.window.addOnFrameMetricsAvailableListener(metricsListener, metricsHandler)

        val density = activity.resources.displayMetrics.density
        overlay = TextView(activity).apply {
            setTextColor(Color.GREEN)
            setBackgroundColor(0x99000000.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setPadding((4 * density).toInt(), (2 * density).toInt(), (4 * density).toInt(), (2 * density).toInt())
            text = "fps –"
        }
        // Fixed size so text updates never request a layout pass during the swipe being measured.
        val params = FrameLayout.LayoutParams((150 * density).toInt(), (34 * density).toInt(), Gravity.TOP or Gravity.END).apply {
            topMargin = (80 * density).toInt()
        }
        (activity.window.decorView as ViewGroup).addView(overlay, params)
    }

    fun detach() {
        if (window == null) return
        stopSwipe()
        window?.removeOnFrameMetricsAvailableListener(metricsListener)
        overlay?.let { (it.parent as? ViewGroup)?.removeView(it) }
        window = null
        overlay = null
        metricsHandler = null
    }

    fun startSwipe() {
        if (active || window == null) return
        active = true
        swipeStartMs = SystemClock.uptimeMillis()
        synchronized(framesLock) {
            frames.clear()
            events.clear()
        }
        Looper.getMainLooper().setMessageLogging { line -> onLooperMessage(line) }
        overlay?.removeCallbacks(overlayUpdater)
        overlay?.post(overlayUpdater)
    }

    fun stopSwipe() {
        if (!active) return
        active = false
        Looper.getMainLooper().setMessageLogging(null)
        overlay?.removeCallbacks(overlayUpdater)
        // FrameMetrics arrive a frame or two late; give the last frames time to land.
        metricsHandler?.postDelayed({ logSummary() }, 100)
    }

    fun mark(label: String) {
        if (!active) return
        synchronized(framesLock) {
            events.add("+${SystemClock.uptimeMillis() - swipeStartMs}ms $label")
        }
    }

    private fun onLooperMessage(line: String) {
        if (line.startsWith(">>>>> Dispatching")) {
            messageStartMs = SystemClock.uptimeMillis()
            messageDescription = line
        } else if (line.startsWith("<<<<< Finished") && messageStartMs != 0L) {
            val duration = SystemClock.uptimeMillis() - messageStartMs
            if (duration >= SLOW_MESSAGE_MS) {
                mark("slow main-thread message ${duration}ms: ${messageDescription.removePrefix(">>>>> Dispatching to ")}")
            }
            messageStartMs = 0L
        }
    }

    private fun onFrameMetrics(metrics: FrameMetrics) {
        if (!active) return
        fun ms(id: Int) = metrics.getMetric(id) / 1_000_000.0
        val deadline = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) ms(FrameMetrics.DEADLINE) else 0.0
        val frame = Frame(
            vsyncMs = metrics.getMetric(FrameMetrics.VSYNC_TIMESTAMP) / 1_000_000,
            totalMs = ms(FrameMetrics.TOTAL_DURATION),
            inputMs = ms(FrameMetrics.INPUT_HANDLING_DURATION),
            animationMs = ms(FrameMetrics.ANIMATION_DURATION),
            layoutMs = ms(FrameMetrics.LAYOUT_MEASURE_DURATION),
            drawMs = ms(FrameMetrics.DRAW_DURATION),
            syncMs = ms(FrameMetrics.SYNC_DURATION),
            commandMs = ms(FrameMetrics.COMMAND_ISSUE_DURATION),
            swapMs = ms(FrameMetrics.SWAP_BUFFERS_DURATION),
            gpuMs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) ms(FrameMetrics.GPU_DURATION) else 0.0,
            deadlineMs = deadline,
        )
        synchronized(framesLock) {
            frames.add(frame)
        }
    }

    private fun currentFps(snapshot: List<Frame>): Int {
        if (snapshot.size < 2) return 0
        val spanMs = snapshot.last().vsyncMs - snapshot.first().vsyncMs
        return if (spanMs <= 0) 0 else ((snapshot.size - 1) * 1000.0 / spanMs).roundToInt()
    }

    private fun updateOverlay() {
        val now = SystemClock.uptimeMillis()
        val recent = synchronized(framesLock) { frames.filter { now - it.vsyncMs <= 500 } }
        val worst = recent.maxOfOrNull { it.totalMs } ?: 0.0
        overlay?.text = "fps ${currentFps(recent)}  worst ${"%.1f".format(worst)}ms"
    }

    private fun logSummary() {
        val snapshot: List<Frame>
        val eventSnapshot: List<String>
        synchronized(framesLock) {
            snapshot = frames.toList()
            eventSnapshot = events.toList()
        }
        if (snapshot.size < 2) {
            Log.i(TAG, "swipe: ${snapshot.size} frames, nothing to report")
            return
        }

        val gaps = snapshot.zipWithNext { a, b -> b.vsyncMs - a.vsyncMs }
        val medianGap = gaps.sorted()[gaps.size / 2].coerceAtLeast(1)
        val droppedVsyncs = gaps.sumOf { ((it.toDouble() / medianGap).roundToInt() - 1).coerceAtLeast(0) }
        val overDeadline = snapshot.count { it.deadlineMs > 0 && it.totalMs > it.deadlineMs }
        val deadlines = snapshot.map { it.deadlineMs.roundToInt() }.filter { it > 0 }.groupingBy { it }.eachCount()

        Log.i(
            TAG,
            "swipe: ${snapshot.size} frames in ${snapshot.last().vsyncMs - snapshot.first().vsyncMs}ms, avg ${currentFps(snapshot)} fps, " +
                "median vsync gap ${medianGap}ms, ~$droppedVsyncs missed vsyncs, $overDeadline frames over deadline, " +
                "max gap ${gaps.max()}ms, deadlines(ms→count)=$deadlines"
        )

        val worstFrames = snapshot.indices
            .filter { i -> (i > 0 && gaps[i - 1] > medianGap * 1.5) || (snapshot[i].deadlineMs > 0 && snapshot[i].totalMs > snapshot[i].deadlineMs) }
            .take(MAX_LOGGED_FRAMES)
        worstFrames.forEach { i ->
            val f = snapshot[i]
            val gap = if (i > 0) gaps[i - 1] else 0
            Log.i(
                TAG,
                "  slow frame @+${f.vsyncMs - swipeStartMs}ms gap=${gap}ms total=${"%.1f".format(f.totalMs)} " +
                    "input=${"%.1f".format(f.inputMs)} anim=${"%.1f".format(f.animationMs)} layout=${"%.1f".format(f.layoutMs)} " +
                    "draw=${"%.1f".format(f.drawMs)} sync=${"%.1f".format(f.syncMs)} cmd=${"%.1f".format(f.commandMs)} " +
                    "swap=${"%.1f".format(f.swapMs)} gpu=${"%.1f".format(f.gpuMs)} deadline=${"%.1f".format(f.deadlineMs)}"
            )
        }
        eventSnapshot.forEach { Log.i(TAG, "  event $it") }
    }
}
