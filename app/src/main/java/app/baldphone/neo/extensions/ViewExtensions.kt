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

private val DELEGATES_CACHE = mutableMapOf<AccessibilityRole?, AccessibilityDelegateCompat>()

private fun getOrCreateDelegate(role: AccessibilityRole?): AccessibilityDelegateCompat =
    DELEGATES_CACHE.getOrPut(role) {
        object : AccessibilityDelegateCompat() {
            override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfoCompat) {
                super.onInitializeAccessibilityNodeInfo(host, info)

                val targetRoleName =
                    when {
                        role != null -> role.className
                        host is ImageView -> AccessibilityRole.IMAGE_BUTTON.className
                        else -> AccessibilityRole.BUTTON.className
                    }

                info.className = targetRoleName

                if (targetRoleName == AccessibilityRole.RADIO_BUTTON.className) {
                    info.isCheckable = true
                    info.isChecked = host.isSelected
                }
            }
        }
    }

/**
 * Sets an accessibility delegate to the view that reports it as a specific accessibility role.
 */
fun View.setClickableAccessibilityRole(role: AccessibilityRole? = null) {
    if (this is Button || this is ImageButton) return

    if (ViewCompat.getAccessibilityDelegate(this) != null) return

    val delegate = getOrCreateDelegate(role)
    ViewCompat.setAccessibilityDelegate(this, delegate)
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
        val sideInsetsMask = WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
        val sideInsets = insets.getInsets(sideInsetsMask)

        view.updatePadding(left = sideInsets.left, right = sideInsets.right)

        val systemBarsInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars())
        val rootLayout = (view as ViewGroup).getChildAt(0)

        if (rootLayout != null) {
            val edgeToEdgeTarget = rootLayout.findViewWithTag<View>("edgeToEdge")
            if (edgeToEdgeTarget != null) {
                rootLayout.updatePadding(bottom = 0)
                edgeToEdgeTarget.updatePadding(bottom = systemBarsInsets.bottom)
            } else if (rootLayout.findViewWithTag<View>("noEdgeToEdge") == null) {
                rootLayout.updatePadding(bottom = systemBarsInsets.bottom)
            }
        }

        insets.inset(sideInsets.left, 0, sideInsets.right, 0)
    }
    ViewCompat.requestApplyInsets(this)
}

/**
 * Applies bottom window insets as padding to this view.
 */
fun View.applyBottomInsets() {
    val initialPaddingBottom = paddingBottom
    ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
        val types = WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
        view.updatePadding(bottom = initialPaddingBottom + insets.getInsets(types).bottom)
        insets
    }
    ViewCompat.requestApplyInsets(this)
}

/**
 * Applies bottom window insets as margin to this view.
 */
fun View.applyBottomInsetsAsMargin() {
    val initialMarginBottom = (layoutParams as? ViewGroup.MarginLayoutParams)?.bottomMargin ?: 0
    ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
        val types = WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
        (view.layoutParams as? ViewGroup.MarginLayoutParams)?.let { params ->
            params.bottomMargin = initialMarginBottom + insets.getInsets(types).bottom
            view.layoutParams = params
        }
        insets
    }
    ViewCompat.requestApplyInsets(this)
}
