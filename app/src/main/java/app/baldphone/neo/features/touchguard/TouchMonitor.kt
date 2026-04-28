package app.baldphone.neo.features.touchguard

import android.os.SystemClock

import java.util.ArrayDeque

/**
 * Tracks touch-down timestamps within a sliding time window and determines
 * whether the accidental-tap threshold has been reached.
 *
 * This class is purely algorithmic – it has no dependency on Android UI or
 * sensor APIs and is therefore straightforward to unit-test.
 */
internal class TouchMonitor {
    private val recentTouches = ArrayDeque<Long>()

    /** Dynamic threshold that gets raised every time the guard fires, reducing sensitivity for repeat triggers. */
    var threshold: Int = TAP_THRESHOLD
        private set

    /** Records a touch-down event and returns `true` if the threshold has just been exceeded. */
    fun recordTouch(): Boolean {
        val now = SystemClock.elapsedRealtime()
        recentTouches.addLast(now)

        // Evict timestamps outside the sliding window.
        while (recentTouches.isNotEmpty() && (now - recentTouches.first()) > WINDOW_MS) {
            recentTouches.removeFirst()
        }

        return if (recentTouches.size > threshold) {
            recentTouches.clear()
            true
        } else {
            false
        }
    }

    /** Clears the current touch history without affecting the threshold. */
    fun reset() {
        recentTouches.clear()
    }

    /**
     * Raises the threshold by [step], capped at [TAP_LIMIT].
     * Called after the guard has been triggered and subsequently dismissed.
     */
    fun raiseThreshold(step: Int = 2) {
        threshold = (threshold + step).coerceAtMost(TAP_LIMIT)
    }

    /** Resets the threshold to its default minimum value. */
    fun resetThreshold() {
        threshold = TAP_THRESHOLD
    }

    companion object {
        /** Width of the sliding window in milliseconds. */
        private const val WINDOW_MS = 3_000L

        /** Starting (minimum) tap threshold before triggering. */
        private const val TAP_THRESHOLD = 3

        /** Upper limit the threshold may be raised to. */
        private const val TAP_LIMIT = 9
    }
}
