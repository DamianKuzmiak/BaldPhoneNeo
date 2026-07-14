package app.baldphone.neo.ui.menu

import android.content.Context
import android.content.ContextWrapper
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.PopupWindow

import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.graphics.drawable.toDrawable
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.core.view.isVisible
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner

import app.baldphone.neo.utils.dpToPx

import com.bald.uriah.baldphone.R
import com.bald.uriah.baldphone.databinding.ItemActionMenuBinding
import com.bald.uriah.baldphone.databinding.ItemActionMenuDividerBinding
import com.bald.uriah.baldphone.databinding.ViewActionMenuBinding
import com.bald.uriah.baldphone.databinding.ViewDividerBinding

/**
 * A custom popup window used to display a list of menu actions: options, toggles, and separators.
 */
class ActionMenu(
    context: Context,
    actionMenuItems: List<ActionMenuItem>,
    showCancel: Boolean = true,
    private val onDismissListener: (() -> Unit)? = null,
    private val onClickListener: ((ActionMenuItem) -> Unit)? = null
) : DefaultLifecycleObserver {
    private val binding = ViewActionMenuBinding.inflate(LayoutInflater.from(context))
    private val lifecycleOwner = context.findLifecycleOwner()

    private val cancelItem =
        ActionMenuItem.Option(
            icon = MenuIcon.Resource(android.R.drawable.ic_menu_close_clear_cancel),
            labelRes = R.string.action_close
        )

    private val items =
        if (showCancel) {
            actionMenuItems + ActionMenuItem.Separator + cancelItem
        } else {
            actionMenuItems
        }

    private val popupWindow =
        PopupWindow(
            binding.root,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            true
        ).apply {
            setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
            isOutsideTouchable = true
            setOnDismissListener {
                cleanup()
                onDismissListener?.invoke()
            }
        }

    init {
        populateMenu(context)
        lifecycleOwner?.lifecycle?.addObserver(this)
    }

    private fun populateMenu(context: Context) {
        val inflater = LayoutInflater.from(context)

        items.forEachIndexed { index, item ->
            if (index > 0) {
                if (item !is ActionMenuItem.Separator && items[index - 1] !is ActionMenuItem.Separator) {
                    ViewDividerBinding.inflate(inflater, binding.container, true)
                }
            }

            if (item is ActionMenuItem.Separator) {
                ItemActionMenuDividerBinding.inflate(inflater, binding.container, true)
            } else {
                val itemBinding = ItemActionMenuBinding.inflate(inflater, binding.container, true)
                bindItemView(itemBinding, item)
            }
        }
    }

    private fun bindItemView(itemBinding: ItemActionMenuBinding, item: ActionMenuItem) {
        val root = itemBinding.root

        root.isEnabled = item.enabled
        root.alpha = if (item.enabled) 1.0f else 0.5f

        val tint = item.iconTint
        if (tint != null) {
            itemBinding.icon.setColorFilter(tint)
        } else {
            itemBinding.icon.clearColorFilter()
        }

        when (item) {
            is ActionMenuItem.Option -> {
                when (val icon = item.icon) {
                    is MenuIcon.Drawable -> itemBinding.icon.setImageDrawable(icon.drawable)
                    is MenuIcon.Resource -> itemBinding.icon.setImageResource(icon.resId)
                    null -> itemBinding.icon.setImageDrawable(null)
                }
                itemBinding.label.setText(item.labelRes)
                itemBinding.switchWidget.isVisible = false
            }

            is ActionMenuItem.Toggle -> {
                itemBinding.icon.setImageResource(item.iconRes)
                itemBinding.label.setText(item.labelRes)
                itemBinding.switchWidget.isVisible = true
                itemBinding.switchWidget.isChecked = item.checked

                ViewCompat.setAccessibilityDelegate(
                    root,
                    object : androidx.core.view.AccessibilityDelegateCompat() {
                        override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfoCompat) {
                            super.onInitializeAccessibilityNodeInfo(host, info)
                            info.className = "android.widget.Switch"
                            info.isCheckable = true
                            info.isChecked = item.checked
                        }
                    }
                )
            }

            else -> {}
        }

        root.setOnClickListener {
            if (item.enabled) {
                if (item is ActionMenuItem.Toggle) {
                    item.toggleState()
                    itemBinding.switchWidget.isChecked = item.checked
                    root.sendAccessibilityEvent(android.view.accessibility.AccessibilityEvent.TYPE_VIEW_CLICKED)
                }
                onMenuItemClicked(item)
            }
        }
    }

    /** Displays the action menu popup anchored to the specified view. */
    fun show(anchor: View) {
        if (!anchor.isAttachedToWindow) return

        val rootBounds = Rect()
        anchor.rootView.getWindowVisibleDisplayFrame(rootBounds)

        val marginPx = anchor.context.dpToPx(8)
        val maxWidth = rootBounds.width() - (marginPx * 2)
        val minWidth = binding.root.minimumWidth

        // Measure once to get the "natural" width and height
        binding.root.measure(
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )

        popupWindow.apply {
            width = binding.root.measuredWidth.coerceIn(minWidth, maxWidth)
            height = binding.root.measuredHeight
            showAsDropDown(anchor, 0, 0)
        }

        val container = popupWindow.contentView.rootView
        val layoutParams = container.layoutParams as? WindowManager.LayoutParams
        if (layoutParams != null) {
            layoutParams.flags = layoutParams.flags or WindowManager.LayoutParams.FLAG_DIM_BEHIND
            layoutParams.dimAmount = 0.4f
            val windowManager = anchor.context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            windowManager.updateViewLayout(container, layoutParams)
        }
    }

    /** Dismisses the popup window if it is currently showing. */
    fun dismiss() {
        if (popupWindow.isShowing) {
            popupWindow.dismiss()
        }
    }

    private fun cleanup() {
        lifecycleOwner?.lifecycle?.removeObserver(this)
    }

    private fun onMenuItemClicked(item: ActionMenuItem) {
        when {
            item === cancelItem -> {
                dismiss()
            }

            item is ActionMenuItem.Option -> {
                item.onClick?.invoke()
                onClickListener?.invoke(item)
                dismiss()
            }

            item is ActionMenuItem.Toggle -> {
                item.onToggle?.invoke(item.checked)
                onClickListener?.invoke(item)
                // Toggles usually do not dismiss the menu
            }
        }
    }

    override fun onDestroy(owner: LifecycleOwner) {
        dismiss()
    }
}

