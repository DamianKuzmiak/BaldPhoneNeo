package app.baldphone.neo.ui.dialogs

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Looper
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast

import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat

import java.lang.ref.WeakReference

import com.google.android.material.shape.CornerFamily
import com.google.android.material.shape.MaterialShapeDrawable
import com.google.android.material.snackbar.Snackbar

import com.bald.uriah.baldphone.R

object BaldSnackbar {
    enum class Type { ERROR, WARNING, INFO, SUCCESS }

    @JvmField
    val TYPE_ERROR = Type.ERROR
    val TYPE_WARNING = Type.WARNING

    @JvmField
    val TYPE_INFO = Type.INFO
    val TYPE_SUCCESS = Type.SUCCESS

    const val LENGTH_LONG = Snackbar.LENGTH_LONG
    const val LENGTH_SHORT = Snackbar.LENGTH_SHORT

    private var snackbarRef: WeakReference<Snackbar>? = null

    /**
     * Primary entry point for Activities with CharSequence.
     */
    fun show(
        activity: Activity,
        message: CharSequence,
        type: Type = Type.INFO,
        duration: Int = LENGTH_SHORT
    ) {
        val rootView = activity.findViewById<View>(android.R.id.content)
        if (rootView != null && !activity.isFinishing && !activity.isDestroyed) {
            show(rootView, message, type, duration)
        } else {
            if (activity.isFinishing || activity.isDestroyed) {
                Log.w("BaldNotice", "Activity is finishing or destroyed, falling back to Toast")
            } else {
                Log.e("BaldNotice", "Snackbar fallback to Toast: android.R.id.content root view not found")
            }
            showToastFallback(activity, message, duration)
        }
    }

    /**
     * Primary entry point for Activities with String Resource.
     */
    fun show(
        activity: Activity,
        @StringRes resId: Int,
        type: Type = Type.INFO,
        duration: Int = LENGTH_SHORT
    ) {
        show(activity, activity.getText(resId), type, duration)
    }

    /**
     * Backup entry point for Contexts with CharSequence. Performs activity lookup.
     */
    fun show(
        context: Context,
        message: CharSequence,
        type: Type = Type.INFO,
        duration: Int = LENGTH_SHORT
    ) {
        val activity = findActivity(context)
        if (activity != null) {
            show(activity, message, type, duration)
        } else {
            Log.e("BaldNotice", "Snackbar fallback to Toast: Could not resolve Activity from context")
            showToastFallback(context, message, duration)
        }
    }

    /**
     * Backup entry point for Contexts with String Resource. Performs activity lookup.
     */
    fun show(
        context: Context,
        @StringRes resId: Int,
        type: Type = Type.INFO,
        duration: Int = LENGTH_SHORT
    ) {
        show(context, context.getText(resId), type, duration)
    }

    /**
     * Shows a snackbar on a specific view. Handles styling and thread switching.
     */
    fun show(
        view: View,
        message: CharSequence,
        type: Type = Type.INFO,
        duration: Int = LENGTH_SHORT
    ) {
        val showLogic = {
            snackbarRef?.get()?.dismiss()

            val snackbar = Snackbar.make(view, message, duration)
            val sView = snackbar.view
            val context = sView.context

            ViewCompat.setBackgroundTintList(
                sView,
                ContextCompat.getColorStateList(context, backgroundColorRes(type))
            )

            val radius = context.resources.getDimension(R.dimen.snackbar_corner_radius)
            (sView.background as? MaterialShapeDrawable)?.apply {
                shapeAppearanceModel =
                    shapeAppearanceModel.toBuilder().setAllCorners(CornerFamily.ROUNDED, radius).build()
            }

            (sView.layoutParams as? ViewGroup.MarginLayoutParams)?.let {
                val margin = context.resources.getDimensionPixelSize(R.dimen.snackbar_margin)
                it.setMargins(margin, margin, margin, margin)
                sView.layoutParams = it
            }

            sView.findViewById<TextView>(com.google.android.material.R.id.snackbar_text)?.apply {
                val typedValue = TypedValue()
                context.theme.resolveAttribute(com.google.android.material.R.attr.colorOnSurface, typedValue, true)
                val textColor = typedValue.data
                setTextColor(textColor)
                setTextSize(TypedValue.COMPLEX_UNIT_PX, context.resources.getDimension(R.dimen.snackbar_text_size))
                maxLines = 3
                textAlignment = View.TEXT_ALIGNMENT_CENTER
                gravity = Gravity.CENTER_HORIZONTAL

                ContextCompat.getDrawable(context, iconRes(type))?.let { icon ->
                    icon.setTint(textColor)
                    val size = context.resources.getDimensionPixelSize(R.dimen.snackbar_icon_size)
                    icon.setBounds(0, 0, size, size)
                    setCompoundDrawables(icon, null, null, null)
                    compoundDrawablePadding = context.resources.getDimensionPixelSize(R.dimen.snackbar_icon_padding)
                }
            }

            snackbar.show()
            snackbarRef = WeakReference(snackbar)
        }

        if (Looper.myLooper() == Looper.getMainLooper()) {
            showLogic()
        } else {
            view.post(showLogic)
        }
    }

    private fun findActivity(context: Context): Activity? {
        var currentContext = context
        while (currentContext is ContextWrapper) {
            if (currentContext is Activity) return currentContext
            currentContext = currentContext.baseContext
        }
        return null
    }

    private fun showToastFallback(context: Context, message: CharSequence, duration: Int) {
        val toastDuration = if (duration == LENGTH_LONG) Toast.LENGTH_LONG else Toast.LENGTH_SHORT
        Toast.makeText(context.applicationContext, message, toastDuration).show()
    }

    @ColorRes
    private fun backgroundColorRes(type: Type) =
        when (type) {
            Type.ERROR -> R.color.toast_error
            Type.WARNING -> R.color.toast_warning
            Type.INFO -> R.color.toast_info
            Type.SUCCESS -> R.color.toast_success
        }

    @DrawableRes
    private fun iconRes(type: Type) =
        when (type) {
            Type.ERROR -> R.drawable.error_on_background
            Type.WARNING -> R.drawable.ic_warning
            Type.INFO -> R.drawable.ic_info
            Type.SUCCESS -> R.drawable.check_on_button
        }
}
