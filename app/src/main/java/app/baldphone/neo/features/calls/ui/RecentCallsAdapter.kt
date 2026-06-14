package app.baldphone.neo.features.calls.ui

import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.ViewGroup

import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView

import coil3.dispose
import coil3.load
import coil3.request.crossfade
import coil3.request.error
import coil3.request.fallback
import coil3.request.transformations
import coil3.transform.CircleCropTransformation

import app.baldphone.neo.core.assisttouch.enableAssistTouchHierarchy
import app.baldphone.neo.extensions.setClickableAccessibilityRole
import app.baldphone.neo.features.calls.model.CallListEntry
import app.baldphone.neo.features.calls.model.CallLogItemType
import app.baldphone.neo.utils.formatRecentTimestamp

import com.bald.uriah.baldphone.R
import com.bald.uriah.baldphone.databinding.CallLogHeaderBinding
import com.bald.uriah.baldphone.databinding.CallLogItemBinding

/**
 * RecyclerView adapter for the recent calls list.
 *
 * @param onItemClick Callback invoked when a call item row is tapped.
 */
class RecentCallsAdapter(
    private val onItemClick: (number: String?, name: String?, lookupUri: String?) -> Unit
) : ListAdapter<CallListEntry, RecyclerView.ViewHolder>(DiffCallback()) {
    init {
        stateRestorationPolicy = StateRestorationPolicy.PREVENT_WHEN_EMPTY
    }

    override fun getItemViewType(position: Int): Int =
        when (getItem(position)) {
            is CallListEntry.Header -> VIEW_TYPE_HEADER
            is CallListEntry.Item -> VIEW_TYPE_ITEM
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == VIEW_TYPE_HEADER) {
            HeaderViewHolder(CallLogHeaderBinding.inflate(inflater, parent, false))
        } else {
            ItemViewHolder(CallLogItemBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val entry = getItem(position)
        when (holder) {
            is HeaderViewHolder -> holder.bind(entry as CallListEntry.Header)
            is ItemViewHolder -> holder.bind(entry as CallListEntry.Item)
        }
    }

    // Cancel in-flight Coil image loads when a ViewHolder is recycled, reducing IO contention with
    // pagination fetches during fast scrolling.
    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        if (holder is ItemViewHolder) {
            holder.clearImage()
        }
    }

    class HeaderViewHolder(private val binding: CallLogHeaderBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(header: CallListEntry.Header) {
            binding.tvHeader.text = header.text
        }
    }

    private inner class ItemViewHolder(private val binding: CallLogItemBinding) :
        RecyclerView.ViewHolder(binding.root) {
        init {
            binding.root.setClickableAccessibilityRole()
            binding.btnCallLogItem.setOnClickListener {
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    (getItem(pos) as? CallListEntry.Item)?.let { item ->
                        onItemClick(item.number, item.name, item.cachedLookupUri)
                    }
                }
            }
            itemView.enableAssistTouchHierarchy()
        }

        fun bind(item: CallListEntry.Item) {
            val context = itemView.context

            // Text and Style
            val displayText = item.displayName(context)
            binding.contactName.text = if (item.groupCount > 1) "$displayText (${item.groupCount})" else displayText
            binding.tvCallTime.text = item.date.formatRecentTimestamp(context)

            val style = if (item.isNew) Typeface.BOLD else Typeface.NORMAL
            binding.contactName.setTypeface(null, style)
            binding.tvCallType.setTypeface(null, style)

            // Call Type UI
            val displayType = CallLogItemType.fromSystemType(item.type)
            binding.tvCallType.setText(displayType.stringRes)
            binding.ivCallType.setImageResource(displayType.drawableRes)
            binding.root.backgroundTintList =
                ContextCompat.getColorStateList(context, displayType.colorRes)

            // Avatar Loading
            val fallbackRes =
                if (item.number.isNullOrBlank()) {
                    R.drawable.private_face_in_recent_calls
                } else {
                    R.drawable.face
                }

            binding.profilePic.load(item.cachedPhotoUri?.toUri()) {
                fallback(fallbackRes)
                error(fallbackRes)
                crossfade(false)
                transformations(CircleCropTransformation())
            }
        }

        /** Cancel any pending Coil request and clear the image. */
        fun clearImage() {
            binding.profilePic.dispose()
            binding.profilePic.setImageDrawable(null)
        }
    }

    private class DiffCallback : DiffUtil.ItemCallback<CallListEntry>() {
        override fun areItemsTheSame(oldItem: CallListEntry, newItem: CallListEntry): Boolean =
            when (oldItem) {
                is CallListEntry.Header if newItem is CallListEntry.Header -> {
                    oldItem.text == newItem.text
                }

                is CallListEntry.Item if newItem is CallListEntry.Item -> {
                    oldItem.id == newItem.id
                }

                else -> {
                    false
                }
            }

        override fun areContentsTheSame(oldItem: CallListEntry, newItem: CallListEntry): Boolean = oldItem == newItem
    }

    companion object {
        private const val VIEW_TYPE_HEADER = 0
        private const val VIEW_TYPE_ITEM = 1
    }
}
