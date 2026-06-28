package app.baldphone.neo.extensions

import android.view.View

import androidx.core.view.isVisible

private const val FADE_DURATION_MS = 300L

/**
 * Manages the visibility and alpha animations (fade-in, fade-out) for a view.
 */

fun View.fadeIn(animate: Boolean = true) {
    if (isVisible && alpha == 1f && !animate) return

    cancelAnimations()
    if (animate) {
        if (!isVisible || alpha == 0f) {
            alpha = 0f
            isVisible = true
        }
        animate()
            .alpha(1f)
            .setDuration(FADE_DURATION_MS)
            .start()
    } else {
        alpha = 1f
        isVisible = true
    }
}

fun View.fadeOut(animate: Boolean = true) {
    if (!isVisible && !animate) return

    cancelAnimations()
    if (animate) {
        animate()
            .alpha(0f)
            .setDuration(FADE_DURATION_MS)
            .withEndAction { isVisible = false }
            .start()
    } else {
        isVisible = false
        alpha = 0f
    }
}

fun View.fadeToggle(animate: Boolean = true) {
    val isEffectivelyVisible = isVisible && alpha > 0.5f
    if (isEffectivelyVisible) {
        fadeOut(animate)
    } else {
        fadeIn(animate)
    }
}

fun View.cancelAnimations() {
    animate().setListener(null).cancel()
}
