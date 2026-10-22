package org.fossify.gallery.helpers

import android.os.SystemClock
import android.os.Trace
import android.util.Log
import org.fossify.gallery.BuildConfig

/**
 * Debug-only timings for All photos load paths. Filter logcat on [TAG].
 */
object LoadTimer {
    const val TAG = "AllPhotosLoad"

    @Volatile
    private var sessionStartMs: Long = 0L

    @Volatile
    private var lastMarkMs: Long = 0L

    private val enabled get() = BuildConfig.DEBUG

    fun resetSession(reason: String) {
        if (!enabled) {
            return
        }
        sessionStartMs = SystemClock.elapsedRealtime()
        lastMarkMs = sessionStartMs
        Log.i(TAG, "session start: $reason")
    }

    fun mark(label: String, extra: String = "") {
        if (!enabled) {
            return
        }
        if (sessionStartMs == 0L) {
            resetSession("implicit")
        }
        val now = SystemClock.elapsedRealtime()
        val sinceStart = now - sessionStartMs
        val sinceLast = now - lastMarkMs
        lastMarkMs = now
        val suffix = if (extra.isEmpty()) "" else " ($extra)"
        Log.i(TAG, "+${sinceStart}ms (+${sinceLast}ms) $label$suffix")
    }

    fun <T> section(label: String, block: () -> T): T {
        if (!enabled) {
            return block()
        }
        if (sessionStartMs == 0L) {
            resetSession("implicit")
        }
        Trace.beginSection(label.take(127))
        val start = SystemClock.elapsedRealtime()
        return try {
            block()
        } finally {
            Trace.endSection()
            val elapsed = SystemClock.elapsedRealtime() - start
            Log.i(TAG, "section ${elapsed}ms: $label")
        }
    }
}
