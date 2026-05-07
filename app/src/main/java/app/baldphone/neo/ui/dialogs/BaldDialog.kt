package app.baldphone.neo.ui.dialogs

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager

import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toDrawable

import com.bald.uriah.baldphone.databinding.DialogBaldBinding
import com.bald.uriah.baldphone.views.BaldButton

/**
 * A custom BDialog replacement with a Builder API like MaterialAlertDialog and custom background color support.
 */
class BaldDialog private constructor(
    context: Context,
    private val builder: Builder
) : Dialog(context) {
    private val binding: DialogBaldBinding by lazy {
        DialogBaldBinding.inflate(LayoutInflater.from(context))
    }

    init {
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(binding.root)

        window?.apply {
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setDimAmount(0.6f)
        }

        setupContent()
        setupButtons()
        setupBackground()
        setupListeners()
    }

    fun getInputText(): String = binding.editText.text.toString()

    private fun setupContent() =
        with(binding) {
            builder.icon?.let {
                dialogIcon.setImageDrawable(it)
                dialogIcon.visibility = View.VISIBLE
            }

            builder.title?.let {
                dialogTitle.text = it
            } ?: run {
                dialogTitle.visibility = View.GONE
                titleDivider.visibility = View.GONE
            }

            builder.message?.let {
                dialogMessage.text = it
            } ?: run {
                messageScrollView.visibility = View.GONE
            }

            if (builder.showInput) {
                editText.visibility = View.VISIBLE
                editText.setText(builder.inputText)
                editText.hint = builder.inputHint
                editText.requestFocus()
            }

            builder.customView?.let { customView ->
                (customView.parent as? ViewGroup)?.removeView(customView)
                customViewContainer.addView(customView)
                customViewContainer.visibility = View.VISIBLE
            }
        }

    private fun setupButtons() =
        with(binding) {
            configureButton(buttonPositive, builder.positiveButtonText, builder.positiveButtonListener)
            configureButton(buttonNegative, builder.negativeButtonText, builder.negativeButtonListener)
            configureButton(buttonNeutral, builder.neutralButtonText, builder.neutralButtonListener)
        }

    private fun configureButton(
        button: BaldButton,
        text: CharSequence?,
        listener: ((BaldDialog) -> Unit)?
    ): Boolean {
        if (text == null) {
            button.visibility = View.GONE
            return false
        }
        button.apply {
            this.text = text
            visibility = View.VISIBLE
            setOnClickListener {
                listener?.invoke(this@BaldDialog)
                if (builder.autoDismiss) dismiss()
            }
        }
        return true
    }

    private fun setupBackground() {
        builder.backgroundColor?.let {
            binding.dialogBackground.setBackgroundColor(it)
        }
        setCancelable(builder.cancelable)
    }

    private fun setupListeners() {
        builder.onDismissListener?.let { listener ->
            setOnDismissListener { listener(this) }
        }
        builder.onCancelListener?.let { listener ->
            setOnCancelListener { listener(this) }
        }
    }

    class Builder(
        private val context: Context
    ) {
        internal var autoDismiss: Boolean = true
        internal var cancelable: Boolean = true
        internal var customView: View? = null
        internal var icon: Drawable? = null
        internal var inputHint: CharSequence? = null
        internal var inputText: CharSequence? = null
        internal var message: CharSequence? = null
        internal var negativeButtonListener: ((BaldDialog) -> Unit)? = null
        internal var negativeButtonText: CharSequence? = null
        internal var neutralButtonListener: ((BaldDialog) -> Unit)? = null
        internal var neutralButtonText: CharSequence? = null
        internal var onCancelListener: ((BaldDialog) -> Unit)? = null
        internal var onDismissListener: ((BaldDialog) -> Unit)? = null
        internal var positiveButtonListener: ((BaldDialog) -> Unit)? = null
        internal var positiveButtonText: CharSequence? = null
        internal var showInput: Boolean = false
        internal var title: CharSequence? = null

        @ColorInt
        internal var backgroundColor: Int? = null

        fun setIcon(
            @DrawableRes resId: Int
        ) = apply { this.icon = ContextCompat.getDrawable(context, resId) }

        fun setTitle(title: CharSequence) = apply { this.title = title }

        fun setTitle(
            @StringRes resId: Int
        ) = setTitle(context.getText(resId))

        fun setMessage(message: CharSequence) = apply { this.message = message }

        fun setMessage(
            @StringRes resId: Int
        ) = setMessage(context.getText(resId))

        fun setInput(show: Boolean = true, text: CharSequence? = null, hint: CharSequence? = null) =
            apply {
                this.showInput = show
                this.inputText = text
                this.inputHint = hint
            }

        fun setPositiveButton(text: CharSequence, listener: ((BaldDialog) -> Unit)? = null) =
            apply {
                this.positiveButtonText = text
                this.positiveButtonListener = listener
            }

        fun setPositiveButton(
            @StringRes resId: Int,
            listener: ((BaldDialog) -> Unit)? = null
        ) = setPositiveButton(context.getText(resId), listener)

        fun setNegativeButton(text: CharSequence, listener: ((BaldDialog) -> Unit)? = null) =
            apply {
                this.negativeButtonText = text
                this.negativeButtonListener = listener
            }

        fun setNegativeButton(
            @StringRes resId: Int,
            listener: ((BaldDialog) -> Unit)? = null
        ) = setNegativeButton(context.getText(resId), listener)

        fun setNeutralButton(text: CharSequence, listener: ((BaldDialog) -> Unit)? = null) =
            apply {
                this.neutralButtonText = text
                this.neutralButtonListener = listener
            }

        fun setNeutralButton(
            @StringRes resId: Int,
            listener: ((BaldDialog) -> Unit)? = null
        ) = setNeutralButton(context.getText(resId), listener)

        fun setCancelable(cancelable: Boolean) = apply { this.cancelable = cancelable }

        fun setBackgroundColor(
            @ColorInt color: Int
        ) = apply { this.backgroundColor = color }

        fun setOnDismissListener(listener: (BaldDialog) -> Unit) = apply { this.onDismissListener = listener }

        fun setOnCancelListener(listener: (BaldDialog) -> Unit) = apply { this.onCancelListener = listener }

        fun create(): BaldDialog = BaldDialog(context, this)

        fun show(): BaldDialog = create().apply { show() }
    }
}

/** DSL wrapper for [BaldDialog.Builder] */
fun Context.baldDialog(
    init: BaldDialog.Builder.() -> Unit
): BaldDialog.Builder = BaldDialog.Builder(this).apply(init)
