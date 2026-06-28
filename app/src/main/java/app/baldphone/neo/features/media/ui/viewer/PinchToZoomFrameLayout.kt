package app.baldphone.neo.features.media.ui.viewer

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.widget.FrameLayout

/**
 * A FrameLayout wrapper that conditionally filters [requestDisallowInterceptTouchEvent]
 * calls from its children to block parent ViewPager2 swiping when:
 * - The child image is zoomed in ([isZoomed] = true), or
 * - A multi-touch (pinch) gesture is in progress.
 */
class PinchToZoomFrameLayout
    @JvmOverloads
    constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0
    ) : FrameLayout(context, attrs, defStyleAttr) {
        /** Set by the ViewHolder when the child's zoom scale changes. */
        var isZoomed: Boolean = false

        private var isMultiTouch: Boolean = false

        override fun dispatchTouchEvent(event: MotionEvent): Boolean {
            if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                isMultiTouch = false
            }
            if (event.pointerCount > 1) {
                isMultiTouch = true
                parent?.requestDisallowInterceptTouchEvent(true)
            }
            return super.dispatchTouchEvent(event)
        }

        override fun requestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {
            if (isZoomed || isMultiTouch) {
                super.requestDisallowInterceptTouchEvent(true)
            } else {
                super.requestDisallowInterceptTouchEvent(false)
            }
        }
    }
