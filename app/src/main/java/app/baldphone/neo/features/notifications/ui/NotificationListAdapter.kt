package app.baldphone.neo.features.notifications.ui

import android.view.ViewGroup

import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView

import app.baldphone.neo.features.notifications.NotificationItem

import com.bald.uriah.baldphone.adapters.ModularListAdapter

class NotificationListAdapter(
    private val onItemCleared: (NotificationItem) -> Unit,
    private val onContentClick: (NotificationItem) -> Unit
) : ModularListAdapter<NotificationItem, NotificationListAdapter.ViewHolder>(DiffCallback) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val density = parent.context.resources.displayMetrics.density
        val view =
            NotificationItemView(parent.context).apply {
                layoutParams =
                    RecyclerView
                        .LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        ).apply {
                            setMargins(
                                (8 * density).toInt(),
                                (16 * density).toInt(),
                                (8 * density).toInt(),
                                0
                            )
                        }
            }
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(
        private val cardView: NotificationItemView
    ) : RecyclerView.ViewHolder(cardView) {
        fun bind(item: NotificationItem) {
            cardView.bind(
                item = item,
                onContentClick = { onContentClick(it) },
                onClearClick = { onItemCleared(it) }
            )
        }
    }

    companion object {
        private object DiffCallback : DiffUtil.ItemCallback<NotificationItem>() {
            override fun areItemsTheSame(oldItem: NotificationItem, newItem: NotificationItem) =
                (oldItem.key == newItem.key)

            override fun areContentsTheSame(oldItem: NotificationItem, newItem: NotificationItem) =
                oldItem.appName == newItem.appName &&
                    android.text.TextUtils.equals(oldItem.title, newItem.title) &&
                    android.text.TextUtils.equals(oldItem.text, newItem.text) &&
                    oldItem.timeStamp == newItem.timeStamp &&
                    oldItem.isClearable == newItem.isClearable
        }
    }
}
