package app.baldphone.neo.core.assisttouch

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration

/**
 * Attach to any View to implement AssistTouch:
 * - < minTapMs          -> "too short" (global callback via manager)
 * - [minTapMs, longMs]  -> normal click (performClick())
 * - >= longMs           -> long press (fires while finger is down via performLongClick())
 *
 * Automatically cleans up when view is detached for memory safety.
 */
class AssistTouchDelegate(
    private val view: View,
    var isActive: Boolean,
    var minTapMs: Long,
    var longPressMs: Long,
    private val onTapTooFast: (View) -> Unit
) : View.OnTouchListener {
    private val handler = Handler(Looper.getMainLooper())
    private var touchSlop = ViewConfiguration.get(view.context).scaledTouchSlop
    private var touchSlopSq = touchSlop * touchSlop

    private var downTime = 0L
    private var downX = 0f
    private var downY = 0f
    private var activePointerId = -1
    private var longPressFired = false
    private var clickFired = false
    private var moved = false

    var externalTouchListener: View.OnTouchListener? = null

    private val earlyClickRunnable = Runnable { fireClick() }
    private val longPressRunnable = Runnable { fireLongClick() }

    private val attachStateListener =
        object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {}

            override fun onViewDetachedFromWindow(v: View) = cleanup()
        }

    init {
        view.addOnAttachStateChangeListener(attachStateListener)
        Log.d(TAG, "AssistTouchDelegate created for $view")
    }

    fun detach() {
        cleanup()
        view.removeOnAttachStateChangeListener(attachStateListener)
        view.setOnTouchListener(null)
    }

    override fun onTouch(v: View, event: MotionEvent): Boolean {
        if (externalTouchListener?.onTouch(v, event) == true) return true
        if (!isActive) return false

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                Log.d(TAG, "onTouch - ACTION_DOWN")
                val configuration = ViewConfiguration.get(v.context)
                touchSlop = configuration.scaledTouchSlop
                touchSlopSq = touchSlop * touchSlop

                activePointerId = event.getPointerId(0)
                downTime = SystemClock.uptimeMillis()
                downX = event.x
                downY = event.y
                resetState()

                v.isPressed = true
                v.parent?.requestDisallowInterceptTouchEvent(true)

                if (v.isLongClickable) {
                    handler.postDelayed(longPressRunnable, longPressMs)
                    Log.d(TAG, "onTouch - Long press scheduled for ${longPressMs}ms")
                } else {
                    handler.postDelayed(earlyClickRunnable, minTapMs)
//                    Log.d(TAG, "onTouch - Early click scheduled for ${minTapMs}ms")
                }
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (!moved) {
                    val pointerIndex = event.findPointerIndex(activePointerId)
                    if (pointerIndex != -1) {
                        val dx = event.getX(pointerIndex) - downX
                        val dy = event.getY(pointerIndex) - downY
                        if ((dx * dx) + (dy * dy) > touchSlopSq) {
                            moved = true
                            cancelInternalRunnables()
                            v.isPressed = false
                            v.parent?.requestDisallowInterceptTouchEvent(false)
                            Log.d(TAG, "Moved beyond slop")
                        }
                    }
                }
            }

            MotionEvent.ACTION_UP -> {
                cancelInternalRunnables()
                v.isPressed = false
                v.parent?.requestDisallowInterceptTouchEvent(false)

                if (moved) return true

                val duration = SystemClock.uptimeMillis() - downTime
                Log.d(TAG, "onTouch - ACTION_UP, duration=${duration}ms")

                when {
                    clickFired || longPressFired -> {
                        Log.d(TAG, "onTouch - Long press already handled")
                    }

                    duration < minTapMs -> {
                        Log.d(TAG, "onTouch - TOO SHORT ($duration < $minTapMs)")
                        onTapTooFast(v)
                    }

                    else -> {
                        if (v.isLongClickable && duration >= longPressMs) {
                            Log.d(TAG, "onTouch - fallback Long click")
                            fireLongClick()
                        } else {
                            Log.d(TAG, "onTouch - fallback click")
                            fireClick()
                        }
                    }
                }
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                Log.d(TAG, "onTouch - ACTION_CANCEL")
                cancelPress()
                return true
            }
        }
        return false
    }

    private fun fireClick() {
        if (!clickFired && !moved) {
            clickFired = true
            Log.d(TAG, "Early click fired")
            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            view.performClick()
        }
    }

    private fun fireLongClick() {
        if (!longPressFired && !moved) {
            longPressFired = true
            Log.d(TAG, "Long press fired")
            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            view.performLongClick()
        }
    }

    private fun cleanup() {
        handler.removeCallbacksAndMessages(null)
    }

    private fun cancelInternalRunnables() {
        handler.removeCallbacks(earlyClickRunnable)
        handler.removeCallbacks(longPressRunnable)
    }

    private fun cancelPress() {
        cancelInternalRunnables()
        view.isPressed = false
        view.parent?.requestDisallowInterceptTouchEvent(false)
        resetState()
    }

    private fun resetState() {
        longPressFired = false
        clickFired = false
        moved = false
    }

    companion object {
        private const val TAG = "AssistTouchDelegate"
    }
}
