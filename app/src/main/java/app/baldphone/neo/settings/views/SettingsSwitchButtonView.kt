package app.baldphone.neo.settings.views

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityEvent

import androidx.annotation.LayoutRes
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.withStyledAttributes
import androidx.core.view.AccessibilityDelegateCompat
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.core.view.children
import androidx.core.view.isEmpty
import androidx.core.view.isVisible
import androidx.transition.TransitionManager

import app.baldphone.neo.R
import app.baldphone.neo.databinding.ViewSettingsSwitchButtonBinding

/**
 * A settings switch component supporting a title, toggle, optional content slot (dimmed when disabled),
 * and description.
 *
 * Descriptions can be static (switchDescription) or dynamic based on state (switchDescriptionOn/switchDescriptionOff).
 * Dynamic mode takes precedence.
 *
 * XML Attributes:
 * - `switchTitle`: Main text of the setting.
 * - `switchDescription`: Static description.
 * - `switchDescriptionOn`: Description shown when ON.
 * - `switchDescriptionOff`: Description shown when OFF.
 */
class SettingsSwitchButtonView
    @JvmOverloads
    constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0
    ) : ConstraintLayout(context, attrs, defStyleAttr) {
        private val binding: ViewSettingsSwitchButtonBinding =
            ViewSettingsSwitchButtonBinding.inflate(LayoutInflater.from(context), this)

        private var onCheckedChangeListener: ((Boolean) -> Unit)? = null
        private var descOn: String? = null
        private var descOff: String? = null

        init {
            setMinHeight(resources.getDimensionPixelSize(R.dimen.settings_switch_min_height))

            val paddingHorizontal = resources.getDimensionPixelSize(R.dimen.settings_switch_padding_horizontal)
            val paddingVertical = resources.getDimensionPixelSize(R.dimen.settings_switch_padding_vertical)
            setPadding(paddingHorizontal, paddingVertical, paddingHorizontal, paddingVertical)
            setBackgroundResource(R.drawable.bg_settings_item_card)

            attrs?.let {
                context.withStyledAttributes(it, R.styleable.SettingsSwitchButtonView, 0, 0) {
                    val title = getString(R.styleable.SettingsSwitchButtonView_switchTitle)
                    val desc = getString(R.styleable.SettingsSwitchButtonView_switchDescription)
                    descOn = getString(R.styleable.SettingsSwitchButtonView_switchDescriptionOn)
                    descOff = getString(R.styleable.SettingsSwitchButtonView_switchDescriptionOff)

                    binding.tvTitle.text = title

                    // A static switchDescription is ignored when On/Off variants are set.
                    // In dynamic mode the description is driven by updateDescriptionForState() below.
                    if (descOn == null && descOff == null && desc != null) {
                        setDescription(desc)
                    }
                }
            }

            updateDescriptionForState(isChecked())
            updateContentState(isChecked())

            ViewCompat.setAccessibilityDelegate(
                this,
                object : AccessibilityDelegateCompat() {
                    override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfoCompat) {
                        super.onInitializeAccessibilityNodeInfo(host, info)
                        info.className = "android.widget.Switch"
                        info.isCheckable = true
                        info.isChecked = isChecked()
                        info.stateDescription = checkedStateDescription(isChecked())
                    }
                }
            )

            setOnClickListener {
                val newState = !isChecked()
                setChecked(newState)
                onCheckedChangeListener?.invoke(newState)
            }
        }

        /** Sets the current checked state of the switch. */
        fun setChecked(isChecked: Boolean) {
            binding.swToggle.isChecked = isChecked
            updateDescriptionForState(isChecked)
            updateContentState(isChecked)

            // Notify accessibility services that the node content changed due to programmatic state changes
            sendAccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED)
        }

        /** Sets a listener to be invoked when the switch state changes. */
        fun setOnCheckedChangeListener(listener: (Boolean) -> Unit) {
            onCheckedChangeListener = listener
        }

        /**
         * Inflates [layoutRes] into the content slot below the description and returns its root.
         * The slot is enabled/disabled and dimmed automatically based on the switch state.
         */
        fun setContentView(
            @LayoutRes layoutRes: Int
        ): View {
            val slot = binding.contentSlot
            slot.removeAllViews()
            LayoutInflater.from(context).inflate(layoutRes, slot, true)
            slot.isVisible = true
            updateContentState(isChecked())
            return slot.getChildAt(0)
        }

        /** Shows or hides the content slot on demand */
        fun setContentVisible(visible: Boolean) {
            binding.contentSlot.isVisible = visible
        }

        private fun isChecked(): Boolean = binding.swToggle.isChecked

        private fun checkedStateDescription(isChecked: Boolean): CharSequence =
            context.getString(if (isChecked) R.string.on else R.string.off)

        private fun updateDescriptionForState(isChecked: Boolean) {
            val desc = if (isChecked) descOn else descOff
            if (desc != null) {
                setDescription(desc)
            }
        }

        private fun setDescription(description: String) {
            if (binding.tvDesc.text?.toString() == description) return
            if (isAttachedToWindow) {
                TransitionManager.beginDelayedTransition(binding.root as ViewGroup)
            }
            binding.tvDesc.text = description
        }

        private fun updateContentState(isChecked: Boolean) {
            val slot = binding.contentSlot
            // A hidden slot must still have its enabled/alpha state kept in sync for when it becomes visible again.
            if (slot.isEmpty()) return

            slot.alpha = if (isChecked) ALPHA_ENABLED else ALPHA_DISABLED
            slot.setChildrenEnabled(isChecked)
        }

        private fun View.setChildrenEnabled(enabled: Boolean) {
            isEnabled = enabled
            if (this is ViewGroup) {
                children.forEach { it.setChildrenEnabled(enabled) }
            }
        }

        companion object {
            private const val ALPHA_ENABLED = 1.0f
            private const val ALPHA_DISABLED = 0.5f
        }
    }
