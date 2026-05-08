@file:JvmName("ViewExtensions")

package app.baldphone.neo.extensions

import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView

import androidx.core.view.AccessibilityDelegateCompat
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat

private val CLICKABLE_ROLE_DELEGATE =
    object : AccessibilityDelegateCompat() {
        override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfoCompat) {
            super.onInitializeAccessibilityNodeInfo(host, info)

            info.className =
                when (host) {
                    is ImageView -> ImageButton::class.java.name
                    else -> Button::class.java.name
                }
        }
    }

/**
 * Sets an accessibility delegate on the view so screen readers report it as a Button or ImageButton.
 */
fun View.setClickableAccessibilityRole() {
    if (this is Button || this is ImageButton) return
    if (ViewCompat.getAccessibilityDelegate(this) != null) return

    ViewCompat.setAccessibilityDelegate(this, CLICKABLE_ROLE_DELEGATE)
}
