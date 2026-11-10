package app.baldphone.neo.views

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.view.LayoutInflater

import androidx.activity.findViewTreeOnBackPressedDispatcherOwner
import androidx.annotation.StringRes
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.content.res.use
import androidx.core.view.isVisible

import app.baldphone.neo.R
import app.baldphone.neo.data.Prefs
import app.baldphone.neo.databinding.ViewTitleBarBinding
import app.baldphone.neo.extensions.applyTopBarInsets

/**
 * A custom toolbar-like view that provides a consistent header across the application.
 * It features a title, an exit (back) button, and an optional "more" options button.
 * Button sides follow [Prefs.isRightHanded] - exit on the right for `true`, mirrored otherwise.
 *
 * XML Attributes:
 * - `titleBarTitle`: The text to display in the center of the bar.
 * - `titleBarBackgroundColor`: The background color for the entire bar.
 */
class TitleBarView
    @JvmOverloads
    constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0
    ) : ConstraintLayout(context, attrs, defStyleAttr) {
        private val binding = ViewTitleBarBinding.inflate(LayoutInflater.from(context), this)

        init {
            elevation = resources.getDimension(R.dimen.title_bar_elevation)

            val horizontalPadding = resources.getDimensionPixelSize(R.dimen.title_bar_side_padding)
            setPaddingRelative(horizontalPadding, paddingTop, horizontalPadding, paddingBottom)

            binding.btnExit.setOnClickListener {
                findViewTreeOnBackPressedDispatcherOwner()?.onBackPressedDispatcher?.onBackPressed()
            }

            attrs?.let { attributeSet ->
                context.obtainStyledAttributes(attributeSet, R.styleable.TitleBarView).use { a ->
                    a.getString(R.styleable.TitleBarView_titleBarTitle)?.let { title ->
                        setTitle(title)
                    }

                    // Background: Fallback to a Gray color
                    val backgroundColor = a.getColor(R.styleable.TitleBarView_titleBarBackgroundColor, Color.GRAY)
                    setBackgroundColor(backgroundColor)
                }
            }

            if (!Prefs.isRightHanded) swapButtonSides()

            applyTopBarInsets()
        }

        /**
         * Sets the title text from a string resource.
         */
        fun setTitle(
            @StringRes resId: Int
        ) {
            binding.txtTitle.setText(resId)
        }

        /**
         * Sets the title text from a string.
         */
        fun setTitle(title: String) {
            binding.txtTitle.text = title
        }

        /**
         * Sets the visibility of the 'More' options button.
         */
        fun showMoreButton(isVisible: Boolean = true) {
            binding.btnMore.isVisible = isVisible
        }

        /**
         * Registers a callback to be invoked when the 'More' options button is clicked.
         * Providing a non-null listener automatically makes the button visible via [showMoreButton].
         */
        fun setOnMoreClickListener(listener: OnClickListener?) {
            binding.btnMore.setOnClickListener(listener)
            if (listener != null) showMoreButton()
        }

        /**
         * Overrides the default exit behavior with a custom click listener.
         * The default action is to trigger a back press.
         */
        fun setOnExitClickListener(listener: OnClickListener?) {
            binding.btnExit.setOnClickListener(listener)
        }

        /** Swaps sides of the exit and 'More' buttons (exit on the left, 'More' on the right) */
        private fun swapButtonSides() {
            ConstraintSet().apply {
                clone(this@TitleBarView)

                clear(R.id.btnExit, ConstraintSet.END)
                connect(R.id.btnExit, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START)

                clear(R.id.btnMore, ConstraintSet.START)
                connect(R.id.btnMore, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END)

                connect(R.id.txtTitle, ConstraintSet.START, R.id.btnExit, ConstraintSet.END)
                connect(R.id.txtTitle, ConstraintSet.END, R.id.btnMore, ConstraintSet.START)

                applyTo(this@TitleBarView)
            }
        }
    }