private fun Context.findLifecycleOwner(): LifecycleOwner? {
    var cur = this
    while (cur is ContextWrapper) {
        if (cur is LifecycleOwner) return cur
        cur = cur.baseContext
    }
    return null
}

/** DSL Builder for creating action menus. */
class ActionMenuBuilder {
    private val items = mutableListOf<ActionMenuItem>()
    var showCancel: Boolean = true
    var onDismiss: (() -> Unit)? = null

    fun option(
        @DrawableRes iconRes: Int = 0,
        @StringRes labelRes: Int,
        @ColorInt iconTint: Int? = null,
        enabled: Boolean = true,
        onClick: () -> Unit
    ) {
        items.add(
            ActionMenuItem.Option(
                icon = if (iconRes != 0) MenuIcon.Resource(iconRes) else null,
                labelRes = labelRes,
                iconTint = iconTint,
                enabled = enabled,
                onClick = onClick
            )
        )
    }

    fun option(
        iconDrawable: Drawable?,
        @StringRes labelRes: Int,
        @ColorInt iconTint: Int? = null,
        enabled: Boolean = true,
        onClick: () -> Unit
    ) {
        items.add(
            ActionMenuItem.Option(
                icon = iconDrawable?.let { MenuIcon.Drawable(it) },
                labelRes = labelRes,
                iconTint = iconTint,
                enabled = enabled,
                onClick = onClick
            )
        )
    }

    fun toggle(
        @DrawableRes iconRes: Int,
        @StringRes labelRes: Int,
        checked: Boolean,
        @ColorInt iconTint: Int? = null,
        enabled: Boolean = true,
        onToggle: (Boolean) -> Unit
    ) {
        items.add(
            ActionMenuItem.Toggle(
                iconRes = iconRes,
                labelRes = labelRes,
                checked = checked,
                iconTint = iconTint,
                enabled = enabled,
                onToggle = onToggle
            )
        )
    }

    fun separator() {
        items.add(ActionMenuItem.Separator)
    }

    fun build(context: Context): ActionMenu = ActionMenu(context, items, showCancel, onDismissListener = onDismiss)
}

/** Displays a [ActionMenu] anchored to the specified view. */
inline fun Context.showActionMenu(anchor: View, crossinline builderAction: ActionMenuBuilder.() -> Unit) {
    val builder = ActionMenuBuilder().apply(builderAction)
    builder.build(this).show(anchor)
}
