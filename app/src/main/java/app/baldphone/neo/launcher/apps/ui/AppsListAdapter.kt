package app.baldphone.neo.launcher.apps.ui

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.ViewGroup

import androidx.annotation.AttrRes
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView

import app.baldphone.neo.launcher.apps.AppIconBinder
import app.baldphone.neo.launcher.apps.data.db.AppEntry

import com.bald.uriah.baldphone.R
import com.bald.uriah.baldphone.adapters.ModularListAdapter
import com.bald.uriah.baldphone.databinding.AppListItemBinding
import com.bald.uriah.baldphone.databinding.AppsHeaderBinding

/**
 * ListAdapter-based adapter for the applications list. Handles two view types:
 *  - [TYPE_HEADER]: Alphabetical section dividers ("A", "B", …). Non-interactive.
 *  - [TYPE_ITEM]: App entries with icon, label, and pin indicator.
 */
class AppsListAdapter(
    private val onAppClick: (position: Int, item: AppEntry) -> Unit
) : ModularListAdapter<AppListItem, RecyclerView.ViewHolder>(AppListItemDiffCallback) {
    private var selectedPosition = RecyclerView.NO_POSITION
    private var themeColors: ThemeColors? = null

    private data class ThemeColors(
        val selectedText: Int,
        val defaultText: Int,
        val selectedDrawable: Drawable?
    )

    /**
     * Clears the current selection state.
     */
    fun deselect() {
        val old = selectedPosition
        selectedPosition = RecyclerView.NO_POSITION
        if (old != RecyclerView.NO_POSITION) notifyItemChanged(old)
    }

    override fun getItemViewType(position: Int): Int =
        when (getItem(position)) {
            is AppListItem.Header -> TYPE_HEADER
            is AppListItem.App -> TYPE_ITEM
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_HEADER) {
            HeaderViewHolder(AppsHeaderBinding.inflate(inflater, parent, false))
        } else {
            AppViewHolder(AppListItemBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is AppListItem.Header -> (holder as HeaderViewHolder).bind(item)
            is AppListItem.App -> (holder as AppViewHolder).bind(item.entry, position == selectedPosition)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int, payloads: MutableList<Any>) {
        // on selection
        android.util.Log.d("AppsListAdapter", "onBindViewHolder(position=$position, payloads=$payloads)")
        if (payloads.isNotEmpty() && holder is AppViewHolder) {
            holder.updateSelectionState(position == selectedPosition)
        } else {
            super.onBindViewHolder(holder, position, payloads)
        }
    }

    class HeaderViewHolder(private val binding: AppsHeaderBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(header: AppListItem.Header) {
            binding.tvLetter.text = header.letter
        }
    }

    inner class AppViewHolder(private val binding: AppListItemBinding) : RecyclerView.ViewHolder(binding.root) {
        private val colors = getThemeColors(binding.root.context)
        private val defaultBackground = binding.root.background

        init {
            binding.root.setOnClickListener {
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    val item = getItem(pos) as? AppListItem.App ?: return@setOnClickListener

                    val oldPos = selectedPosition
                    selectedPosition = pos
                    notifyItemChanged(oldPos, PAYLOAD_SELECTION)
                    notifyItemChanged(selectedPosition, PAYLOAD_SELECTION)

                    onAppClick(pos, item.entry)
                }
            }
        }

        fun bind(app: AppEntry, isSelected: Boolean) {
            binding.appName.text = app.label
            AppIconBinder.loadPic(app, binding.appIcon)
            binding.pin.isVisible = app.isPinned
            updateSelectionState(isSelected)
        }

        fun updateSelectionState(isSelected: Boolean) {
            binding.root.background = if (isSelected) colors.selectedDrawable else defaultBackground
            binding.appName.setTextColor(if (isSelected) colors.selectedText else colors.defaultText)
        }
    }

    private fun getThemeColors(context: Context): ThemeColors =
        themeColors ?: ThemeColors(
            selectedText = context.resolveThemeColor(R.attr.bald_text_on_selected),
            defaultText = context.resolveThemeColor(R.attr.bald_text_on_button),
            selectedDrawable = ContextCompat.getDrawable(context, R.drawable.btn_selected)
        ).also { themeColors = it }

    companion object {
        const val TYPE_HEADER = 0
        const val TYPE_ITEM = 1
        private const val PAYLOAD_SELECTION = "PAYLOAD_SELECTION"
    }
}

fun Context.resolveThemeColor(
    @AttrRes attrRes: Int
): Int {
    val typedValue = TypedValue()
    theme.resolveAttribute(attrRes, typedValue, true)
    return typedValue.data
}

private object AppListItemDiffCallback : DiffUtil.ItemCallback<AppListItem>() {
    override fun areItemsTheSame(oldItem: AppListItem, newItem: AppListItem): Boolean =
        when {
            oldItem is AppListItem.Header && newItem is AppListItem.Header -> {
                oldItem.letter == newItem.letter
            }

            oldItem is AppListItem.App && newItem is AppListItem.App -> {
                oldItem.entry.componentName == newItem.entry.componentName &&
                    oldItem.entry.userId == newItem.entry.userId
            }

            else -> {
                false
            }
        }

    override fun areContentsTheSame(oldItem: AppListItem, newItem: AppListItem): Boolean = (oldItem == newItem)
}
