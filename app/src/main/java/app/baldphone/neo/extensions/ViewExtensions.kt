@file:JvmName("ViewExtensions")

package app.baldphone.neo.extensions

import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView

import androidx.core.view.AccessibilityDelegateCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.core.view.updatePadding

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

/**
 * Applies top window insets as padding to this view.
 */
fun View.applyTopBarInsets() {
    val initialPaddingTop = paddingTop
    ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
        val types = WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
        view.updatePadding(top = initialPaddingTop + insets.getInsets(types).top)
        insets
    }
    ViewCompat.requestApplyInsets(this)
}

/**
 * Applies edge-to-edge window insets to this view group, handling side insets and bottom insets for tagged children.
 */
fun ViewGroup.applyEdgeToEdgeInsets() {
    ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
        val typesMask = WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
        val targetInsets = insets.getInsets(typesMask)

        view.updatePadding(left = targetInsets.left, right = targetInsets.right, bottom = targetInsets.bottom)

        insets.inset(targetInsets.left, 0, targetInsets.right, targetInsets.bottom)
    }
    ViewCompat.requestApplyInsets(this)
}
