package app.baldphone.neo.ui.menu

import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes

import com.bald.uriah.baldphone.R

/**
 * Represents an item within an action menu:
 * buttons with icons ([Option]), checkboxes ([Toggle]), or visual separators ([Separator]).
 *
 * @property enabled Whether the action is currently interactable.
 */
sealed interface ActionMenuItem {
    var enabled: Boolean
    val iconTint: Int?

    data class Option(
        @param:DrawableRes val iconRes: Int,
        @param:StringRes val labelRes: Int,
        @param:ColorInt override val iconTint: Int? = null,
        override var enabled: Boolean = true,
        val onClick: (() -> Unit)? = null
    ) : ActionMenuItem

    data class Toggle(
        @param:DrawableRes val iconRes: Int = R.drawable.check_on_button,
        @param:StringRes val labelRes: Int,
        var checked: Boolean,
        @param:ColorInt override val iconTint: Int? = null,
        override var enabled: Boolean = true,
        val onToggle: ((Boolean) -> Unit)? = null
    ) : ActionMenuItem {
        fun toggleState() {
            checked = !checked
        }
    }

    data object Separator : ActionMenuItem {
        override var enabled = false
        override val iconTint: Int? = null
    }
}
