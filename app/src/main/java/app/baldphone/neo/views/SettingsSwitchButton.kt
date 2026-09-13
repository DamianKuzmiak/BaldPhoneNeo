package app.baldphone.neo.views

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup

import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.withStyledAttributes
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.transition.TransitionManager

import app.baldphone.neo.R
import app.baldphone.neo.databinding.ViewSettingsSwitchButtonBinding
import app.baldphone.neo.utils.dpToPx

class SettingsSwitchButton
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
            setMinHeight(context.dpToPx(64))

            val paddingHorizontal = context.dpToPx(12)
            val paddingVertical = context.dpToPx(8)
            setPadding(paddingHorizontal, paddingVertical, paddingVertical, paddingHorizontal)
            setBackgroundResource(R.drawable.style_for_buttons_rectangle)

            attrs?.let {
                context.withStyledAttributes(it, R.styleable.SettingsSwitchButton, 0, 0) {
                    val title = getString(R.styleable.SettingsSwitchButton_switchTitle)
                    val desc = getString(R.styleable.SettingsSwitchButton_switchDescription)
                    descOn = getString(R.styleable.SettingsSwitchButton_switchDescriptionOn)
                    descOff = getString(R.styleable.SettingsSwitchButton_switchDescriptionOff)

                    setTitle(title ?: "")
                    if (desc != null) {
                        setDescription(desc)
                    }
                }
            }

            updateDescriptionForState(isChecked())

            ViewCompat.setAccessibilityDelegate(
                this,
                object : androidx.core.view.AccessibilityDelegateCompat() {
                    override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfoCompat) {
                        super.onInitializeAccessibilityNodeInfo(host, info)
                        info.className = "android.widget.Switch"
                        info.isCheckable = true
                        info.isChecked = isChecked()
                    }
                }
            )

            setOnClickListener {
                val newState = !isChecked()
                setChecked(newState)
                onCheckedChangeListener?.invoke(newState)
                sendAccessibilityEvent(android.view.accessibility.AccessibilityEvent.TYPE_VIEW_CLICKED)
            }
        }

        fun setTitle(title: String) {
            binding.tvTitle.text = title
        }

        fun setDescription(description: String) {
            TransitionManager.beginDelayedTransition(binding.root as ViewGroup)
            binding.tvDesc.text = description
        }

        fun setChecked(isChecked: Boolean) {
            binding.swToggle.isChecked = isChecked
            updateDescriptionForState(isChecked)
        }

        fun isChecked(): Boolean = binding.swToggle.isChecked

        fun setOnCheckedChangeListener(listener: (Boolean) -> Unit) {
            onCheckedChangeListener = listener
        }

        private fun updateDescriptionForState(isChecked: Boolean) {
            val desc = if (isChecked) descOn else descOff
            if (desc != null) {
                setDescription(desc)
            }
        }
    }
