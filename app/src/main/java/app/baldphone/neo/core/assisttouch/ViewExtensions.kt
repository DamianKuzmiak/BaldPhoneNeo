package app.baldphone.neo.core.assisttouch

import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.Toast

import androidx.core.view.ViewCompat

import app.baldphone.neo.data.AccessibilityLevel
import app.baldphone.neo.data.Prefs
import app.baldphone.neo.extensions.setClickableAccessibilityRole

import com.bald.uriah.baldphone.R

/**
 * Tag key for storing [AssistTouchDelegate] reference in View.
 */
private val ASSIST_TOUCH_DELEGATE_TAG_KEY = "assist_touch_delegate_tag".hashCode()

private var currentToast: Toast? = null

/**
 * Helper to retrieve the AssistTouchDelegate from any View.
 */
val View.assistTouchDelegate: AssistTouchDelegate?
    get() = getTag(ASSIST_TOUCH_DELEGATE_TAG_KEY) as? AssistTouchDelegate

/**
 * Attach AssistTouch gesture helper to any View.
 * The delegate automatically cleans up when the view is detached (via [AssistTouchDelegate.cleanup]).
 *
 * @return AssistTouchDelegate for advanced control if needed
 */
fun View.enableAssistTouch(): AssistTouchDelegate {
    val existing = assistTouchDelegate
    if (existing != null) {
//        existing.isActive = Prefs.accessibilityLevel != AccessibilityLevel.BASIC
//        existing.minTapMs = Prefs.shortPressTimeoutMs.toLong()
//        existing.longPressMs = Prefs.longPressTimeoutMs.toLong()
        Log.v("AssistTouch", "Returning existing delegate - view already has one: $this")
        return existing
    }

    isClickable = true

    // TODO: Long press duration to be respect system setting (ViewConfiguration.getLongPressTimeout()).
    //  Users can customize this in Settings -> Accessibility -> Touch & hold delay.

    val delegate =
        AssistTouchDelegate(
            view = this,
            isActive = Prefs.accessibilityLevel != AccessibilityLevel.BASIC,
            minTapMs = Prefs.shortPressTimeoutMs.toLong(),
            longPressMs = Prefs.longPressTimeoutMs.toLong(),
            onTapTooFast = { view ->
                if (Prefs.showPressLongerHint) {
                    currentToast?.cancel()
                    val toast = Toast.makeText(view.context, R.string.press_longer, Toast.LENGTH_SHORT)
                    currentToast = toast
                    toast.show()
                }
            }
        )

    setTag(ASSIST_TOUCH_DELEGATE_TAG_KEY, delegate)
    setOnTouchListener(delegate)

    return delegate
}

/**
 * Detach the AssistTouch gesture listener. View will revert to standard Android touch behavior.
 */
fun View.disableAssistTouch() {
    val delegate = getTag(ASSIST_TOUCH_DELEGATE_TAG_KEY) as? AssistTouchDelegate
    delegate?.detach()
    setTag(ASSIST_TOUCH_DELEGATE_TAG_KEY, null)
}

/**
 * Recursively traverses the view hierarchy and enables AssistTouch on all clickable and focusable views.
 */
fun View.enableAssistTouchHierarchy() {
    val isAssistTouchActive = Prefs.accessibilityLevel != AccessibilityLevel.BASIC
    if (!isAssistTouchActive) {
        return
    }

    if (this is ViewGroup) {
        for (i in 0 until childCount) {
            getChildAt(i).enableAssistTouchHierarchy()
        }
    }

    // We only enable AssistTouch if:
    // - the view is clickable
    // - not explicitly excluded
    // Also:
    // - not already using AssistTouch
    // - not a scrollable
    // - not an input component which needs native touch handling
    if (this.isClickable && this.tag != "exclude_assist_touch") {
        if (this.assistTouchDelegate == null) {
            if (!AssistTouchExclusions.shouldExclude(this)) {
                this.enableAssistTouch()
            } else {
                Log.v("AssistTouch", "View is excluded: $this")
            }
            if (ViewCompat.getAccessibilityDelegate(this) == null) {
                this.setClickableAccessibilityRole()
            }
        } else {
            Log.v("AssistTouch", "View is already enabled: $this")
        }

        if (ViewCompat.getAccessibilityDelegate(this) == null) {
            this.setClickableAccessibilityRole()
        }
    }
}
